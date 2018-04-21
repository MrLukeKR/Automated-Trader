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
     * Calculates the ratio of return over risk (used for maximisation)
     *
     * @param weights              Array of portfolio asset weightings (one per stock)
     * @param expectedReturns      Array of mean returns (one per stock)
     * @param riskCovarianceMatrix Covariance matrix between stocks
     * @return Ratio of Return/Risk
     */
    static double getReturnToRiskRatio(Map<String, Double> weights, Map<String, Double> expectedReturns, Map<String, Map<String, Double>> riskCovarianceMatrix) {
        double expectedReturn = getReturn(weights, expectedReturns),
                expectedRisk = getRisk(weights, riskCovarianceMatrix);

        return (expectedReturn == 0 || expectedRisk == 0) ? 0 : expectedReturn / expectedRisk;
    }
}
