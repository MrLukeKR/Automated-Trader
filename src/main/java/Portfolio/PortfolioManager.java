package Portfolio;

import Default.DatabaseHandler;

import java.sql.Date;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.3
 */

public class PortfolioManager {

    private static DatabaseHandler dh;

    /**
     * Initialises the Portfolio Manager with a {@link DatabaseHandler} to prevent deadlock when accessing the database
     *
     * @param pmdh Portfolio Manager Database Handler
     */
    public static void initialise(DatabaseHandler pmdh) {
        dh = pmdh;

        System.out.println("Initialised Portfolio Manager");
    }

    /**
     * Gets the n latest prices of a given stock
     *
     * @param symbol Stock ticker to retrieve price data on (e.g. AAPL for Apple Inc.)
     * @param limit  Number of days to retrieve price data for
     * @return A TreeMap containing each price over the last n days, sorted by Trade Date
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public TreeMap<Date, Double> getPrices(String symbol, int limit) throws SQLException {
        TreeMap<Date, Double> prices = new TreeMap<>();

        for (String record : dh.executeQuery("SELECT TradeDate, ClosePrice FROM dailystockprices WHERE Symbol='" + symbol + "' AND ClosePrice is not null AND ClosePrice != 0 ORDER BY TradeDate DESC LIMIT " + limit)) {
            String[] splitRecord = record.split(",");
            prices.put(Date.valueOf(splitRecord[0]),Double.valueOf(splitRecord[1]));
        }

        return prices;
    }

    /**
     * Calculates the covariance between two stocks
     * @param stock1Returns A list of returns for stock 1
     * @param stock2Returns A list of returns for stock 2
     * @return The covariance between two stocks
     */
    private static double calculateCovariance(ArrayList<Double> stock1Returns, ArrayList<Double> stock2Returns) {
        if (stock1Returns.size() != stock2Returns.size()) throw new Error();

        double total = 0;
        double average1 = calculateAverage(stock1Returns);
        double average2 = calculateAverage(stock2Returns);

        for (int i = 0; i < stock1Returns.size(); i++)
            total += (stock1Returns.get(i) - average1) * (stock2Returns.get(i) - average2) ;

        return total / (stock1Returns.size() - 1); // -1 because this is sample based, not population based
    }

    /**
     * Calculates the variance of a stock's returns
     * @param returns List of returns for a given stock
     * @return The variance of the given stock
     */
    private static double calculateVariance(ArrayList<Double> returns) {
        double total = 0;
        double average = calculateAverage(returns);

        for (Double value : returns)
            total += (value - average) * (value - average);

        return total / (returns.size() - 1); // -1 because this is sample based, not population based
    }

    /**
     * Calculates returns, given a list of historic prices
     * @param prices A list of prices for a stock, in date order
     * @param amountOfDays Amount of days between returns
     * @return A list of returns
     */
    private static ArrayList<Double> calculateReturns(TreeMap<Date, Double> prices, int amountOfDays) {
        ArrayList<Double> returns = new ArrayList<>();
        ArrayList<Date> dates = new ArrayList<>(prices.keySet());

        for (int i = 0; i < dates.size() - amountOfDays; i++)
            returns.add(Math.log(prices.get(dates.get(i + amountOfDays)) / prices.get(dates.get(i)))); //Logarithmic Return

        return returns;
    }

    /**
     * Calculates the average value of a list of doubles
     * @param values A list of values (e.g. prices) for which to calculate the mean average
     * @return Mean of the list of values
     */
    private static double calculateAverage(ArrayList<Double> values) {
        double sum = 0;

        for (double value : values) sum += value;

        return sum / values.size();
    }

    /**
     * Generates a covariance matrix, given a list of stocks for consideration
     * @param stocksToCalculate Stocks to calculate the covariance for
     * @return A covariance matrix based on the given list of stocks
     */
    private static double[][] calculateCovarianceMatrix(ArrayList<Stock> stocksToCalculate) {
        double[][] covarianceMatrix = new double[stocksToCalculate.size()][stocksToCalculate.size()];

        for(Stock stock1 : stocksToCalculate)
            for (Stock stock2 : stocksToCalculate)
                covarianceMatrix[stocksToCalculate.indexOf(stock1)][stocksToCalculate.indexOf(stock2)] = calculateCovariance(stock1.getReturns(), stock2.getReturns());

        return covarianceMatrix;
    }

