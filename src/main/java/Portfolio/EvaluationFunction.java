package Portfolio;

import java.util.Map;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.3
 */

class EvaluationFunction {

    /**
     * Calculates the risk for a given array of weights and a covariance matrix (used for minimisation)
     *
     * @param weights              Array of portfolio asset weightings (one per stock)
     * @param riskCovarianceMatrix Covariance matrix between stocks
     * @return Risk value (variance)
     */
    static double getRisk(Map<String, Double> weights, Map<String, Map<String, Double>> riskCovarianceMatrix) {
        double risk = 0;

        for (String stock1 : weights.keySet())
            for (String stock2 : weights.keySet())
                risk += weights.get(stock1) * weights.get(stock2) * riskCovarianceMatrix.get(stock1).get(stock2);

        return risk;
    }

    /**
     * Calculates the expected return of a set of weights (used for maximisation)
     *
     * @param weights         Array of portfolio asset weightings (one per stock)
     * @param expectedReturns Array of mean returns (one per stock)
     * @return Expected return of the portfolio
     */
    static double getReturn(Map<String, Double> weights, Map<String, Double> expectedReturns) {
        double portfolioReturn = 0;

        if (weights.size() != expectedReturns.size()) return 0;

        for (String stock : expectedReturns.keySet())
            portfolioReturn += weights.get(stock) * expectedReturns.get(stock);

        return portfolioReturn;
    }

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
     * Calculates the ratio of return over risk (used for maximisation)
     *
     * @param weights              Array of portfolio asset weightings (one per stock)
     * @param expectedReturns      Array of mean returns (one per stock)
     * @param riskCovarianceMatrix Covariance matrix between stocks
     * @return Ratio of Return/Risk
     */
    static double getReturnToRiskRatio(Map<String, Double> weights, Map<String, Double> expectedReturns, Map<String, Map<String, Double>> riskCovarianceMatrix) {
        double expectedReturn = getReturn(weights, expectedReturns), expectedRisk = getRisk(weights, riskCovarianceMatrix);

        if (expectedReturn == 0 || expectedRisk == 0) return 0;

        return expectedReturn / expectedRisk;
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
                sellCost += (originalWeights.get(stock) - newWeights.get(stock) * sellCosts.get(stock));
            else if (originalWeights.get(stock) < newWeights.get(stock)) //If selling a stock
                buyCost += (newWeights.get(stock) - originalWeights.get(stock)) * buyCosts.get(stock);

        return sellCost + buyCost;
    }
}
