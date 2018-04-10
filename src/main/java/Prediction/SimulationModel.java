package Prediction;

import Portfolio.PortfolioManager;

import java.sql.Date;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.6
 */

public class SimulationModel {
    private Map<String, Double> portfolio = new HashMap<>(), investments = new HashMap<>();
    private Map<String, Integer> heldStocks = new HashMap<>();
    private Map<String, Map<Integer, ArrayList<Investment>>> multidayInvestments = new HashMap<>();
    private String name;
    private double balance = 0, stockWorth = 0, balanceMin = 0, balanceMax = 0, bought = 0, sold = 0;

    /**
     * Initialises the Simulation Model with the name of the model, a list of stocks and a set of trading periods to simulate
     *
     * @param name     Name of the Simulation Model
     * @param stocks   List of stocks to simulate trading on
     * @param dayArray Set of trading periods to simulate
     */
    SimulationModel(String name, ArrayList<String> stocks, Set<Integer> dayArray) {
        this.name = name;
        initialiseMultidayInvestments(stocks, dayArray);
    }

    /**
     * Resets all iteration-dependent variables to 0
     */
    void initialiseIteration() {
        stockWorth = 0;
        bought = 0;
        sold = 0;
    }

    /**
     * Gets the current balance of the simulation model
     * @return Balance of the simulation model
     */
    public double getBalance() {
        return balance;
    }

    /**
     * Sets the balance (i.e. available funds) of the simulation model
     *
     * @param balance Current amount of money available
     */
    void setBalance(double balance) {
        this.balance = balance;
    }

    /**
     * Returns the asset weighting of the portfolio
     *
     * @return Map of stock-to-asset-weight values
     */
    public Map<String, Double> getPortfolio() {
        return portfolio;
    }

    /**
     * Sets the portfolio of the simulation model
     * @param portfolio Portfolio of stock-to-asset-weighting values
     */
    public void setPortfolio(Map<String, Double> portfolio) {
        this.portfolio = portfolio;
    }

    /**
     * Updates the total value of held stocks
     *
     * @param stock Stock to calculate the worth of
     * @param price Current price of the given stock
     */
    void updateStockWorth(String stock, double price) {
        if (heldStocks.containsKey(stock) && heldStocks.get(stock) > 0)
            stockWorth += heldStocks.get(stock) * price;
    }

    /**
     * Initialises the arrays used to maintain the multi-day investments
     * @param stocks List of stocks to consider for investment
     * @param dayArray Set of trading periods to simulate
     */
    private void initialiseMultidayInvestments(ArrayList<String> stocks, Set<Integer> dayArray) {
        for (String stock : stocks) {
            if (!multidayInvestments.containsKey(stock)) multidayInvestments.put(stock, new TreeMap<>());
            for (Integer day : dayArray)
                if (!multidayInvestments.get(stock).containsKey(day))
                    multidayInvestments.get(stock).put(day, new ArrayList<>());
        }
    }

    /**
     * Simulate the trading of a stock
     *
     * @param stock       Stock ticker to trade
     * @param price       Current price of the stock
     * @param predictions Map of Day-to-Prediction values
     */
    void tradeStock(String stock, double price, HashMap<Integer, Boolean> predictions) {
        int splitFactor = 0;
        for (int predictionDay : predictions.keySet())
            if (predictions.get(predictionDay)) splitFactor++;

        for (Integer day : multidayInvestments.get(stock).keySet()) {
            for (int i = 0; i < multidayInvestments.get(stock).get(day).size(); i++) {
                Investment investment = multidayInvestments.get(stock).get(day).get(i);
                multidayInvestments.get(stock).get(day).get(i).decrementTime();
                if (investment.hasExpired()) {
                    if (!predictions.get(day))
                        sellStock(stock, price, investment.getQuantity());
                    else
                        multidayInvestments.get(stock).get(day).get(i).resetTime();
                }
            }

            multidayInvestments.get(stock).get(day).removeIf(Investment::hasExpired);
        }

        for (Integer day : predictions.keySet()) {
            if (predictions.get(day)) {
                buyStock(stock, price, splitFactor);
                multidayInvestments.get(stock).get(day).add(new Investment(day, splitFactor, price));
            }
        }
    }