    /**
     * Optimises the asset weights of a portfolio
     * @param method Optimisation method to use (e.g. Genetic Algorithm, Simulated Annealing)
     * @param em Evaluation Method to use (e.g. Maximise Return, Balance Risk and Return)
     * @param holdPeriod How long a stock is expected to be held for (in days)
     * @param prices List of date-sorted prices for a set of stocks
     * @param showDebug True of debug information should be printed, False if otherwise
     * @return A map of stock tickers and their respective percentage weightings
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public Map<String, Double> optimisePortfolio(OptimisationMethod method, EvaluationMethod em, int holdPeriod, TreeMap<String, TreeMap<Date, Double>> prices, boolean showDebug) throws SQLException {
        ArrayList<String> stocks = dh.executeQuery("SELECT Symbol FROM indices ORDER BY Symbol ASC;");
        ArrayList<Stock> calcStocks = new ArrayList<>();

        TreeMap<String, ArrayList<Double>> returns = new TreeMap<>();
        double[] expectedReturns = new double[stocks.size()];


        for (String stock : stocks) {
            Stock currStock = new Stock(stock);
            currStock.setPrices(prices.get(stock));
            currStock.setReturns(calculateReturns(prices.get(stock), holdPeriod));
            returns.put(stock, currStock.getReturns());
            currStock.setExpectedReturn(calculateAverage(currStock.getReturns()));
            currStock.setReturnsVariance(calculateVariance(currStock.getReturns()));
            currStock.setReturnsStandardDeviation(Math.sqrt(currStock.getReturnsVariance()));

            calcStocks.add(currStock);
        }

        calcStocks.sort(Comparator.comparing(Stock::getName));

        int i = 0;
        for (Stock currStock : calcStocks) expectedReturns[i++] = currStock.getExpectedReturn();

        double[][] covarianceMatrix = calculateCovarianceMatrix(calcStocks);
        double[] best = null;

        switch(method) {
            case GENETIC_ALGORITHM:
                best = GAOptimiser.optimise(stocks.size(), em, 1000, 500, expectedReturns, covarianceMatrix, showDebug);
                break;
            case SIMULATED_ANNEALING:
                best = SAOptimiser.optimise(stocks.size(), em, 1, 0.0001, 0.99, 100, expectedReturns, covarianceMatrix, showDebug);
                break;
            case DETERMINISTIC:
                best = DeterministicOptimiser.optimise(stocks.size(), em, expectedReturns, covarianceMatrix);
                break;
        }

        Map<String, Double> portfolio = new HashMap<>();

        for (int j = 0; j < Objects.requireNonNull(best).length; j++) {
            double val = best[j] * 10000;
            int newVal = (int) Math.round(val);
            double percentage = newVal / 100.0;

            if (percentage > 0)
                portfolio.put(stocks.get(j), percentage / 100.0);

            if(showDebug)
                System.out.println(stocks.get(j) + " weight: " + percentage + " (Expected Return: " + (Math.round(expectedReturns[j] * 10000.0) / 100.0) + "\tRisk: " + (calculateVariance(returns.get(stocks.get(j))) + "\tRatio: " + (expectedReturns[j] / calculateVariance(returns.get(stocks.get(j)))) + ")"));
        }

        double expectedReturn = EvaluationFunction.getReturn(best, expectedReturns);

        int eRInt = (int) Math.round(expectedReturn * 10000);

        if(showDebug) {
            System.out.println("----------\r\nPORTFOLIO\r\n----------");
            for (String stock : portfolio.keySet())
                System.out.println(stock + " " + portfolio.get(stock) * 100.0 + "%");
            System.out.println("Expected Return of Portfolio: " + expectedReturn + " (" + eRInt / 100.0 + "%)");
            System.out.println("Risk of Portfolio: " + EvaluationFunction.getRisk(best, covarianceMatrix));
            System.out.println("Expected Return to Risk Ratio: " + EvaluationFunction.getReturnToRiskRatio(best, expectedReturns, covarianceMatrix));
        }
        portfolio.put("RETURN", expectedReturn);

        return portfolio;
    }

    public enum OptimisationMethod {SIMULATED_ANNEALING, GENETIC_ALGORITHM, DETERMINISTIC}

    public enum EvaluationMethod {MAXIMISE_RETURN, MAXIMISE_RETURN_MINIMISE_RISK}
}