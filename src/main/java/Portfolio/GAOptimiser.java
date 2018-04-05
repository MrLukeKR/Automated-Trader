package Portfolio;

import Utility.PortfolioUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

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
        ArrayList<Genome> orderedIndividuals = new ArrayList<>(population);

        orderedIndividuals.sort(Comparator.comparing(Genome::getFitness));
        Collections.reverse(orderedIndividuals);

        double totalFitness = 0;

        for (Genome genome : population) totalFitness += genome.getFitness();

        for (int i = 0; i < population.size() / 2; i++) {
            double selectionPoint = Math.random() * totalFitness, accumulatedFitnesss = 0;
            int selectInd = 0;

            while ((accumulatedFitnesss += orderedIndividuals.get(selectInd).getFitness()) < selectionPoint)
                selectInd++;

            selectedPopulation.add(orderedIndividuals.get(selectInd));
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
        ArrayList<Genome> mutatedPopulation = new ArrayList<>();
        for (Genome individual : population) {
            Genome newGenome = new Genome(PortfolioUtils.mutate(individual.getGenes(), rate));
            mutatedPopulation.add(newGenome);
        }

        return mutatedPopulation;
    }

    /**
     * Calculates the fitness of each genome in a population
     *
     * @param generation      The iteration that the Genetic Algorithm is acting upon
     * @param em              Evaluation Method (Maximise Return, Balance Risk and Return)
     * @param population      List of Genomes (portfolio asset weightings)
     * @param expectedReturns Array of expected returns (mean return, one per stock)
     * @param riskCovariance  Covariance matrix (represents the risk of the portfolio)
     * @param showDebug       True if debug information should be printed, false otherwise
     * @return Total sum of fitness over all Genomes
     */
    static private double evaluate(int generation, PortfolioManager.EvaluationMethod em, ArrayList<Genome> population, Map<String, Double> expectedReturns, double[][] riskCovariance, boolean showDebug) {
        double sum = 0;
        double best = 0;

        for (Genome currentGenome : population) {
            double currFitness = 0;

            if (EvaluationFunction.sumsToOne(currentGenome.getGenes()))
                if (em == PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK)
                    currFitness = EvaluationFunction.getReturnToRiskRatio(currentGenome.getGenes(), expectedReturns, riskCovariance);
                else
                    currFitness = EvaluationFunction.getReturn(currentGenome.getGenes(), expectedReturns);

            sum += currFitness;
            currentGenome.setFitness(currFitness);

            if (currFitness >= best) best = currFitness;
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
    static Map<String, Double> optimise(ArrayList<String> stocks, PortfolioManager.EvaluationMethod em, int numberOfGenerations, int populationSize, Map<String, Double> expectedReturns, double[][] riskCovarianceMatrix, boolean showDebug) {
        System.out.println("Performing Genetic Portfolio Optimisation");
        ArrayList<Genome> population = new ArrayList<>();
        ArrayList<Genome> bestOfPopulation;
        Genome bestGenome;

        for (int i = 0; i < populationSize; i++) population.add(new Genome(getRandomWeights(stocks)));

        double prevFitness = -1, currFitness;
        int convergenceCount = 0;

        double bestFitness = evaluate(0, em, population, expectedReturns, riskCovarianceMatrix, showDebug); //Evaluate

        bestGenome = getBestOfPopulation(population);

        for (int i = 1; i <= numberOfGenerations; i++) {
            bestOfPopulation = getBestOfPopulation(Math.round(population.size() / 10), population);

            ArrayList<Genome> parents1 = selection(population); //Selection of parent population 1
            ArrayList<Genome> parents2 = selection(population); //Selection of parent population 2

            population = mutate(crossover(parents1, parents2, 0.5), 0.1);
            evaluate(i, em, population, expectedReturns, riskCovarianceMatrix, false); //Evaluate
            population = replaceWorstOfPopulation(bestOfPopulation, population);
            currFitness = evaluate(i, em, population, expectedReturns, riskCovarianceMatrix, showDebug); //Evaluate

            if (currFitness >= bestFitness) {
                bestFitness = currFitness;
                bestGenome = getBestOfPopulation(population);
            }

            if (currFitness == prevFitness) convergenceCount++;
            if (convergenceCount == 100) break;

            prevFitness = currFitness;
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
        ArrayList<Genome> orderedIndividuals = new ArrayList<>(population);
        orderedIndividuals.sort(Comparator.comparing(Genome::getFitness));

        for (int i = 0; i < replacement.size(); i++) orderedIndividuals.remove(0);

        orderedIndividuals.addAll(replacement);

        return orderedIndividuals;
    }

    /**
     * Selects the n best performing Genomes of a given population
     * @param amount The amount of best performing Genomes to select
     * @param population The population to perform the selection on
     * @return An array of the n best performing Genomes
     */
    static private ArrayList<Genome> getBestOfPopulation(int amount, ArrayList<Genome> population) {
        ArrayList<Genome> orderedIndividuals = new ArrayList<>(population);
        orderedIndividuals.sort(Comparator.comparing(Genome::getFitness));

        Collections.reverse(orderedIndividuals);

        return new ArrayList<>(orderedIndividuals.subList(0, amount));
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