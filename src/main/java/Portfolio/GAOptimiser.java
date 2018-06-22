package Portfolio;


import Utility.PortfolioUtils;

import java.util.*;

import static Utility.PortfolioUtils.getRandomWeights;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.3
 */

class GAOptimiser {

    /**
     * Performs Roulette Wheel Selection on the population to obtain parent genomes
     *
     * @param population List of Genomes
     * @return List of selected individuals (parents)
     */
    static private ArrayList<Genome> selection(ArrayList<Genome> population) {
        ArrayList<Genome> selectedPopulation = new ArrayList<>();
        population.sort(Comparator.comparing(Genome::getFitness));
        Collections.reverse(population);

        double totalFitness = 0;
        double worstFitness = Double.NaN;

        for (Genome genome : population) {
            if (genome.getFitness() < worstFitness || Double.isNaN(worstFitness)) worstFitness = genome.getFitness();
            totalFitness += genome.getFitness();
        }

        totalFitness -= worstFitness * population.size();

        for (int i = 0; i < population.size() / 2; i++) {
            double selectionPoint = Math.random() * totalFitness, accumulatedFitnesss = 0;
            int selectInd = 0;

            while ((accumulatedFitnesss += population.get(selectInd).getFitness() - worstFitness) < selectionPoint)
                selectInd++;

            selectedPopulation.add(population.get(selectInd));
        }

        return selectedPopulation;
    }

    /**
     * Performs uniform crossover on two lists of selected individuals (parents) to create a population of children
     *
     * @param parents1 List of selected individuals
     * @param parents2 List of selected individuals
     * @param rate     Probability that a value will be switched between two parents
     * @return New population of children (resulting genomes from crossover between parents)
     */
    static private ArrayList<Genome> crossover(ArrayList<Genome> parents1, ArrayList<Genome> parents2, double rate) {
        ArrayList<Genome> newPopulation = new ArrayList<>();

        for (int i = 0; i < parents1.size(); i++) newPopulation.addAll(crossover(parents1.get(i), parents2.get(i), rate));

        return newPopulation;
    }

    /**
     * Performs uniform crossover on two selected individuals (parents) to create two child genomes
     * @param parent1 Selected individual (set of portfolio asset weights)
     * @param parent2 Selected individual (set of portfolio asset weights)
     * @param rate Probability that a value will be switched between two parents
     * @return Resulting child genome from crossover between parents
     */
    static private ArrayList<Genome> crossover(Genome parent1, Genome parent2, double rate) {
        Genome child1 = new Genome(parent1.getGenes()), child2 = new Genome(parent2.getGenes());
        ArrayList<Genome> children = new ArrayList<>();

        for (String stock : parent1.getGenes().keySet())
            if (Math.random() < rate) {
                child1.setGene(stock, parent2.getGene(stock));
                child2.setGene(stock, parent1.getGene(stock));
            }

        children.add(child1);
        children.add(child2);

        return children;
    }

    /**
     * Performs gaussian mutation on the population
     * @param population List of genomes (portfolio asset weightings)
     * @param rate Probability that a genome will be mutated
     * @return List of mutate genomes
     */
    static private ArrayList<Genome> mutate(ArrayList<Genome> population, double rate) {
        ArrayList<Genome> mutatedPopultion = new ArrayList<>();

        for (Genome individual : population)
            mutatedPopultion.add(new Genome(PortfolioUtils.mutate(individual.getGenes(), rate)));

        return mutatedPopultion;
    }

    /**
     * Calculates the fitness of each genome in a population
     *
     * @param generation      The iteration that the Genetic Algorithm is acting upon
     * @param em              Evaluation Method (Maximise Return, Balance Risk and Return)
     * @param population      List of Genomes (portfolio asset weightings)
     * @param currentPortfolio The current weighting of assets in the portfolio
     * @param stockPrices The current prices of each stock
     * @param expectedReturns Array of expected returns (mean return, one per stock)
     * @param riskCovariance  Covariance matrix (represents the risk of the portfolio)
     * @param showDebug       True if debug information should be printed, false otherwise
     * @return Total sum of fitness over all Genomes
     */
    static private double evaluate(int generation, PortfolioManager.EvaluationMethod em, ArrayList<Genome> population, Map<String, Double> currentPortfolio, Map<String, Double> stockPrices, Map<String, Double> expectedReturns, Map<String, Map<String, Double>> riskCovariance, boolean showDebug) {
        double sum = 0;
        double best = Double.NaN;

        Map<String, Double> buyCost = new TreeMap<>(stockPrices);
        for (String stock : buyCost.keySet()) buyCost.put(stock, buyCost.get(stock) * 0.1);

        Map<String, Double> sellCost = new TreeMap<>(stockPrices);
        for (String stock : sellCost.keySet()) sellCost.put(stock, sellCost.get(stock) * 0.1);

        ArrayList<Thread> evaluationThreads = new ArrayList<>();

        for (Genome currentGenome : population) {
            evaluationThreads.add(new Thread(() -> {
                double currFitness = 0;

                if (Constraint.sumsToOne(currentGenome.getGenes())) {
                    if (em == PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK)
                        currFitness = EvaluationFunction.getReturnToRiskRatio(currentGenome.getGenes(), expectedReturns, riskCovariance);
                    else
                        currFitness = EvaluationFunction.getReturn(currentGenome.getGenes(), expectedReturns);

                    currFitness -= Constraint.getTransactionCosts(currentPortfolio, currentGenome.getGenes(), buyCost, sellCost);
                }

                currentGenome.setFitness(currFitness);
            }));
        }

        for (Thread thread : evaluationThreads) thread.start();
        for (Thread thread : evaluationThreads)
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        for (Genome currentGenome : population) {
            double currentFitness = currentGenome.getFitness();
            sum += currentFitness;
            if (currentFitness >= best || Double.isNaN(best)) best = currentFitness;
        }

        if (showDebug)
            System.out.println("GENERATION " + generation + " AVERAGE FITNESS: " + sum / population.size() + " TOTAL FITNESS: " + sum + "\tBEST FITNESS: " + best);

        return sum;
    }

