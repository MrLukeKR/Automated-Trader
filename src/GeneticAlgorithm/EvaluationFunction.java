package GeneticAlgorithm;

public class EvaluationFunction {
    static public int constraintHolds(double[] weights) {
        double sum = 0;

        for (int i = 0; i < weights.length; i++)
            sum += weights[i];

        if (sum > 0.99 && sum < 1.01)
            return 1;
        else
            return 0;
    }

    static public double getVariance(double[] weights, double[][] riskCovarianceMatrix) {
        double fitness = 0;

        for (int i = 0; i < weights.length; i++)
            for (int j = 0; j < weights.length; j++)
                fitness += weights[i] * weights[j] * riskCovarianceMatrix[i][j];

        return fitness;
    }

    static public double getReturn(double[] weights, double[] expectedReturns) {
        double fitness = 0;

        if (weights.length != expectedReturns.length)
            return 0;

        for (int i = 0; i < weights.length; i++)
            fitness += weights[i] * expectedReturns[i];

        return fitness;
    }


    static public double getReturnToRiskRatio(double[] weights, double[] expectedReturns, double[][] riskCovarianceMatrix) {
        double expectedReturn = getReturn(weights, expectedReturns), expectedRisk = getVariance(weights, riskCovarianceMatrix);

        if (expectedReturn == 0 || expectedRisk == 0)
            return 0;

        double ratio = expectedReturn / expectedRisk;

        return Math.max(0, ratio);
    }
}
