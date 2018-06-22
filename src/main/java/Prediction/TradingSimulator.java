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

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.5
 */

public class TradingSimulator {
    static private DatabaseHandler dh;

    /**
     * Initialises the Trading Simulator with a Database Handler to prevent deadlocks when accessing the database alongside other classes
     *
     * @param tsdh Trading Simulator Database Handler
     */
    static public void initialise(DatabaseHandler tsdh) {
        dh = tsdh;
    }

    /**
     * Splits the data from the database into training and testing sets
     *
     * @param stock            Stock to create data sets for
     * @param testingTimeFrame Time Frame to simulate trading for (test set period)
     * @param index            Index of stock (for use with Multi-Stock models)
     * @return Collection of all data collected from the database and formatted for use with models
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static private HashMap<String, ArrayList<String>> getSplit(String stock, int testingTimeFrame, int index) throws SQLException {
        HashMap<String, ArrayList<String>> values = new HashMap<>();

        ArrayList<String> dbSchema = dh.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        StringBuilder commandStart = new StringBuilder("SELECT COALESCE(" + dbSchema.get(0) + ",0)");
        for(int i = 1; i < dbSchema.size(); i++)
            commandStart.append(",COALESCE(").append(dbSchema.get(i)).append(", 0)");
        commandStart.append(" FROM (SELECT * FROM dailystockprices WHERE Symbol='").append(stock).append("'");
        String commandEnd = " ORDER BY TradeDate DESC LIMIT " + testingTimeFrame + ") as t ORDER BY t.TradeDate ASC";

        values.put("TestingRecords", dh.executeQuery(commandStart + commandEnd));

        String cutoffDate = values.get("TestingRecords").get(0).split(",")[1];

        commandStart = new StringBuilder("SELECT COALESCE(" + dbSchema.get(0) + ",0)");
        for(int i = 1; i < dbSchema.size(); i++)
            commandStart.append(",COALESCE(").append(dbSchema.get(i)).append(",0)");
        commandStart.append(" FROM dailystockprices WHERE Symbol='").append(stock).append("' AND TradeDate < '").append(cutoffDate).append("'");
        commandEnd = " ORDER BY TradeDate ASC";
        values.put("TrainingRecords", dh.executeQuery(commandStart + commandEnd));
        values.put("TrainingSet", TrainingFileUtils.convertToClassificationTrainingArray(stock, commandStart.toString(), commandEnd, index, new int[]{1, 30, 200}, 0.1, true, true, false, false));

        return values;
    }

    /**
     * Trains Single Stock models, given a set of training file locations
     *
     * @param stocks List of stocks to train prediction models for
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void trainSingleStocks(ArrayList<String> stocks) throws SQLException {
        for (String stock : stocks)
            StockPredictor.trainRandomForest(System.getProperty("user.dir") + "/res/Simulator/" + stock + "/TrainingFile.libsvm", stock, true);
    }

    /**
     * Trains a Multi Stock model, given a training file location
     *
     * @param stocks List of stocks to train prediction model on
     * @throws SQLException         Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     * @throws InterruptedException Throws InterruptedException if the sleep function is interrupted by another process
     */
    static public void trainMultiStock(ArrayList<String> stocks) throws SQLException, InterruptedException {
        StockPredictor.trainRandomForest(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingFile.libsvm", stocks.size(), true);
    }

    /**
     * Creates individual stock-based training files required for training Single Stock models
     *
     * @param stocks        List of stocks to create training files for
     * @param timeFrame     Time frame for which testing should be done (e.g. 200 days)
     * @param includeHeader True if the title of each column should be included, False if otherwise
     * @throws IOException  Throws IOException if the request fails due to server unavailability or connection refusal
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void generateSingleStockTrainingFiles(ArrayList<String> stocks, int timeFrame, boolean includeHeader) throws IOException, SQLException {
        for(String stock : stocks) {
            File dir = new File(System.getProperty("user.dir") + "/res/Simulator/" + stock);
            if(!dir.exists())
                if (!dir.mkdirs())
                    Main.getController().updateCurrentTask("Could not create file/directory: " + dir, true, true);

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

    /**
     * Creates a large multi-stock training file required for training a Multi-Stock model
     *
     * @param stocks        Stocks to include in the training file
     * @param timeFrame     Time frame for which testing should be done (e.g. 200 days)
     * @param includeHeader True if the title of each column should be included, False if otherwise
     * @throws IOException  Throws IOException if the request fails due to server unavailability or connection refusal
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void generateMultistockTrainingFiles(ArrayList<String> stocks, int timeFrame, boolean includeHeader) throws IOException, SQLException {
        File dir = new File(System.getProperty("user.dir") + "/res/Simulator/MultiStock");
        if(!dir.exists())
            if (!dir.mkdirs())
                Main.getController().updateCurrentTask("Could not create file/directory: " + dir, true, true);

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

    /**
     * Converts a file to a price map that is accessible through specifying a stock and then a date
     *
     * @param filePath File to load in
     * @return A Map of prices sorted by stock and trade date
     * @throws IOException Throws IOException if the request fails due to server unavailability or connection refusal
     */
    static private TreeMap<String, TreeMap<Date, Double>> fileToPriceTreeMap(String filePath) throws IOException {
        String line;
        TreeMap<String, TreeMap<Date, Double>> prices = new TreeMap<>();

        FileReader fr = new FileReader(filePath);
        BufferedReader br = new BufferedReader(fr);

        while ((line = br.readLine()) != null) {
            String[] splitString = line.split(",");
            prices.computeIfAbsent(splitString[0], k -> new TreeMap<>());
            prices.get(splitString[0]).put(Date.valueOf(splitString[1]), Double.parseDouble(splitString[5]));
        }

        br.close();
        fr.close();

        return prices;
    }

    /**
     * Converts a file to a record array, accessible through specifying a stock and a date
     *
     * @param filePath File to load in
     * @return A Map of records sorted by trade date
     * @throws IOException Throws IOException if the request fails due to server unavailability or connection refusal
     */
    static private HashMap<String, TreeMap<Date, String>> fileToRecordArray(String filePath) throws IOException {
        String line;

        FileReader fr = new FileReader(filePath);
        BufferedReader br = new BufferedReader(fr);
        HashMap<String, TreeMap<Date, String>> records = new HashMap<>();

        while ((line = br.readLine()) != null) {
            String[] splitString = line.split(",");
            records.computeIfAbsent(splitString[0], k -> new TreeMap<>());
            records.get(splitString[0]).put(Date.valueOf(splitString[1]), line);
        }

        return records;
    }

    /**
     * Simulates trading amongst a given set of stocks for a given time period
     *
     * @param stocksToSimulate           Stocks that are considered for buying and selling during simulation
     * @param singleStock                True if the simulator should use Single-Stock models, False if it should use Multi-Stock
     * @param portfolioCalibrationPeriod Number of days of historic data that the portfolio should consider
     * @param holdPeriod                 Number of days that a stock is expected to be held
     * @param dayArray                   Set of days that are to be used during long-term investments
     * @throws Exception Including SQLException, IOException and InterruptedException
     */
    static public void simulate(ArrayList<String> stocksToSimulate, boolean singleStock, int portfolioCalibrationPeriod, int holdPeriod, Integer[] dayArray) throws Exception {
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
                testDates.addAll(testingRecords.get(stock).keySet());
                trainDates.addAll(trainingRecords.get(stock).keySet());
            }
        }else{
            prices.putAll(fileToPriceTreeMap(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingRecords.csv"));
            trainingRecords.putAll(fileToRecordArray(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingRecords.csv"));
            testingRecords.putAll(fileToRecordArray(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TestingRecords.csv"));
            for (String stock : stocksToSimulate) {
                testDates.addAll(testingRecords.get(stock).keySet());
                trainDates.addAll(trainingRecords.get(stock).keySet());
            }
        }

        testDates = cleanDates(testDates, stocksToSimulate, testingRecords);
        trainDates = cleanDates(trainDates, stocksToSimulate, trainingRecords);

        ArrayList<Date> dates = new ArrayList<>(trainDates);

        for(String stock : prices.keySet()) {
            Collections.reverse(dates);

            reducedPrices.put(stock, new TreeMap<>());

            for (int i = 0; i < portfolioCalibrationPeriod; i++)
                reducedPrices.get(stock).put(dates.get(i), prices.get(stock).get(dates.get(i)));
        }

        SimulationModel automatedTrader = new SimulationModel("Automated Trader (Initial Portfolio Optimisation only)", stocksToSimulate, new HashSet<>(Arrays.asList(dayArray)));
        SimulationModel automatedTraderWithRebalancing = new SimulationModel("Automated Trader (with Portfolio Rebalancing)", stocksToSimulate, new HashSet<>(Arrays.asList(dayArray)));
        SimulationModel automatedTraderEqualAllocation = new SimulationModel("Automated Trader (with Equal Allocation)", stocksToSimulate, new HashSet<>(Arrays.asList(dayArray)));
        SimulationModel randomTrader = new SimulationModel("Random Trader", stocksToSimulate, new HashSet<>(Arrays.asList(dayArray)));

        Map<String, Double> emptyPortfolio = new TreeMap<>();
        for (String stock : stocksToSimulate) emptyPortfolio.put(stock, 0.0);

        Map<String, Double> portfolio = PortfolioManager.optimisePortfolio(PortfolioManager.OptimisationMethod.GENETIC_ALGORITHM, PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK, holdPeriod, reducedPrices, emptyPortfolio, false);
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

                automatedTraderWithRebalancing.rebalancePortfolio(testingRecords, date, holdPeriod, reducedPrices, false);

                Main.getController().updateSimulatedComponentChart("REBALANCED_ALLOC", automatedTraderWithRebalancing.getPortfolio());

                Main.getController().updateSimulationPredictions(predictions);

                for (String stock : stocksToSimulate) {
                    reducedPrices.get(stock).remove(Collections.min(reducedPrices.get(stock).keySet()));
                    double price = Double.parseDouble(testingRecords.get(stock).get(date).split(",")[5]);

                    reducedPrices.get(stock).put(date, price);
                    if (rng.nextBoolean())
                        randomTrader.buyStock(stock, price, 1);
                    else
                        randomTrader.sellAllStock(stock, price);

                    automatedTrader.tradeStock(stock, price, predictions.get(stock));
                    automatedTraderEqualAllocation.tradeStock(stock, price, predictions.get(stock));
                    automatedTraderWithRebalancing.tradeStock(stock, price, predictions.get(stock));

                    for (SimulationModel model : simulations) model.updateStockWorth(stock, price);
                    Main.getController().addHistoricPrice(stock, index, price);
                }
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

    /**
     * Matches up the latest dates so that all simulations can be performed accurately on the same days
     *
     * @param dates   Original (dirty) dates
     * @param stocks  Stocks to extract record dates from
     * @param records Records to search for dates
     * @return A set of dates that are present in all stock records
     */
    static private TreeSet<Date> cleanDates(TreeSet<Date> dates, ArrayList<String> stocks, HashMap<String, TreeMap<Date, String>> records) {
        TreeSet<Date> cleanDates = new TreeSet<>(dates);

        for (Date date : dates)
            for (String s : stocks) {
                String rec = records.get(s).get(date);
                if (rec == null || rec.isEmpty())
                    cleanDates.remove(date);
            }
        return cleanDates;
    }

    /**
     * Utilises simulation prediction models to predict the price direction at n days
     *
     * @param stock        Stock to predict the price direction of
     * @param numberOfDays Number of days in advance to predict the price direction
     * @param stockIndex   Index of the stock from the total list of stocks (for use with Multi-Stock models)
     * @param date         Date to calculate the News Sentiment of
     * @param record       Record associated with this date (for price and indicator extraction)
     * @param singleStock  True if the simulator should use a Single-Stock model or the Multi-Stock model
     * @return True if the stock price is expected to rise or stay the same, False if it is expected to fall
     * @throws Exception Including IOException, SQLException and InterruptedException
     */
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