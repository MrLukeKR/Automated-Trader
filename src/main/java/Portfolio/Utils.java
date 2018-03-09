package Portfolio;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.DoubleStream;

public class Utils {
    static private final Random rng = new Random();

    static public double[] getRandomWeights(int amount) {

        double[] weights = new double[amount];

        for (int i = 0; i < amount; i++)
            weights[i] = Math.random();

        return scaleWeights(weights);
    }

    static public double[] mutate(double[] weights, double rate) {
        for (int i = 0; i < weights.length; i++) {
            if (Math.random() < rate)
                weights[i] += rng.nextGaussian();
            if (weights[i] < 0) weights[i] = 0;
        }

        return scaleWeights(weights);
    }

    static private double[] scaleWeights(double[] originalWeights) {
        double sum = DoubleStream.of(originalWeights).parallel().sum();

        originalWeights = Arrays.stream(originalWeights).map(v -> v /= sum).toArray();

        if(Arrays.stream(originalWeights).sum() < 0.99 || Arrays.stream(originalWeights).sum() > 1.01)
            System.err.println("WEIGHT ERROR: " + Arrays.stream(originalWeights).sum());

        return originalWeights;
    }
}