    /**
     * Sells all shares of a given stock
     *
     * @param stock Stock to sell all shares of
     * @param price Current price of the stock
     */
    void sellAllStock(String stock, double price) {
        if (heldStocks.containsKey(stock)) sellStock(stock, price, heldStocks.get(stock));
        multidayInvestments.get(stock).clear();
    }

    /**
     * Sells a given number of shares of a given stock
     *
     * @param stock    Stock to sell
     * @param price    Current price of the stock
     * @param quantity Quantity of the stock to sell
     */
    private void sellStock(String stock, double price, int quantity) {
        if (heldStocks.containsKey(stock) && heldStocks.get(stock) > 0 && quantity <= heldStocks.get(stock)) {
            double worth = quantity * price;
            sold += worth;
            balance += worth;
            investments.put(stock, investments.getOrDefault(stock, 0.0) - worth);
            heldStocks.put(stock, heldStocks.get(stock) - quantity);
        }
    }

    /**
     * Buys a given number of shares of a given stock
     * @param stock Stock to buy
     * @param price Current price of the stock
     * @param splitFactor Quantity of the stock to buy
     */
    void buyStock(String stock, double price, int splitFactor) {
        if (!portfolio.containsKey(stock)) return;

        double allocation;
        //if(price <= (allocation = (portfolio.get(stock) * balance) - investments.getOrDefault(stock, 0.0))){
        if (price <= (allocation = (portfolio.get(stock) * balance))) {
            int amount = (int) Math.floor(allocation / price) / splitFactor;
            if (balance - (amount * price) >= 0) {
                double totalCost = amount * price;
                balance -= totalCost;
                bought += totalCost;
                investments.put(stock, investments.getOrDefault(stock, 0.0) + totalCost);
                heldStocks.put(stock, heldStocks.getOrDefault(stock, 0) + amount);
            }
        }
    }

    /**
     * Updates the cutoff point in which the portfolio will rebalance if the balance falls above or below two bounds
     * @param cutoff Cutoff percentage
     */
    void updateCutoff(double cutoff) {
        balanceMin = balance * (1 - cutoff);
        balanceMax = balance * (1 + cutoff);
    }

    /**
     * Rebalances the portfolio using the latest price information
     *
     * @param testingRecords Records used to gather the latest price information
     * @param date           Current date to consider
     * @param holdPeriod     Amount of time (in days) to hold a stock before considering for sale/reinvestment
     * @param latestPrices   Collection of latest n days of prices
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    void rebalancePortfolio(HashMap<String, TreeMap<Date, String>> testingRecords, Date date, int holdPeriod, TreeMap<String, TreeMap<Date, Double>> latestPrices, boolean sellAllStocks) throws SQLException {
        if (balance + stockWorth <= balanceMin || balance + stockWorth >= balanceMax) {
            portfolio.remove("RETURN");
            portfolio = PortfolioManager.optimisePortfolio(PortfolioManager.OptimisationMethod.GENETIC_ALGORITHM, PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK, holdPeriod, latestPrices, portfolio, false);
            double cutoff = portfolio.get("RETURN");
            portfolio.remove("RETURN");

            if (sellAllStocks) {
                for (String symbol : heldStocks.keySet())
                    sellAllStock(symbol, Double.parseDouble(testingRecords.get(symbol).get(date).split(",")[5]));

                heldStocks.clear();
                investments.clear();
            }
            updateCutoff(cutoff);
        }
    }

    /**
     * Gets the current total monetary value of this simulation model
     * @return The value of held stock + the current total of available funds
     */
    double getTotal() {
        return stockWorth + balance;
    }

    /**
     * Sells all remaining stock and prints the final performance measurement of the simulation
     * @param initialBalance Starting balance of the model
     */
    void finalise(double initialBalance) {
        balance += stockWorth;
        stockWorth = 0;

        System.out.println(name.toUpperCase() + "\tFinished Simulation with: " + balance + " -> " + ((balance - initialBalance) / initialBalance * 100) + "% return");
    }

    /**
     * Prints the current balance, stock worth and status to the console
     */
    void printStatus() {
        System.out.println(name.toUpperCase() + "\t Balance: " + balance + ", Stock Worth: " + stockWorth + ", Total: " + (balance + stockWorth) + "(BOUGHT: " + bought + ", SOLD:" + sold + ")");
    }
}