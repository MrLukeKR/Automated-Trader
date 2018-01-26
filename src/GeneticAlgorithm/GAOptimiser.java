package GeneticAlgorithm;

import java.util.*;

public class GAOptimiser {
    ArrayList<double[]> population;
    double[] returns;
    Map<Integer, Double> fitnesses = new HashMap<>();
    int generations;
    double[][] riskCovarianceMatrix;
    double[] bestWeights;

    private double[] getRandomWeights(int amount) {
        double[] weights = new double[amount];

        for (int i = 0; i < amount; i++)
            weights[i] = Math.random();

        return banNegativeStocks(weights, returns);
    }

    public double[] banNegativeStocks(double[] weights, double[] returns) {
        for (int i = 0; i < weights.length; i++)
            if (returns[i] <= 0) weights[i] = 0;

        return scaleWeights(weights);
    }

    public void initialise(int amountOfWeights, int numberOfGenerations, int populationSize, double[] returns, double[][] riskCovarianceMatrix) {
        population = new ArrayList<>();
        generations = numberOfGenerations;

        this.returns = returns;
        this.riskCovarianceMatrix = riskCovarianceMatrix;

        for (int i = 0; i < populationSize; i++)
            population.add(getRandomWeights(amountOfWeights));
    }

    public ArrayList<double[]> selection(ArrayList<double[]> population) {
        ArrayList<double[]> selectedPopulation = new ArrayList<>();
        ArrayList<Map.Entry<Integer, Double>> orderedIndividuals = new ArrayList<>(fitnesses.entrySet());
        Collections.sort(orderedIndividuals, Comparator.comparing(Map.Entry::getValue));
        Collections.reverse(orderedIndividuals);

        double totalFitness = 0;

        for (int i = 0; i < population.size(); i++)
            totalFitness += Math.abs(fitnesses.get(i));

        for (int i = 0; i < population.size(); i++) {
            double selectionPoint = Math.random() * totalFitness, accumulatedFitnesss = 0;
            int selectInd = 0;

            while ((accumulatedFitnesss += Math.abs(orderedIndividuals.get(selectInd).getValue())) < selectionPoint)
                selectInd++;

            selectedPopulation.add(population.get(orderedIndividuals.get(selectInd).getKey()));
        }

        return selectedPopulation;
    }

    public ArrayList<double[]> crossover(ArrayList<double[]> parents1, ArrayList<double[]> parents2, double rate) {
        ArrayList<double[]> newPopulation = new ArrayList<>();

        for (int i = 0; i < parents1.size(); i++)
            newPopulation.add(crossover(parents1.get(i), parents2.get(i), rate));

        return newPopulation;
    }

    public double[] crossover(double[] weights1, double[] weights2, double rate) {
        double[] newWeights = new double[weights1.length];

        for (int i = 0; i < newWeights.length; i++)
            if (Math.random() < rate)
                newWeights[i] = weights1[i];
            else
                newWeights[i] = weights2[i];

        return scaleWeights(newWeights);
    }

    public ArrayList<double[]> mutate(ArrayList<double[]> population, double rate) {
        ArrayList<double[]> mutatedPopulation = new ArrayList<>();
        for (double[] individual : population)
            mutatedPopulation.add(mutate(individual, rate));

        return mutatedPopulation;
    }

    public double[] mutate(double[] weights, double rate) {
        for (int i = 0; i < weights.length; i++)
            if (Math.random() < rate)
                weights[i] *= Math.random();

        return scaleWeights(weights);
    }

    private double[] scaleWeights(double[] originalWeights) {
        double sum = 0;

        for (int i = 0; i < originalWeights.length; i++)
            sum += originalWeights[i];

        for (int i = 0; i < originalWeights.length; i++)
            originalWeights[i] /= sum;

        return originalWeights;
    }

    public double evaluate(int generation) {
        double sum = 0;
        double best = Double.MIN_VALUE;

        for (int i = 0; i < population.size(); i++) {
            double currFitness = EvaluationFunction.getReturnToRiskRatio(population.get(i), returns, riskCovarianceMatrix);
            //    double currFitness = EvaluationFunction.getReturn(population.get(i), returns);
            sum += currFitness;
            fitnesses.put(i, currFitness);
            if (currFitness > best) best = currFitness;
        }

        System.out.println("GENERATION " + generation + " AVERAGE FITNESS: " + sum / population.size() + "\tBEST FITNESS: " + best);

        return sum / population.size();
    }

    public void run() {
        double prevFitness = -1, currFitness, bestFitness = Double.MIN_VALUE;
        int convergenceCount = 0;

        evaluate(0); //Evaluate

        for (int i = 1; i <= generations; i++) {
            ArrayList<double[]> parents1 = selection(population); //Selection of parent population 1
            ArrayList<double[]> parents2 = selection(population); //Selection of parent population 2

            population = crossover(parents1, parents2, 0.5);//Crossover
            population = mutate(population, 0.1);//Mutation

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
