package Prediction;

import Portfolio.PortfolioManager;

import java.sql.Date;
import java.sql.SQLException;
import java.util.*;

public class SimulationModel {
    Map<String, Double> portfolio = new HashMap<>();
    Map<String, Map<Integer, ArrayList<Investment>>> multidayInvestments = new HashMap<>();
    Map<String, Double> investments = new HashMap<>();
    Map<String, Integer> heldStocks = new HashMap<>();
    private String name;
    private double balance = 0;
    private double stockWorth = 0;
    private double balanceMin = 0, balanceMax = 0;
    private double bought = 0, sold = 0;

    public SimulationModel(String name, ArrayList<String> stocks, Set<Integer> dayArray) {
        this.name = name;
        initialiseMultidayInvestments(stocks, dayArray);
    }

    public void initialiseIteration() {
        stockWorth = 0;
        bought = 0;
        sold = 0;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public double getStockWorth() {
        return stockWorth;
    }

    public void setStockWorth(double stockWorth) {
        this.stockWorth = stockWorth;
    }

    public Map getPortfolio() {
        return portfolio;
    }

    public void setPortfolio(Map<String, Double> portfolio) {
        this.portfolio = portfolio;
    }

    public void updateStockWorth(String stock, double price) {
        if (heldStocks.containsKey(stock) && heldStocks.get(stock) > 0)
            stockWorth += heldStocks.get(stock) * price;
    }

    private void initialiseMultidayInvestments(ArrayList<String> stocks, Set<Integer> dayArray) {
        for (String stock : stocks) {
            if (!multidayInvestments.containsKey(stock)) multidayInvestments.put(stock, new TreeMap<>());
            for (Integer day : dayArray)
                if (!multidayInvestments.get(stock).containsKey(day))
                    multidayInvestments.get(stock).put(day, new ArrayList<>());
        }
    }

    public void tradeStock(String stock, double price, HashMap<Integer, Boolean> predictions) {
        int splitFactor = 0;
        for (int predictionDay : predictions.keySet())
            if (predictions.get(predictionDay)) splitFactor++;

        for (Integer day : multidayInvestments.get(stock).keySet()) {
            for (int i = 0; i < multidayInvestments.get(stock).get(day).size(); i++) {
                Investment investment = multidayInvestments.get(stock).get(day).get(i);
                multidayInvestments.get(stock).get(day).get(i).decrementTime();
                if (investment.hasExpired()) {
                    //if (!predictions.get(day))
                    sellStock(stock, price, investment.getQuantity());
                    //else
                    //    multidayInvestments.get(stock).get(day).get(i).resetTime();
                }
            }

            multidayInvestments.get(stock).get(day).removeIf(i -> i.hasExpired());
        }

        for (Integer day : predictions.keySet()) {
            if (predictions.get(day)) {
                buyStock(stock, price, splitFactor);
                multidayInvestments.get(stock).get(day).add(new Investment(day, splitFactor, price));
            }
        }
    }

    public void sellAllStock(String stock, double price) {
        if (heldStocks.containsKey(stock)) sellStock(stock, price, heldStocks.get(stock));
    }

    public void sellStock(String stock, double price, int quantity) {
        if (heldStocks.containsKey(stock) && heldStocks.get(stock) > 0 && quantity <= heldStocks.get(stock)) {
            double worth = quantity * price;
            sold += worth;
            balance += worth;
            investments.put(stock, investments.getOrDefault(stock, 0.0) - worth);
            heldStocks.put(stock, heldStocks.get(stock) - quantity);
        }
    }

    public void buyStock(String stock, double price, int splitFactor) {
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

    public void updateCutoff(double cutoff) {
        balanceMin = balance * (1 - cutoff);
        balanceMax = balance * (1 + cutoff);
    }

    public void rebalancePortfolio(HashMap<String, TreeMap<Date, String>> testingRecords, Date date, int holdPeriod, TreeMap<String, TreeMap<Date, Double>> reducedPrices) throws SQLException {
        if (balance + stockWorth <= balanceMin || balance + stockWorth >= balanceMax) {
            portfolio = PortfolioManager.optimisePortfolio(PortfolioManager.OptimisationMethod.SIMULATED_ANNEALING, PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK, holdPeriod, reducedPrices, false);
            double cutoff = portfolio.get("RETURN");
            portfolio.remove("RETURN");
            /*
            for (String symbol : heldStocks.keySet()) {
                double worth = Double.parseDouble(testingRecords.get(symbol).get(date).split(",")[5]) * heldStocks.get(symbol);
                balance += worth;
                sold += worth;
            }

            heldStocks.clear();
            investments.clear();
            */
            updateCutoff(cutoff);
        }
    }

    public double getTotal() {
        return stockWorth + balance;
    }

    public void finalise(double initialBalance) {
        balance += stockWorth;
        stockWorth = 0;

        System.out.println(name.toUpperCase() + "\tFinished Simulation with: " + balance + " -> " + ((balance - initialBalance) / initialBalance * 100) + "% return");
    }

    public void printStatus() {
        System.out.println(name.toUpperCase() + "\t Balance: " + balance + ", Stock Worth: " + stockWorth + ", Total: " + (balance + stockWorth) + "(BOUGHT: " + bought + ", SOLD:" + sold + ")");
    }
}
