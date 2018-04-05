package Portfolio;

import Utility.MathUtils;
import Utility.PortfolioUtils;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.9
 */

public class DeterministicOptimiser {
    static public double[] optimise(int amountOfWeights, PortfolioManager.EvaluationMethod em, double[] returnsArray, double[][] riskCovarianceMatrix){
        double[] weights = PortfolioUtils.getEqualWeights(amountOfWeights);



     //   MultivariateVectorOptimizer mvo = new MultivariateVectorOptimizer();
    return null;
    }

    static public double target(double[] weights, double[] returns, double[][] covarianceMatrix){
        double[] first = MathUtils.dot(weights, covarianceMatrix, true);
        double second = MathUtils.dot(first,weights) * 252;

        double third = MathUtils.dot(weights,returns) * 252;
        return Math.sqrt(second / third);
    }
}
