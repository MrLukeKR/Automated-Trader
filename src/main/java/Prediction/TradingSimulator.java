package Prediction;

import Default.DatabaseHandler;
import Default.Main;
import Portfolio.PortfolioManager;
import Processing.NaturalLanguageProcessor;
import Utility.TrainingFileUtils;
import org.apache.spark.mllib.linalg.DenseVector;

import java.io.*;
import java.sql.Date;
import java.sql.SQLException;
import java.util.*;


public class TradingSimulator {
    static private DatabaseHandler dh;

    static public void initialise(DatabaseHandler tsdh){
        dh = tsdh;
    }

    static public HashMap<String, ArrayList<String>> getSplit(String stock, int testingTimeFrame, int index) throws SQLException {
        HashMap<String, ArrayList<String>> values = new HashMap<>();

        ArrayList<String> dbSchema = dh.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        String commandStart = "SELECT COALESCE(" + dbSchema.get(0) + ",0)";
        for(int i = 1; i < dbSchema.size(); i++)
            commandStart += ",COALESCE(" + dbSchema.get(i)+", 0)";
        commandStart += " FROM (SELECT * FROM dailystockprices WHERE Symbol='" + stock + "'";
        String commandEnd = " ORDER BY TradeDate DESC LIMIT " + testingTimeFrame + ") as t ORDER BY t.TradeDate ASC";

        values.put("TestingRecords", dh.executeQuery(commandStart + commandEnd));

        String cutoffDate = values.get("TestingRecords").get(0).split(",")[1];

        commandStart = "SELECT COALESCE(" + dbSchema.get(0) + ",0)";
        for(int i = 1; i < dbSchema.size(); i++)
            commandStart += ",COALESCE(" + dbSchema.get(i)+",0)";
        commandStart += " FROM dailystockprices WHERE Symbol='" + stock + "' AND TradeDate < '" + cutoffDate+"'";
        commandEnd = " ORDER BY TradeDate ASC";
        values.put("TrainingRecords", dh.executeQuery(commandStart + commandEnd));
        values.put("TrainingSet", TrainingFileUtils.convertToClassificationTrainingArray(stock,commandStart,commandEnd,index,new int[]{1,5,20,200},0.2,true,true, true));

        return values;
    }

    static public HashMap<String, ArrayList<String>> getSplit(String stock, double split) throws SQLException {
        HashMap<String, ArrayList<String>> values = new HashMap<>();

        ArrayList<String> dbSchema = dh.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        String commandStart = "SELECT COALESCE(" + dbSchema.get(0) + ",0)";
        for(int i = 1; i < dbSchema.size(); i++)
            commandStart += ",COALESCE(" + dbSchema.get(i)+", 0)";
        commandStart += " FROM(\n" +
                "SELECT dailystockprices.*, @rowno := @rowno + 1 as rowno\n" +
                "FROM (SELECT @rowno := 0)  AS init, dailystockprices WHERE Symbol = '" + stock + "'";

        String commandEnd = " ORDER BY TradeDate DESC\n" +
                ") as t\n" +
                "WHERE rowno > (" + (1-split) + " * @rowno) " +
                "ORDER BY TradeDate ASC;";

        values.put("TrainingRecords", dh.executeQuery(commandStart + commandEnd));
        values.put("TrainingSet", TrainingFileUtils.convertToClassificationTrainingArray(stock,commandStart,commandEnd,0,new int[]{1,5,20,200},0.25,true,true, true));

        commandEnd = " ORDER BY TradeDate DESC\n" +
                ") as t\n" +
                "WHERE rowno <= (" + (1-split) + " * @rowno) " +
                "ORDER BY TradeDate ASC;";

        values.put("TestingRecords", dh.executeQuery(commandStart + commandEnd));

        return values;
    }

    static private void trainSingleStocks(ArrayList<String> stocks) throws IOException, SQLException {
        File trainRecs = new File("res/Simulator/TrainingRecords.csv");
        File testRecs = new File("res/Simulator/TestingRecords.csv");
        PrintWriter pwTrainRec = new PrintWriter(trainRecs);
        PrintWriter pwTestRec = new PrintWriter(testRecs);

        for(String stock : stocks) {
            File dir = new File("res/Simulator/" + stock);
            if(!dir.exists())
                dir.mkdirs();

            File trainFile = new File("res/Simulator/" + stock + "/TrainingFile.csv");

            PrintWriter pwTrain = new PrintWriter(trainFile);

            HashMap<String, ArrayList<String>> values = getSplit(stock, 200,-1);
            for (String record : values.get("TrainingSet"))
                pwTrain.println(record);
            for (String record : values.get("TrainingRecords"))
                pwTrainRec.println(record);
            for (String record : values.get("TestingRecords"))
                pwTestRec.println(record);

            pwTrain.close();


            if(dh.executeQuery("SELECT COUNT(*) FROM predictors WHERE Scope = '" + stock + "';").get(0).equals("0")) {
                TrainingFileUtils.exportLibSVMFile("res/Simulator/" + stock + "/TrainingFile.csv", "res/Simulator/" + stock + "/TrainingFile.libsvm");
                StockPredictor.trainRandomForest("res/Simulator/" + stock + "/TrainingFile.libsvm", stock);
            }


        }
        pwTestRec.close();
        pwTrainRec.close();
    }

