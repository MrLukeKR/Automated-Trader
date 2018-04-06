package Portfolio;

import Utility.PortfolioUtils;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.9
 */

class DeterministicOptimiser {
    static Map<String, Double> optimise(ArrayList<String> stocks, PortfolioManager.EvaluationMethod em, Map<String, Double> returnsArray, Map<String, Map<String, Double>> riskCovarianceMatrix) {
        Map<String, Double> weights = PortfolioUtils.getEqualWeights(stocks);

        return weights;
    }
}
