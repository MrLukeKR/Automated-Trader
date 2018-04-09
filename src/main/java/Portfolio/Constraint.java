package Portfolio;

import java.util.Map;

class Constraint {
    /**
     * Determines whether the constraint of weights summing to one holds
     *
     * @param weights Array of portfolio asset weightings (one per stock)
     * @return True if weights sum to 1, False otherwise
     */
    static boolean sumsToOne(Map<String, Double> weights) {
        double sum = 0;

        for (String stock : weights.keySet()) sum += weights.get(stock);

        return sum >= 0.99 || sum <= 1.01;
    }

    /**
     * Calculates the transaction costs associated with a portfolio rebalance
     *
     * @param originalWeights Asset allocation of the old portfolio
     * @param newWeights      Asset allocation of the new portfolio
     * @param buyCosts        Costs associated with buying each stock
     * @param sellCosts       Costs associated with selling each stock
     * @return A total transaction cost for the cumulative buying and selling of stocks
     */
    static double getTransactionCosts(Map<String, Double> originalWeights, Map<String, Double> newWeights, Map<String, Double> buyCosts, Map<String, Double> sellCosts) {
        double sellCost = 0, buyCost = 0;

        for (String stock : originalWeights.keySet())
            if (originalWeights.get(stock) > newWeights.get(stock)) //If buying a stock
                sellCost += (originalWeights.get(stock) - newWeights.get(stock)) * sellCosts.get(stock);
            else if (originalWeights.get(stock) < newWeights.get(stock)) //If selling a stock
                buyCost += (newWeights.get(stock) - originalWeights.get(stock)) * buyCosts.get(stock);

        return sellCost + buyCost;
    }
}