    static private void trainMultiStock(ArrayList<String> stocks) throws IOException, SQLException, InterruptedException {
        /*
        File dir = new File("res/Simulator/MultiStock");
            if(!dir.exists())
                dir.mkdirs();

            File trainFile = new File("res/Simulator/MultiStock/TrainingFile.csv");
            File trainRecs = new File("res/Simulator/MultiStock/TrainingRecords.csv");
            File testRecs = new File("res/Simulator/MultiStock/TestingRecords.csv");
            PrintWriter pwTestRec = new PrintWriter(testRecs);
            PrintWriter pwTrain = new PrintWriter(trainFile);
            PrintWriter pwTrainRec = new PrintWriter(trainRecs);

        for(String stock : stocks) {

            HashMap<String, ArrayList<String>> values = getSplit(stock,200,stocks.indexOf(stock));
            for (String record : values.get("TrainingSet"))
                pwTrain.println(record);
            for (String record : values.get("TrainingRecords"))
                pwTrainRec.println(record);
            for (String record : values.get("TestingRecords"))
                pwTestRec.println(record);
        }

            pwTrain.close();
            pwTrainRec.close();
            pwTestRec.close();
*/
        if(dh.executeQuery("SELECT COUNT(*) FROM predictors WHERE Scope = 'MultiStock';").get(0).equals("0")) {
            //TrainingFileUtils.exportLibSVMFile("res/Simulator/MultiStock/TrainingFile.csv", "res/Simulator/MultiStock/TrainingFile.libsvm");
            StockPredictor.trainRandomForest("res/Simulator/MultiStock/TrainingFile.libsvm",stocks.size());
        }
    }

