package Utility;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.DoubleStream;

public class PortfolioUtils {
    static private final Random rng = new Random();

    static public double[] getRandomWeights(int amount) {
        double[] weights = new double[amount];

        for (int i = 0; i < amount; i++)
            weights[i] = Math.random();

        return scaleWeights(weights);
    }

    static public double[] getEqualWeights(int amount) {
        double[] weights = new double[amount];

        for (int i = 0; i < amount; i++)
            weights[i] = 1.0 / amount;

        return weights;
    }

    static public double[] mutate(double[] weights, double rate) {
        double[] newWeights = new double[weights.length];
        for (int i = 0; i < newWeights.length; i++) {
            if (Math.random() < rate)
                newWeights[i] = weights[i] + rng.nextGaussian();
            if (newWeights[i] < 0) newWeights[i] = 0;
        }

        return scaleWeights(newWeights);
    }

    static private double[] scaleWeights(double[] originalWeights) {
        double sum = DoubleStream.of(originalWeights).parallel().sum();


        double[] newWeights = new double[originalWeights.length];

        for(int i = 0; i < newWeights.length; i++)
            newWeights[i] = originalWeights[i] / sum;

        if(Arrays.stream(newWeights).sum() < 0.99 || Arrays.stream(newWeights).sum() > 1.01)
            System.err.println("WEIGHT ERROR: " + Arrays.stream(originalWeights).sum());


        return newWeights.clone();
    }
}
