package AIOptimisation;

import static AIOptimisation.Utils.getRandomWeights;

/**
 * Simulated Annealing Portfolio Optimiser
 */
public class SAOptimiser {

    static public double[] optimise(int noOfStocks, double initialTemperature, double minimumTemperature, double coolRate, int iterations, double[] expectedReturns, double[][] riskCovarianceMatrix){
        double t = initialTemperature;
        double[] solution = getRandomWeights(noOfStocks);
        double bestFitness = EvaluationFunction.getReturnToRiskRatio(solution,expectedReturns,riskCovarianceMatrix);
        double currentFitness = bestFitness;
        double[] currentSolution = solution.clone();

        while(t > minimumTemperature){
            for(int i = 0; i < iterations; i++) {
                double[] candidateSolution = Utils.mutate(currentSolution, 1);
                double fitness = EvaluationFunction.getReturnToRiskRatio(candidateSolution, expectedReturns, riskCovarianceMatrix);
                System.out.println("ITERATION " + i + " - CURRENT FITNESS: " + currentFitness + " CANDIDATE FITNESS: " + fitness + " BEST FITNESS: " + bestFitness);

                if(fitness > currentFitness){
                    currentFitness = fitness;
                    currentSolution = candidateSolution.clone();
                    if (fitness > bestFitness) {
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