    static public void simulate(ArrayList<String> stocksToSimulate) throws SQLException, IOException, InterruptedException {
        Main.getController().updateCurrentTask("Starting Simulation",false,false);
        //trainMultiStock(stocksToSimulate);
        //trainSingleStocks(stocksToSimulate);

        ArrayList<Thread> threads = new ArrayList<>();
/*

        for(String stock:stocksToSimulate) {
            Thread modelThread = new Thread(()-> {
                try { StockPredictor.loadLatestRandomForest(stock);
                } catch (SQLException e) { e.printStackTrace(); }
            });
            threads.add(modelThread);
            modelThread.start();
        }

        for(Thread thread : threads)
            thread.join();
*/
        TreeMap<String, ArrayList<Double>> prices = new TreeMap<>();
        TreeMap<String, ArrayList<Double>> reducedPrices = new TreeMap<>();
        int portfolioTimeFrame = 20;

        String line;

        TreeSet<Date> testDates = new TreeSet<>();
        HashMap<String, TreeMap<Date, String>> records = new HashMap<>();

        FileReader fr = new FileReader("res/Simulator/TrainingRecords.csv");
        BufferedReader br = new BufferedReader(fr);

        while ((line = br.readLine()) != null) {
            String[] splitString = line.split(",");

            if(prices.get(splitString[0]) == null)
                prices.put(splitString[0],new ArrayList<>());

            prices.get(splitString[0]).add(Double.parseDouble(splitString[5]));
        }

        br.close();
        fr.close();
        fr = new FileReader("res/Simulator/TestingRecords.csv");
        br = new BufferedReader(fr);

        while((line = br.readLine()) != null){
            String[] splitString = line.split(",");
            if(records.get(splitString[0]) == null)
                records.put(splitString[0], new TreeMap<>());

            records.get(splitString[0]).put(Date.valueOf(splitString[1]), line);
            testDates.add(Date.valueOf(splitString[1]));
        }

        for(String stock : prices.keySet())
            reducedPrices.put(stock, new ArrayList<>(prices.get(stock).subList(prices.get(stock).size() - portfolioTimeFrame,prices.get(stock).size())));

        Map<String, Double> portfolio = PortfolioManager.optimisePortfolio(PortfolioManager.OptimisationMethod.GENETIC_ALGORITHM,1,reducedPrices, false);
        Map<String, Integer> heldStocks = new HashMap<>();

        double cutoff = portfolio.get("RETURN");
        portfolio.remove("RETURN");

        TreeMap<Date, Double> balance = new TreeMap<>();
        TreeMap<Date, ArrayList<String>> actions = new TreeMap<>();

        double currentBalance = 1000000;
        double stockWorth = 0;
        double balanceMin = currentBalance * (1 - cutoff);
        double balanceMax = currentBalance * (1 + cutoff);
        int index = 0;
        double bought = 0, sold = 0;
        HashMap<String, Boolean> predictions = new HashMap<>();

        //TODO: Simulate buy/sell with test set from here on
        for(Date date : testDates) {
            System.out.println("SIMULATING '" + date.toString() + "':");
            //Predict and Perform 1-day buy/sell

            boolean equal = true;
            stockWorth = 0;
            bought = 0;
            sold = 0;
            for (String s : stocksToSimulate) {
                String rec = records.get(s).get(date);
                if (rec == null || rec.isEmpty()) {
                    equal = false;
                    break;
                }
            }
            if (equal){
                if (currentBalance + stockWorth <= balanceMin || currentBalance + stockWorth >= balanceMax) {
                    portfolio = PortfolioManager.optimisePortfolio(PortfolioManager.OptimisationMethod.GENETIC_ALGORITHM, 1, reducedPrices, false); //TODO: Ammend reduced prices
                    cutoff = portfolio.get("RETURN");
                    portfolio.remove("RETURN");
                    for (String symbol : heldStocks.keySet()) {
                        double worth = Double.parseDouble(records.get(symbol).get(date).split(",")[5]) * heldStocks.get(symbol);
                        currentBalance += worth;
                        sold += worth;
                        heldStocks.put(symbol, 0);
                    }
                    balanceMin = currentBalance * (1 - cutoff);
                    balanceMax = currentBalance * (1 + cutoff);
                }

                for (String stock : stocksToSimulate) {

                    String rec = records.get(stock).get(date);
                    predictions.put(stock, predictStock(stock, 1, stocksToSimulate.indexOf(stock), date, rec));
                    reducedPrices.get(stock).remove(0);
                    double price = Double.parseDouble(records.get(stock).get(date).split(",")[5]);
                    reducedPrices.get(stock).add(price);

                    if (portfolio.get(stock) != null) {
                        double allocation = portfolio.get(stock) * currentBalance;

                        if (predictions.get(stock) && (price <= allocation)) {
                            int amount = (int) Math.floor(allocation / price);
                            if (currentBalance - (amount * price) >= 0) {
                                currentBalance -= amount * price;
                                bought += amount * price;
                                heldStocks.put(stock, heldStocks.getOrDefault(stock, 0) + amount);
                            }
                        }
                    }
                    if (!predictions.get(stock) && heldStocks.get(stock) != null && heldStocks.get(stock) > 0) {
                        currentBalance += heldStocks.get(stock) * price;
                        sold += heldStocks.get(stock) * price;
                        heldStocks.replace(stock, 0);
                    }


                    if (heldStocks.get(stock) != null && heldStocks.get(stock) > 0)
                        stockWorth += heldStocks.get(stock) * price;

                    Main.getController().addHistoricPrice(stock, index, price);
                }
            }else
            {
                System.err.println("Mismatched Date at " + date);
            }
            System.out.println("\tCurrent Balance: " + currentBalance + ", Stock Worth: "+stockWorth + ", Total: " + (currentBalance+stockWorth) + "(BOUGHT: " + bought + ", SOLD:"+ sold + ")");




            //Rebalance portfolio if it goes past cutoff
            balance.put(date, currentBalance+stockWorth);

            if(equal) {
                Main.getController().realignSimulatorCharts();
                Main.getController().addSimulatorBalance(index++, currentBalance+stockWorth);
            }
        }

        System.out.println("Finished Simulation with: " + currentBalance);
    }

    static private boolean predictStock(String stock, int numberOfDays, int stockIndex, Date date, String record) throws SQLException {

        String[] splitString = record.split(",");
        double newsSentiment = NaturalLanguageProcessor.getAverageSentimentOnDate(stock, date.toString());
        double features[] = new double[splitString.length];
        //double features[] = new double[splitString.length-1];

        features[0] = stockIndex;
        features[1] = numberOfDays;

        for (int i = 2; i < splitString.length - 1; i++)
            features[i] = Double.parseDouble(splitString[i]);

        features[features.length - 1] = newsSentiment;
        return StockPredictor.predictDirection(new DenseVector(features));
/*
        features[0] = numberOfDays;

        for (int i = 2; i < splitString.length - 1; i++)
            features[i-1] = Double.parseDouble(splitString[i]);

        features[features.length - 1] = newsSentiment;
        return StockPredictor.predictDirection(new DenseVector(features), stock);
        */
    }
}