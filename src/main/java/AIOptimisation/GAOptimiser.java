package AIOptimisation;

import java.util.*;

import static AIOptimisation.Utils.getRandomWeights;

public class GAOptimiser {
    private ArrayList<double[]> population;
    private double[] returns;
    private final Map<Integer, Double> fitnesses = new HashMap<>();
    private int generations;
    private double[][] riskCovarianceMatrix;
    private double[] bestWeights;

    public void initialise(int amountOfWeights, int numberOfGenerations, int populationSize, double[] returns, double[][] riskCovarianceMatrix) {
        population = new ArrayList<>();
        generations = numberOfGenerations;

        this.returns = returns;
        this.riskCovarianceMatrix = riskCovarianceMatrix;

        for (int i = 0; i < populationSize; i++)
            population.add(getRandomWeights(amountOfWeights));
    }

    private ArrayList<double[]> selection(ArrayList<double[]> population) {
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

    private ArrayList<double[]> crossover(ArrayList<double[]> parents1, ArrayList<double[]> parents2, double rate) {
        ArrayList<double[]> newPopulation = new ArrayList<>();

        for (int i = 0; i < parents1.size(); i++) {
            ArrayList<double[]> children = crossover(parents1.get(i), parents2.get(i), rate);

            newPopulation.addAll(children);
        }

        return newPopulation;
    }

    private ArrayList<double[]> crossover(double[] parent1, double[] parent2, double rate) {
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

    private ArrayList<double[]> mutate(ArrayList<double[]> population, double rate) {
        ArrayList<double[]> mutatedPopulation = new ArrayList<>();
        for (double[] individual : population)
            mutatedPopulation.add(Utils.mutate(individual, rate));

        return mutatedPopulation;
    }

    private double evaluate(int generation) {
        double sum = 0;
        double best = Double.MIN_VALUE;

        for (int i = 0; i < population.size(); i++) {
            double currFitness = EvaluationFunction.getReturnToRiskRatio(population.get(i), returns, riskCovarianceMatrix);

            sum += currFitness;
            fitnesses.put(i, currFitness);
            if (currFitness > best) best = currFitness;
        }

        System.out.println("GENERATION " + generation + " AVERAGE FITNESS: " + sum / population.size() + "\tBEST FITNESS: " + best);

        return sum / population.size();
    }

    public void run() {
        System.out.println("Performing Genetic Portfolio Optimisation");

        double prevFitness = -1, currFitness, bestFitness = Double.MIN_VALUE;
        int convergenceCount = 0;

        evaluate(0); //Evaluate

        for (int i = 1; i <= generations; i++) {
            ArrayList<double[]> parents1 = selection(population); //Selection of parent population 1
            ArrayList<double[]> parents2 = selection(population); //Selection of parent population 2

            population = crossover(parents1, parents2, 0.5);//Crossover
            population = mutate(population, 0.01);//Mutation

            currFitness = evaluate(i); //Evaluate

            if (currFitness > bestFitness) {
                bestFitness = currFitness;
                bestWeights = getBestOfPopulation();
            }

            if (currFitness == prevFitness) convergenceCount++;
            if (convergenceCount == 100) break;

            prevFitness = currFitness;
        }
    }

    public double[] getBest() {
        return bestWeights;
    }

    private double[] getBestOfPopulation() {
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