    /**
     * Performs Genetic Algorithm optimisation on the portfolio
     *
     * @param em                   Evaluation method (Maximise Return, Balance Risk and Return)
     * @param numberOfGenerations  Number of iterations to perform optimisation
     * @param populationSize       Amount of Genomes in a population
     * @param expectedReturns      List of mean returns of a stock (one per stock)
     * @param riskCovarianceMatrix Matrix of covariances between stock expected returns
     * @param showDebug            True if debug information should be printed, False otherwise
     * @return Array of stock weights (the portfolio)
     */
    static Map<String, Double> optimise(ArrayList<String> stocks, PortfolioManager.EvaluationMethod em, int numberOfGenerations, int populationSize, Map<String, Double> currentPortfolio, Map<String, Double> stockPrices, Map<String, Double> expectedReturns, Map<String, Map<String, Double>> riskCovarianceMatrix, boolean showDebug) {
        System.out.println("Performing Genetic Portfolio Optimisation");
        ArrayList<Genome> population = new ArrayList<>();
        ArrayList<Genome> bestOfPopulation;
        Genome bestGenome;

        for (int i = 0; i < populationSize; i++) population.add(new Genome(getRandomWeights(stocks)));

        double prevFitness = -1, currFitness;
        int convergenceCount = 0;

        double bestFitness = evaluate(0, em, population, currentPortfolio, stockPrices, expectedReturns, riskCovarianceMatrix, showDebug); //Evaluate

        bestGenome = getBestOfPopulation(population);

        for (int i = 1; i <= numberOfGenerations; i++) {
            bestOfPopulation = getBestOfPopulation(Math.round(population.size() / 10), population);

            ArrayList<Genome> parents1 = selection(population), parents2 = selection(population); //Selection of parent population 1 + 2

            population = crossover(parents1, parents2, 0.8);
            population = mutate(population, 0.1);
            evaluate(i, em, population, currentPortfolio, stockPrices, expectedReturns, riskCovarianceMatrix, false); //Evaluate
            population = replaceWorstOfPopulation(bestOfPopulation, population);

            currFitness = evaluate(i, em, population, currentPortfolio, stockPrices, expectedReturns, riskCovarianceMatrix, showDebug); //Evaluate

            if (currFitness >= bestFitness) bestFitness = currFitness;
            if (bestOfPopulation.get(0).getFitness() >= bestGenome.getFitness()) bestGenome = bestOfPopulation.get(0);

            if ((convergenceCount = (int) (prevFitness * 100.0) == (int) ((prevFitness = bestGenome.getFitness()) * 100.0) ? convergenceCount + 1 : 0) == 20)
                break;
        }
        return bestGenome.getGenes();
    }

    /**
     * Uses elitism to replace the worst performing genomes
     *
     * @param replacement Best Genomes of previous population
     * @param population  Population to perform replacement on
     * @return Population containing the elitist Genomes
     */
    static private ArrayList<Genome> replaceWorstOfPopulation(ArrayList<Genome> replacement, ArrayList<Genome> population) {
        population.sort(Comparator.comparing(Genome::getFitness));
        ArrayList<Genome> newPopulation = new ArrayList<>(new ArrayList<>(replacement));
        for (int i = replacement.size() - 1; i < population.size(); i++)
            newPopulation.add(new Genome(population.get(i)));

        return newPopulation;
    }

    /**
     * Selects the n best performing Genomes of a given population
     * @param amount The amount of best performing Genomes to select
     * @param population The population to perform the selection on
     * @return An array of the n best performing Genomes
     */
    static private ArrayList<Genome> getBestOfPopulation(int amount, ArrayList<Genome> population) {
        ArrayList<Genome> bestOfPopulation = new ArrayList<>();
        population.sort(Comparator.comparing(Genome::getFitness));

        Collections.reverse(population);

        for (int i = 0; i < amount; i++)
            bestOfPopulation.add(new Genome(population.get(i)));

        return bestOfPopulation;
    }

    /**
     * Gets the single best performing Genome of a population
     *
     * @param population Population of Genomes to select the best performing individual from
     * @return Best performing Genome
     */
    static private Genome getBestOfPopulation(ArrayList<Genome> population) {
        return getBestOfPopulation(1, population).get(0); }
}