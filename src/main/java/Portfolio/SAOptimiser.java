package Portfolio;

import Utility.PortfolioUtils;

import static Utility.PortfolioUtils.getEqualWeights;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.3
 */

public class SAOptimiser {

    static public double[] optimise(int noOfStocks, PortfolioManager.EvaluationMethod em, double initialTemperature, double minimumTemperature, double coolRate, int iterations, double[] expectedReturns, double[][] riskCovarianceMatrix, boolean showDebug) {
        double t = initialTemperature;
        double[] solution = getEqualWeights(noOfStocks);
        double bestFitness;
        if(em == PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK)
            bestFitness = EvaluationFunction.getReturnToRiskRatio(solution, expectedReturns, riskCovarianceMatrix);
        else
            bestFitness = EvaluationFunction.getReturn(solution,expectedReturns);

        double currentFitness = bestFitness;
        double[] currentSolution = solution.clone();

        while(t > minimumTemperature){
            for(int i = 0; i < iterations; i++) {
                double[] candidateSolution = PortfolioUtils.mutate(currentSolution, 1);
                double fitness;

                if(em == PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK)
                    fitness = EvaluationFunction.getReturnToRiskRatio(candidateSolution, expectedReturns, riskCovarianceMatrix);
                else
                    fitness = EvaluationFunction.getReturn(candidateSolution,expectedReturns);

                if (showDebug)
                    System.out.println("ITERATION " + i + " - CURRENT FITNESS: " + currentFitness + " (RETURN: " + (EvaluationFunction.getReturn(currentSolution,expectedReturns) * 100.0) + "%) CANDIDATE FITNESS: " + fitness + " (RETURN: " + (EvaluationFunction.getReturn(candidateSolution,expectedReturns) * 100.0) + "%) BEST FITNESS: " + bestFitness + " (RETURN: " + (EvaluationFunction.getReturn(solution,expectedReturns) * 100) + "%)");

                if(fitness >= currentFitness){
                    currentFitness = fitness;
                    currentSolution = candidateSolution.clone();
                    if (fitness >= bestFitness) {
                        bestFitness = fitness;
                        solution = candidateSolution.clone();
                    }
                }else if((Math.exp((currentFitness - fitness) / t) > Math.random())) {
                    currentFitness = fitness;
                    currentSolution = candidateSolution.clone();
                }
            }
            t *= coolRate;
        }

        return solution;
    }
}
