package Portfolio;

import Utility.PortfolioUtils;

import java.util.*;

import static Utility.PortfolioUtils.getRandomWeights;

public class GAOptimiser {
    static private ArrayList<double[]> population;
    static private double[] returns;
    static private final Map<Integer, Double> fitnesses = new TreeMap<>();
    static private int generations;
    static private double[][] riskCovariance;

    static private ArrayList<double[]> selection(ArrayList<double[]> population) {
        ArrayList<double[]> selectedPopulation = new ArrayList<>();
        ArrayList<Map.Entry<Integer, Double>> orderedIndividuals = new ArrayList<>(fitnesses.entrySet());
        orderedIndividuals.sort(Comparator.comparing(Map.Entry::getValue));
        Collections.reverse(orderedIndividuals);

        double totalFitness = 0;

        for (int i = 0; i < population.size(); i++)
            totalFitness += Math.abs(fitnesses.get(i));

        for (int i = 0; i < population.size() / 2; i++) {
            double selectionPoint = Math.random() * totalFitness, accumulatedFitnesss = 0;
            int selectInd = 0;

            while ((accumulatedFitnesss += Math.abs(orderedIndividuals.get(selectInd).getValue())) < selectionPoint)
                selectInd++;

            selectedPopulation.add(population.get(orderedIndividuals.get(selectInd).getKey()));
        }

        return selectedPopulation;
    }

    static private ArrayList<double[]> crossover(ArrayList<double[]> parents1, ArrayList<double[]> parents2, double rate) {
        ArrayList<double[]> newPopulation = new ArrayList<>();

        for (int i = 0; i < parents1.size(); i++) {
            ArrayList<double[]> children = crossover(parents1.get(i), parents2.get(i), rate);

            newPopulation.addAll(children);
        }

        return newPopulation;
    }

    static private ArrayList<double[]> crossover(double[] parent1, double[] parent2, double rate) {
        double[] child1 = new double[parent1.length], child2 = new double[parent2.length];
        ArrayList<double[]> children = new ArrayList<>();

        for (int i = 0; i < child1.length; i++)
            if (Math.random() < rate) {
                child1[i] = parent2[i];
                child2[i] = parent1[i];
            } else {
                child1[i] = parent1[i];
                child2[i] = parent2[i];
            }

        children.add(child1);
        children.add(child2);

        return children;
    }

    static private ArrayList<double[]> mutate(ArrayList<double[]> population, double rate) {
        ArrayList<double[]> mutatedPopulation = new ArrayList<>();
        for (double[] individual : population)
            mutatedPopulation.add(PortfolioUtils.mutate(individual, rate));

        return mutatedPopulation;
    }

    static private double evaluate(int generation, boolean showDebug) {
        double sum = 0;
        double best = Double.MIN_VALUE;

        for (int i = 0; i < population.size(); i++) {
            double[] currentGene = population.get(i);
            double currFitness = EvaluationFunction.getReturnToRiskRatio(currentGene, returns, riskCovariance);

            sum += currFitness;
            fitnesses.put(i, currFitness);
            if (currFitness > best)
                best = currFitness;
        }

        if (showDebug)
            System.out.println("GENERATION " + generation + " AVERAGE FITNESS: " + sum / population.size() + "\tBEST FITNESS: " + best);

        return sum / population.size();
    }

    static public double[] optimise(int amountOfWeights, int numberOfGenerations, int populationSize, double[] returnsArray, double[][] riskCovarianceMatrix, boolean showDebug) {
        System.out.println("Performing Genetic Portfolio Optimisation");
        double[] bestWeights;
        population = new ArrayList<>();
        generations = numberOfGenerations;

        returns = returnsArray;
        riskCovariance = riskCovarianceMatrix;

        for (int i = 0; i < populationSize; i++)
            population.add(getRandomWeights(amountOfWeights));

        double prevFitness = -1, currFitness;
        int convergenceCount = 0;

        double bestFitness = evaluate(0, showDebug); //Evaluate

        bestWeights = getBestOfPopulation();

        for (int i = 1; i <= generations; i++) {
            ArrayList<double[]> parents1 = selection(population); //Selection of parent population 1
            ArrayList<double[]> parents2 = selection(population); //Selection of parent population 2

            population = crossover(parents1, parents2, 0.5);//Crossover
            population = mutate(population, 0.1);//Mutation

            currFitness = evaluate(i, showDebug); //Evaluate

            if (currFitness > bestFitness) {
                bestFitness = currFitness;
                bestWeights = getBestOfPopulation();
            }

            if (currFitness == prevFitness) convergenceCount++;
            if (convergenceCount == 100) break;

            prevFitness = currFitness;
        }
        return bestWeights;
    }

   static private double[] getBestOfPopulation() {
        double best = 0;
        int bestInd = 0;

        for (int i = 0; i < population.size(); i++) {
            if (fitnesses.get(i) > best) {
                best = fitnesses.get(i);
                bestInd = i;
            }
        }

        return population.get(bestInd);
    }
}
