package GeneticAlgorithm;

import java.util.*;

public class GeneticAlgorithm {
    ArrayList<double[]> population = new ArrayList<>();
    double[] returns;
    Map<Integer, Double> fitnesses = new HashMap<>();
    int generations;
    double[][] riskCovarianceMatrix;

    private double[] getRandomWeights(int amount) {
        double[] weights = new double[amount];
        double total = 0;

        for (int i = 0; i < amount; i++)
            total += weights[i] = Math.random();

        for (int i = 0; i < amount; i++)
            weights[i] /= total;

        return weights;
    }

    public void initialise(int amountOfWeights, int numberOfGenerations, int populationSize, double[] returns, double[][] riskCovarianceMatrix) {
        generations = numberOfGenerations;
        for (int i = 0; i < populationSize; i++)
            population.add(getRandomWeights(amountOfWeights));

        this.returns = returns;
        this.riskCovarianceMatrix = riskCovarianceMatrix;
    }

    public ArrayList<double[]> selection() {
        ArrayList<double[]> selectedPopulation = new ArrayList<>();
        ArrayList<Map.Entry<Integer, Double>> orderedIndividuals = new ArrayList<>(fitnesses.entrySet());
        Collections.sort(orderedIndividuals, Comparator.comparing(Map.Entry::getValue));
        Collections.reverse(orderedIndividuals);

        double totalFitness = 0;

        for (int i = 0; i < population.size(); i++)
            totalFitness += fitnesses.get(i);

        for (int i = 0; i < population.size(); i++) {
            double selectionPoint = Math.random() * totalFitness, accumulatedFitnesss = 0;
            int selectInd = 0;

            while ((accumulatedFitnesss += orderedIndividuals.get(selectInd).getValue()) < selectionPoint) selectInd++;

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

        return newWeights;
    }

    public ArrayList<double[]> mutate(double rate) {
        ArrayList<double[]> mutatedPopulation = new ArrayList<>();
        for (double[] individual : population)
            mutatedPopulation.add(mutate(individual, rate));

        return mutatedPopulation;
    }

    public double[] mutate(double[] weights, double rate) {
        double temp;

        for (int i = 0; i < weights.length - 1; i++) {
            if (Math.random() < rate) {
                temp = weights[i];
                weights[i] = weights[i + 1];
                weights[i + 1] = temp;
            }
        }

        return weights;
    }

    public void evaluate(int generation) {
        double sum = 0;

        for (int i = 0; i < population.size(); i++)
            fitnesses.put(i, sum += EvaluationFunction.getReturnToRiskRatio(population.get(i), returns, riskCovarianceMatrix));

        System.out.println("GENERATION " + generation + " FITNESS: " + sum / population.size());
    }

    public void run() {
        for (int i = 0; i < generations; i++) {
            evaluate(i); //Evaluate

            ArrayList<double[]> parents1 = selection(); //Selection of parent population 1
            ArrayList<double[]> parents2 = selection(); //Selection of parent population 2

            population = crossover(parents1, parents2, 0.5);//Crossover
            population = mutate(0.5);//Mutation
        }
    }

    public double[] getBest() {
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
