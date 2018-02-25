import GeneticAlgorithm.EvaluationFunction;
import GeneticAlgorithm.GAOptimiser;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class PortfolioManager {

    private static DatabaseHandler dh;

    public static void initialise(DatabaseHandler pmdh) {
        dh = pmdh;

        System.out.println("Initialised Portfolio Manager");
    }

    static private ArrayList<Double> getPrices(String symbol, int limit) throws SQLException {
        ArrayList<Double> prices = new ArrayList<>();

        for (String record : dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + symbol + "' AND ClosePrice is not null AND ClosePrice != 0 ORDER BY TradeDate DESC LIMIT " + limit))
            prices.add(Double.valueOf(record));

        Collections.reverse(prices);

        return prices;
    }

    private static double calculateCovariance(ArrayList<Double> stock1Returns, ArrayList<Double> stock2Returns) {
        if (stock1Returns.size() != stock2Returns.size())
            throw new Error(); //TODO: Customise this error

        double total = 0;

        double average1 = calculateAverage(stock1Returns);
        double average2 = calculateAverage(stock2Returns);

        for (int i = 0; i < stock1Returns.size(); i++)
            total += (stock1Returns.get(i) - average1) * (stock2Returns.get(i) - average2);

        return total / (stock1Returns.size() - 1); // -1 because this is sample based, not population based
    }

    private static double calculateVariance(ArrayList<Double> returns) {
        double total = 0;
        double average = calculateAverage(returns);

        for (Double value : returns)
            total += (value - average) * (value - average);

        return total / (returns.size() - 1); // -1 because this is sample based, not population based
    }

    private static ArrayList<Double> calculateReturns(ArrayList<Double> prices, int amountOfDays) {
        ArrayList<Double> returns = new ArrayList<>();

        for (int i = 0; i < prices.size() - amountOfDays; i++)
            returns.add(Math.log(prices.get(i + amountOfDays) / prices.get(i))); //Logarithmic Return
        //returns.add((prices.get(i + amountOfDays) / prices.get(i)) -1); //Arithmetic Return

        return returns;
    }

    private static double calculateAverage(ArrayList<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).sum() / values.size();
    }

    private static double[][] calculateCovarianceMatrix(ArrayList<ArrayList<Double>> returns) {
        double[][] covarianceMatrix = new double[returns.size()][returns.size()];

        for (int i = 0; i < returns.size(); i++)
            for (int j = 0; j < returns.size(); j++)
                covarianceMatrix[i][j] = calculateCovariance(returns.get(i), returns.get(j));

        return covarianceMatrix;
    }

    static public Map<String, Double> optimisePortfolio() throws SQLException {
        int timeFrame = 5; //The last n days to consider (A shorter value can be susceptible to noise, but longer values are susceptible to long-term trend bias)
        int holdPeriod = 1; //Amount of days you want to hold this portfolio

        ArrayList<String> stocks = dh.executeQuery("SELECT Symbol FROM indices;");
        ArrayList<ArrayList<Double>> returns = new ArrayList<>();
        double[] expectedReturns = new double[stocks.size()];

        int i = 0;

        for (String stock : stocks) {
            ArrayList<Double> currentReturns = calculateReturns(getPrices(stock, timeFrame), holdPeriod);
            returns.add(currentReturns);
            expectedReturns[i++] = calculateAverage(currentReturns);
        }

        double[][] covarianceMatrix = calculateCovarianceMatrix(returns);

        GAOptimiser ga = new GAOptimiser();

        ga.initialise(stocks.size(), 1000, 200, expectedReturns, covarianceMatrix);
        ga.run();

        double[] best = ga.getBest();

        Map<String, Double> portfolio = new HashMap<>();

        for (int j = 0; j < best.length; j++) {
            double val = best[j] * 10000;
            int newVal = (int) Math.round(val);
            double percentage = newVal / 100.0;

            if (percentage > 0)
                portfolio.put(stocks.get(j), percentage / 100.0);

            System.out.println(stocks.get(j) + " weight: " + percentage + " (Expected Return: " + Math.round(expectedReturns[j] * 10000.0) / 100.0 + "\tRisk: " + calculateVariance(returns.get(j)) + "\tRatio: " + expectedReturns[j] / calculateVariance(returns.get(j)) + ")");
        }

        double expectedReturn = EvaluationFunction.getReturn(best, expectedReturns);

        int eRInt = (int) Math.round(expectedReturn * 10000);

        System.out.println("----------\r\nPORTFOLIO\r\n----------");
        for (String stock : portfolio.keySet()) System.out.println(stock + " " + portfolio.get(stock) * 100.0 + "%");
        System.out.println("Expected Return of Portfolio: " + expectedReturn + " (" + eRInt / 100.0 + "%)");
        System.out.println("Risk of Portfolio: " + EvaluationFunction.getVariance(best, covarianceMatrix));
        System.out.println("Expected Return to Risk Ratio: " + EvaluationFunction.getReturnToRiskRatio(best, expectedReturns, covarianceMatrix));

        portfolio.put("RETURN", expectedReturn);

        return portfolio;
    }
}