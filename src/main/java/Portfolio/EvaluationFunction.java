package Portfolio;

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
    static double getRisk(double[] weights, double[][] riskCovarianceMatrix) {
        double risk = 0;

        for (int i = 0; i < weights.length; i++)
            for (int j = 0; j < weights.length; j++)
                risk += weights[i] * weights[j] * riskCovarianceMatrix[i][j];

        return risk;
    }

    /**
     * Calculates the expected return of a set of weights (used for maximisation)
     *
     * @param weights         Array of portfolio asset weightings (one per stock)
     * @param expectedReturns Array of mean returns (one per stock)
     * @return Expected return of the portfolio
     */
    static double getReturn(double[] weights, double[] expectedReturns) {
        double portfolioReturn = 0;

        if (weights.length != expectedReturns.length) return 0;

        for (int i = 0; i < weights.length; i++) portfolioReturn += weights[i] * expectedReturns[i];

        return portfolioReturn;
    }

    /**
     * Determines whether the constraint of weights summing to one holds
     *
     * @param weights Array of portfolio asset weightings (one per stock)
     * @return True if weights sum to 1, False otherwise
     */
    static boolean sumsToOne(double[] weights) {
        double sum = 0;

        for (double weight : weights) sum += weight;

        return sum == 1;
    }

    /**
     * Calculates the ratio of return over risk (used for maximisation)
     *
     * @param weights              Array of portfolio asset weightings (one per stock)
     * @param expectedReturns      Array of mean returns (one per stock)
     * @param riskCovarianceMatrix Covariance matrix between stocks
     * @return Ratio of Return/Risk
     */
    static double getReturnToRiskRatio(double[] weights, double[] expectedReturns, double[][] riskCovarianceMatrix) {
        double expectedReturn = getReturn(weights, expectedReturns), expectedRisk = getRisk(weights, riskCovarianceMatrix);

        if (expectedReturn == 0 || expectedRisk == 0) return 0;

        return expectedReturn / expectedRisk;
    }
}
