package Prediction;

import APIHandler.StockQuoteDownloader;
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

    static public void initialise(DatabaseHandler tsdh){dh = tsdh;}

    static private HashMap<String, ArrayList<String>> getSplit(String stock, int testingTimeFrame, int index) throws SQLException {
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
        values.put("TrainingSet", TrainingFileUtils.convertToClassificationTrainingArray(stock,commandStart,commandEnd,index,new int[]{1,30,200},0.1,true,true, true, false));

        return values;
    }

    static public void trainSingleStocks(ArrayList<String> stocks) throws SQLException {
        for (String stock : stocks)
            StockPredictor.trainRandomForest(System.getProperty("user.dir") + "/res/Simulator/" + stock + "/TrainingFile.libsvm", stock, true);
    }

    static public void trainMultiStock(ArrayList<String> stocks) throws Exception {
        StockPredictor.trainRandomForest(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingFile.libsvm", stocks.size(), true);
    }

    static public void generateSingleStockTrainingFiles(ArrayList<String> stocks, int timeFrame, boolean includeHeader) throws IOException, SQLException {
        for(String stock : stocks) {
            File dir = new File(System.getProperty("user.dir") + "/res/Simulator/" + stock);
            if(!dir.exists())
                dir.mkdirs();

            File trainRecs = new File(System.getProperty("user.dir") + "/res/Simulator/" + stock + "/TrainingRecords.csv");
            File testRecs = new File(System.getProperty("user.dir") + "/res/Simulator/" + stock + "/TestingRecords.csv");
            File trainFile = new File(System.getProperty("user.dir") + "/res/Simulator/" + stock + "/TrainingFile.csv");

            PrintWriter pwTrainRec = new PrintWriter(trainRecs);
            PrintWriter pwTestRec = new PrintWriter(testRecs);
            PrintWriter pwTrain = new PrintWriter(trainFile);

            if (includeHeader)
                pwTrain.println("Days,OpenPrice,HighPrice,LowPrice,ClosePrice,TradeVolume,PercentChange,SmoothedClosePrice,SMA5,SMA10,SMA20,SMA200,EMA5,EMA10,EMA20,EMA200,MACD,MACDSig,MACDHist,RSI,ADX10,CCI,AD,OBV,StoOscSlowK,StoOscSlowD,WillR,Sentiment,Prediction");

            HashMap<String, ArrayList<String>> values = getSplit(stock, timeFrame, -1);
            for (String record : values.get("TrainingSet"))
                pwTrain.println(record);
            for (String record : values.get("TrainingRecords"))
                pwTrainRec.println(record);
            for (String record : values.get("TestingRecords"))
                pwTestRec.println(record);

            pwTrain.close();
            pwTestRec.close();
            pwTrainRec.close();

            TrainingFileUtils.exportLibSVMFile(System.getProperty("user.dir") + "/res/Simulator/" + stock + "/TrainingFile.csv", System.getProperty("user.dir") + "/res/Simulator/" + stock + "/TrainingFile.libsvm");
        }
    }

    static public void generateMultistockTrainingFiles(ArrayList<String> stocks, int timeFrame, boolean includeHeader) throws IOException, SQLException {
        File dir = new File(System.getProperty("user.dir") + "/res/Simulator/MultiStock");
        if(!dir.exists())
            dir.mkdirs();

        File trainFile = new File(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingFile.csv");
        File trainRecs = new File(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingRecords.csv");
        File testRecs = new File(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TestingRecords.csv");
        PrintWriter pwTestRec = new PrintWriter(testRecs);
        PrintWriter pwTrain = new PrintWriter(trainFile);
        PrintWriter pwTrainRec = new PrintWriter(trainRecs);

        if (includeHeader)
            pwTrain.println("Index,Days,OpenPrice,HighPrice,LowPrice,ClosePrice,TradeVolume,PercentChange,SmoothedClosePrice,SMA5,SMA10,SMA20,SMA200,EMA5,EMA10,EMA20,EMA200,MACD,MACDSig,MACDHist,RSI,ADX10,CCI,AD,OBV,StoOscSlowK,StoOscSlowD,WillR,Sentiment,Prediction");
        HashMap<String, HashMap<String, ArrayList<String>>> allValues = new HashMap<>();

        for(String stock : stocks) {
            HashMap<String, ArrayList<String>> values = getSplit(stock,timeFrame,stocks.indexOf(stock));
            allValues.put(stock,values);

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

        String date = allValues.get(stocks.get(0)).get("TestingRecords").get(0).split(",")[1];

        TrainingFileUtils.exportLibSVMFile(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingFile.csv", System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingFile.libsvm");

        for(String stock : stocks){
            if(!allValues.get(stock).get("TestingRecords").get(0).split(",")[1].equals(date))
                System.err.println(stock + " MISMATCH - Check Stock History (Potentially Due to De-listing or Trade-Day Halting)");
        }
    }

    static private TreeMap<String, TreeMap<Date, Double>> fileToPriceTreeMap(String filePath) throws IOException {
        String line;
        TreeMap<String, TreeMap<Date, Double>> prices = new TreeMap<>();

        FileReader fr = new FileReader(filePath);
        BufferedReader br = new BufferedReader(fr);

        while ((line = br.readLine()) != null) {
            String[] splitString = line.split(",");

            if (prices.get(splitString[0]) == null)
                prices.put(splitString[0], new TreeMap<>());

            prices.get(splitString[0]).put(Date.valueOf(splitString[1]), Double.parseDouble(splitString[5]));
        }

        br.close();
        fr.close();

        return prices;
    }

    static private HashMap fileToRecordArray(String filePath) throws IOException {
        String line;

        FileReader fr = new FileReader(filePath);
        BufferedReader br = new BufferedReader(fr);
        HashMap<String, TreeMap<Date, String>> records = new HashMap<>();

        while ((line = br.readLine()) != null) {
            String[] splitString = line.split(",");
            if (records.get(splitString[0]) == null)
                records.put(splitString[0], new TreeMap<>());

            records.get(splitString[0]).put(Date.valueOf(splitString[1]), line);
        }

        return records;
    }

    static public void simulate(ArrayList<String> stocksToSimulate, boolean singleStock, int lookback, int holdPeriod, int[] dayArray) throws Exception {
        Main.getController().updateCurrentTask("Starting Simulation", false, false);
        TreeMap<Date, Double> indexPerformance = new TreeMap<>();

        Main.getController().clearSimulation();

        ArrayList<String> indexRecords = StockQuoteDownloader.downloadIndexHistory("^NDX");
        indexRecords.remove(0);

        for(String indexRecord : indexRecords){
            String[] splitRecord = indexRecord.split(",");
            indexPerformance.put(Date.valueOf(splitRecord[0]),Double.parseDouble(splitRecord[4]));
        }

        if (singleStock) {
            ArrayList<Thread> threads = new ArrayList<>();

            for (String stock : stocksToSimulate) {
                Thread modelThread = new Thread(() -> {
                    try {
                        StockPredictor.loadLatestRandomForest(stock + "_SIMULATION");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                threads.add(modelThread);
                modelThread.start();
            }

            for (Thread thread : threads)
                thread.join();

        } else
            StockPredictor.loadLatestRandomForest("MultiStock_SIMULATION");

        TreeMap<String, TreeMap<Date, Double>> prices = new TreeMap<>();
        TreeMap<String, TreeMap<Date, Double>> reducedPrices = new TreeMap<>();

        TreeSet<Date> testDates = new TreeSet<>();
        TreeSet<Date> trainDates = new TreeSet<>();
        HashMap<String, TreeMap<Date, String>> testingRecords = new HashMap<>();
        HashMap<String, TreeMap<Date, String>> trainingRecords = new HashMap<>();

        if (singleStock){
            for (String stock : stocksToSimulate) {
                prices.putAll(fileToPriceTreeMap(System.getProperty("user.dir") + "/res/Simulator/" + stock + "/TrainingRecords.csv"));
                trainingRecords.putAll(fileToRecordArray(System.getProperty("user.dir") + "/res/Simulator/" + stock + "/TrainingRecords.csv"));
                testingRecords.putAll(fileToRecordArray(System.getProperty("user.dir") + "/res/Simulator/" + stock + "/TestingRecords.csv"));
                for (Date date : testingRecords.get(stock).keySet())
                    testDates.add(date);
                for (Date date : trainingRecords.get(stock).keySet())
                    trainDates.add(date);
            }
        }else{
            prices.putAll(fileToPriceTreeMap(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingRecords.csv"));
            trainingRecords.putAll(fileToRecordArray(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingRecords.csv"));
            testingRecords.putAll(fileToRecordArray(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TestingRecords.csv"));
            for(String stock : stocksToSimulate) {
                for (Date date : testingRecords.get(stock).keySet())
                    testDates.add(date);
                for (Date date : trainingRecords.get(stock).keySet())
                    trainDates.add(date);
            }
        }

        TreeSet<Date> dirtyDates = new TreeSet<>(testDates);

        for(Date date : dirtyDates)
            for (String s : stocksToSimulate) {
                String rec = testingRecords.get(s).get(date);
                if (rec == null || rec.isEmpty())
                    testDates.remove(date);
            }

        dirtyDates = new TreeSet<>(trainDates);

        for(Date date : dirtyDates)
            for (String s : stocksToSimulate) {
                String rec = trainingRecords.get(s).get(date);
                if (rec == null || rec.isEmpty())
                    trainDates.remove(date);
            }

        int portfolioTimeFrame = lookback;

        ArrayList<Date> dates = new ArrayList<>(trainDates);

        for(String stock : prices.keySet()) {
            Collections.reverse(dates);

            reducedPrices.put(stock, new TreeMap<>());

            for(int i = 0; i < portfolioTimeFrame; i++)
                reducedPrices.get(stock).put(dates.get(i), prices.get(stock).get(dates.get(i)));
        }

        SimulationModel automatedTrader = new SimulationModel("Automated Trader (Initial Portfolio Optimisation only)");
        SimulationModel automatedTraderWithRebalancing = new SimulationModel("Automated Trader (with Portfolio Rebalancing)");
        SimulationModel automatedTraderEqualAllocation = new SimulationModel("Automated Trader (with Equal Allocation)");
        SimulationModel randomTrader = new SimulationModel("Random Trader");

        Map<String, Double> portfolio = PortfolioManager.optimisePortfolio(PortfolioManager.OptimisationMethod.SIMULATED_ANNEALING, PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK, holdPeriod, reducedPrices, false);
        Main.getController().updateSimulatedComponentChart("REBALANCED_ALLOC", portfolio);
        Main.getController().updateSimulatedComponentChart("INITIAL_ALLOC", portfolio);
        Map<String, Double> equalPortfolio = new HashMap<>();

        for (String stock : stocksToSimulate)
            equalPortfolio.put(stock, 1.0 / stocksToSimulate.size());

        automatedTrader.setPortfolio(new HashMap<>(portfolio));
        automatedTraderWithRebalancing.setPortfolio(new HashMap<>(portfolio));
        automatedTraderEqualAllocation.setPortfolio(equalPortfolio);
        randomTrader.setPortfolio(new HashMap<>(portfolio));

        ArrayList<SimulationModel> simulations = new ArrayList<>();
        simulations.add(automatedTrader);
        simulations.add(automatedTraderEqualAllocation);
        simulations.add(automatedTraderWithRebalancing);
        simulations.add(randomTrader);

        double cutoff = portfolio.get("RETURN");
        portfolio.remove("RETURN");

        Random rng = new Random();
        TreeMap<Date, Double> balance = new TreeMap<>();
        TreeMap<Date, ArrayList<String>> actions = new TreeMap<>();
        ArrayList<String> investmentRecords = new ArrayList<>();

        int index = 0;
        double initialBalance = -1;
        HashMap<String, HashMap<Integer, Boolean>> predictions = new HashMap<>();

        for (String stock : stocksToSimulate)
            predictions.put(stock, new HashMap<>());

        for (Date date : testDates) {
            System.out.println("\r\nSIMULATING '" + date.toString() + "':");

            boolean equal = true;
            for (SimulationModel model : simulations) model.initialiseIteration();

            for (String s : stocksToSimulate) {
                String rec = testingRecords.get(s).get(date);
                if (rec == null || rec.isEmpty()) {
                    equal = false;
                    break;
                }
            }

            if (equal){
                for (String stock : stocksToSimulate) {
                    String rec = testingRecords.get(stock).get(date);
                    for (int day : dayArray)
                        predictions.get(stock).put(day, predictStock(stock, day, stocksToSimulate.indexOf(stock), date, rec, singleStock));
                }

                if(initialBalance == -1) {
                    initialBalance = indexPerformance.get(date);
                    for (SimulationModel model : simulations) model.setBalance(initialBalance);
                    automatedTrader.updateCutoff(cutoff);
                    randomTrader.updateCutoff(cutoff);
                    Main.getController().initialiseSimulatorPredictions(predictions);
                }

                //automatedTraderWithRebalancing.rebalancePortfolio(testingRecords,date,holdPeriod,reducedPrices);
                // randomTrader.rebalancePortfolio(testingRecords,date,holdPeriod,reducedPrices);

                Main.getController().updateSimulatedComponentChart("REBALANCED_ALLOC", automatedTraderWithRebalancing.getPortfolio());

                Main.getController().updateSimulationPredictions(predictions);

                for (String stock : stocksToSimulate) {
                    reducedPrices.get(stock).remove(Collections.min(reducedPrices.get(stock).keySet()));
                    double price = Double.parseDouble(testingRecords.get(stock).get(date).split(",")[5]);

                    reducedPrices.get(stock).put(date, price);
                    //    for(int day : dayArray) {
                    if (rng.nextBoolean())
                        randomTrader.buyStock(stock, price);
                    else
                        randomTrader.sellStock(stock, price);

                    if (predictions.get(stock).get(1)) {
                        automatedTrader.buyStock(stock, price);
                        automatedTraderEqualAllocation.buyStock(stock, price);
                        automatedTraderWithRebalancing.buyStock(stock, price);
                    } else {
                        automatedTrader.sellStock(stock, price);
                        automatedTraderEqualAllocation.sellStock(stock, price);
                        automatedTraderWithRebalancing.sellStock(stock, price);
                    }

                    // }
                    for (SimulationModel model : simulations) model.updateStockWorth(stock, price);
                    Main.getController().addHistoricPrice(stock, index, price);
                }
                automatedTrader.updateInvestments();
                automatedTraderEqualAllocation.updateInvestments();
                automatedTraderWithRebalancing.updateInvestments();
            }else
                System.err.println("Mismatched Date at " + date);

            for (SimulationModel model : simulations) model.printStatus();

            //Rebalance portfolio if it goes past cutoff

            if(equal) {
                Main.getController().addSimulatorIndexPerformance(index, indexPerformance.get(date));

                Main.getController().addSimulatorPortfolioInitialBalance(index, automatedTrader.getTotal());
                Main.getController().addSimulatorBalance(index, automatedTraderWithRebalancing.getTotal());
                Main.getController().addSimulatorEqualAllocationBalance(index, automatedTraderEqualAllocation.getTotal());
                Main.getController().addSimulatorRandomPerformance(index, randomTrader.getTotal());
                Main.getController().realignSimulatorCharts();
                index++;
            }
        }

        System.out.println("\r\n");
        for (SimulationModel model : simulations) model.finalise(initialBalance);
    }

    static private boolean predictStock(String stock, int numberOfDays, int stockIndex, Date date, String record, boolean singleStock) throws Exception {
        String[] splitString = record.split(",");
        double newsSentiment = NaturalLanguageProcessor.getAverageSentimentOnDate(stock, date.toString());

        double features[];

        if(singleStock) {
            features = new double[splitString.length];

            features[0] = numberOfDays;

            for (int i = 2; i < splitString.length; i++)
                features[i-1] = Double.parseDouble(splitString[i]);


            features[features.length - 1] = newsSentiment;
            return StockPredictor.predictDirection(new DenseVector(features), stock);
        }else {
            features = new double[splitString.length+1];

            features[0] = stockIndex;
            features[1] = numberOfDays;

            for (int i = 2; i < splitString.length; i++)
                features[i] = Double.parseDouble(splitString[i]);

            features[features.length - 1] = newsSentiment;

            return StockPredictor.predictDirection(new DenseVector(features));
        }
    }
}