package Utility;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.6
 */

public class PortfolioUtils {
    static private Random rng = new Random();

    /**
     * Creates a collection of random asset weights
     *
     * @param stocks Stocks to create random weights for
     * @return A Map of Stock-to-Weight values
     */
    static public Map<String, Double> getRandomWeights(ArrayList<String> stocks) {
        Map<String, Double> weights = new TreeMap<>();

        for (String stock : stocks)
            weights.put(stock, Math.random());

        return scaleWeights(weights);
    }

    /**
     * Creates a collection of equal asset weights
     *
     * @param stocks Stocks to create equal weights for
     * @return A Map of Stock-to-Weight values
     */
    static public Map<String, Double> getEqualWeights(ArrayList<String> stocks) {
        Map<String, Double> weights = new TreeMap<>();

        for (String stock : stocks)
            weights.put(stock, 1.0 / stocks.size());

        return weights;
    }

    /**
     * Alters a set of weights away from its original value in order to create a novel solution
     *
     * @param weights Original weight values that are to be mutated
     * @param rate    The probability that a gene will be mutated
     * @return Mutated collection of Stock-to-Weight values
     */
    static public Map<String, Double> mutate(Map<String, Double> weights, double rate) {
        Map<String, Double> newWeights = new TreeMap<>(weights);
        for (String stock : newWeights.keySet())
            if (Math.random() < rate) newWeights.put(stock, Math.max(newWeights.get(stock) + rng.nextGaussian(), 0));

        return scaleWeights(newWeights);
    }

    /**
     * Scales the weights to sum to 1 (i.e. so that the "sum to one" constraint cannot be violated)
     *
     * @param weights Original asset weightings that are to be scaled
     * @return Scaled weights that conform to the "sum to one" constraint
     */
    static private Map<String, Double> scaleWeights(Map<String, Double> weights) {
        double sum = weights.entrySet().stream().mapToDouble(Map.Entry::getValue).sum();
        weights.entrySet().forEach(x -> x.setValue(x.getValue() / sum));
        return weights;
    }
}