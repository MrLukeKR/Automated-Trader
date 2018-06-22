package Portfolio;

import java.sql.Date;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.4
 */

public class Stock {
    private String ticker;
    private TreeMap<Date, Double> prices = new TreeMap<>();
    private ArrayList<Double> returns = new ArrayList<>();
    private double expectedReturn;
    private double returnsVariance;
    private double returnsStandardDeviation;

    /**
     * Initialises the stock class with the stock's ticker
     *
     * @param ticker Stock symbol (e.g. AAPL for Apple Inc.)
     */
    public Stock(String ticker) {
        this.ticker = ticker;
    }

    /**
     * Returns the expected return associated with this stock
     *
     * @return Expected Return (Mean value of stock's prices)
     */
    double getExpectedReturn() {
        return expectedReturn;
    }

    /**
     * Sets the expected return of the stock (i.e. the average mean of stock prices)
     *
     * @param expectedReturn Mean value of stock's prices
     */
    void setExpectedReturn(double expectedReturn) {
        this.expectedReturn = expectedReturn;
    }

    /**
     * Returns the return variance associated with this stock
     *
     * @return Stock returns variance
     */
    double getReturnsVariance() {
        return returnsVariance;
    }

    /**
     * Sets the variance of the stock's returns
     *
     * @param returnsVariance Variance of returns
     */
    void setReturnsVariance(double returnsVariance) {
        this.returnsVariance = returnsVariance;
    }

    /**
     * Returns the stock ticker associated with this stock
     *
     * @return Stock ticker
     */
    String getTicker() {
        return ticker;
    }

    /**
     * Returns the stock returns associated with this stock
     *
     * @return Stock returns
     */
    ArrayList<Double> getReturns() {
        return returns;
    }

    /**
     * Sets the returns associated with this stock
     *
     * @param returns List of returns, previously calculated from a list of the stock's prices
     */
    void setReturns(ArrayList<Double> returns) {
        this.returns = returns;
    }

    /**
     * Returns the stock prices associated with this stock
     *
     * @return Stock prices
     */
    public TreeMap<Date, Double> getPrices() {
        return prices;
    }

    /**
     * Sets the prices associated with this stock
     *
     * @param prices List of prices (sorted in ascending order by Trade Date) for this stock
     */
    void setPrices(TreeMap<Date, Double> prices) {
        this.prices = prices;
    }

    /**
     * Return the Standard Deviation associated with this stock
     * @return Standard Deviation
     */
    public double getReturnsStandardDeviation() {
        return returnsStandardDeviation;
    }

    /**
     * Sets the Standard Deviation of the stock's returns
     *
     * @param returnsStandardDeviation Standard Deviation of returns
     */
    void setReturnsStandardDeviation(double returnsStandardDeviation) {this.returnsStandardDeviation = returnsStandardDeviation;}
}