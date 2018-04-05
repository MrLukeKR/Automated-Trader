package Portfolio;

import Utility.MathUtils;
import Utility.PortfolioUtils;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.9
 */

public class DeterministicOptimiser {
    static Map<String, Double> optimise(ArrayList<String> stocks, PortfolioManager.EvaluationMethod em, Map<String, Double> returnsArray, double[][] riskCovarianceMatrix) {
        Map<String, Double> weights = PortfolioUtils.getEqualWeights(stocks);



     //   MultivariateVectorOptimizer mvo = new MultivariateVectorOptimizer();
        return weights;
    }

    static public double target(double[] weights, double[] returns, double[][] covarianceMatrix){
        double[] first = MathUtils.dot(weights, covarianceMatrix, true);
        double second = MathUtils.dot(first,weights) * 252;

        double third = MathUtils.dot(weights,returns) * 252;
        return Math.sqrt(second / third);
    }
}
