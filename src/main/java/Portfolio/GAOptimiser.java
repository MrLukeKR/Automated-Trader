package Portfolio;

import Utility.PortfolioUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static Utility.PortfolioUtils.getRandomWeights;

public class GAOptimiser implements Optimiser{
    static private ArrayList<Genome> selection(ArrayList<Genome> population) {
        ArrayList<Genome> selectedPopulation = new ArrayList<>();
        ArrayList<Genome> orderedIndividuals = new ArrayList<>(population);

        Collections.sort(orderedIndividuals, Comparator.comparing(Genome::getFitness));

        Collections.reverse(orderedIndividuals);

        double totalFitness = 0;

        for (Genome genome : population)
            totalFitness += genome.getFitness();

        for (int i = 0; i < population.size() / 2; i++) {
            double selectionPoint = Math.random() * totalFitness, accumulatedFitnesss = 0;
            int selectInd = 0;

            while ((accumulatedFitnesss += orderedIndividuals.get(selectInd).getFitness()) < selectionPoint)
                selectInd++;

            selectedPopulation.add(orderedIndividuals.get(selectInd));
        }

        return selectedPopulation;
    }

    static private ArrayList<Genome> crossover(ArrayList<Genome> parents1, ArrayList<Genome> parents2, double rate) {
        ArrayList<Genome> newPopulation = new ArrayList<>();

        for (int i = 0; i < parents1.size(); i++) {
            ArrayList<Genome> children = crossover(parents1.get(i), parents2.get(i), rate);

            newPopulation.addAll(children);
        }

        return newPopulation;
    }

    static private ArrayList<Genome> crossover(Genome parent1, Genome parent2, double rate) {
        Genome child1 = new Genome(parent1.getGenes()), child2 = new Genome(parent2.getGenes());
        ArrayList<Genome> children = new ArrayList<>();

        for (int i = 0; i < child1.getGenes().length; i++)
            if (Math.random() < rate) {
                child1.setGene(i,parent2.getGene(i));
                child2.setGene(i,parent1.getGene(i));
            }

        children.add(child1);
        children.add(child2);

        return children;
    }

    static private ArrayList<Genome> mutate(ArrayList<Genome> population, double rate) {
        ArrayList<Genome> mutatedPopulation = new ArrayList<>();
        for (Genome individual : population) {
            double[] originalValues = individual.getGenes().clone();
            double[] values = PortfolioUtils.mutate(originalValues, rate).clone();
         Genome newGenome = new Genome(values);
            mutatedPopulation.add(newGenome);
        }

        return mutatedPopulation;
    }

    static private double evaluate(int generation, PortfolioManager.EvaluationMethod em, ArrayList<Genome> population, double[] returns, double[][] riskCovariance, boolean showDebug) {
        double sum = 0;
        double best = 0;

        for (int i = 0; i < population.size(); i++) {
            Genome currentGenome = population.get(i);
            double currFitness = 0;

            if(EvaluationFunction.sumsToOne(currentGenome.getGenes()))
                if(em == PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK)
                    currFitness = EvaluationFunction.getReturnToRiskRatio(currentGenome.getGenes(), returns, riskCovariance);
                else
                    currFitness = EvaluationFunction.getReturn(currentGenome.getGenes(),returns);

            sum += currFitness;
            currentGenome.setFitness(currFitness);

            if (currFitness >= best)
                best = currFitness;
        }

        if (showDebug)
            System.out.println("GENERATION " + generation + " AVERAGE FITNESS: " + sum / population.size() + " TOTAL FITNESS: " + sum+ "\tBEST FITNESS: " + best);

        return sum;
    }

    static public double[] optimise(int amountOfWeights, PortfolioManager.EvaluationMethod em, int numberOfGenerations, int populationSize, double[] returnsArray, double[][] riskCovarianceMatrix, boolean showDebug) {
        System.out.println("Performing Genetic Portfolio Optimisation");
        ArrayList<Genome> population = new ArrayList<>();
        ArrayList<Genome> bestOfPopulation;
        Genome bestGenome;

        for (int i = 0; i < populationSize; i++)
            population.add(new Genome(getRandomWeights(amountOfWeights)));

        double prevFitness = -1, currFitness;
        int convergenceCount = 0;

        double bestFitness = evaluate(0, em, population, returnsArray, riskCovarianceMatrix, showDebug); //Evaluate

        bestGenome = getBestOfPopulation(population);

        for (int i = 1; i <= numberOfGenerations; i++) {
            bestOfPopulation = getBestOfPopulation(Math.round(population.size() / 10), population);

            ArrayList<Genome> parents1 = selection(population); //Selection of parent population 1
            ArrayList<Genome> parents2 = selection(population); //Selection of parent population 2

            population = crossover(parents1, parents2, 0.5);//Crossover

            population = mutate(population, 0.1);//Mutation

            evaluate(i, em, population, returnsArray, riskCovarianceMatrix, false); //Evaluate

            population = replaceWorstOfPopulation(bestOfPopulation, population);

            currFitness = evaluate(i, em, population, returnsArray, riskCovarianceMatrix, showDebug); //Evaluate

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

    static private ArrayList<Genome> replaceWorstOfPopulation(ArrayList<Genome> replacement, ArrayList<Genome> population){
        ArrayList<Genome> orderedIndividuals = new ArrayList<>(population);
        Collections.sort(orderedIndividuals, Comparator.comparing(Genome::getFitness));

        for(int i = 0; i < replacement.size(); i++)
            orderedIndividuals.remove(0);

        orderedIndividuals.addAll(replacement);

        return orderedIndividuals;
    }

    static private ArrayList<Genome> getBestOfPopulation(int amount, ArrayList<Genome> population) {
        ArrayList<Genome> orderedIndividuals = new ArrayList<>(population);
        Collections.sort(orderedIndividuals, Comparator.comparing(Genome::getFitness));

        Collections.reverse(orderedIndividuals);

        return new ArrayList<>(orderedIndividuals.subList(0,amount));
    }

   static private Genome getBestOfPopulation(ArrayList<Genome> population) {
        return getBestOfPopulation(1,population).get(0);
    }
}
