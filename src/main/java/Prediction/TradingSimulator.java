package Prediction;

import APIHandler.StockQuoteDownloader;
import Default.DatabaseHandler;
import Default.Main;
import Portfolio.PortfolioManager;
import Processing.NaturalLanguageProcessor;
import Utility.TrainingFileUtils;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;

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
        values.put("TrainingSet", TrainingFileUtils.convertToClassificationTrainingArray(stock,commandStart,commandEnd,0,new int[]{1,30,200},0.25,true,true, true, false));

        commandEnd = " ORDER BY TradeDate DESC\n" +
                ") as t\n" +
                "WHERE rowno <= (" + (1-split) + " * @rowno) " +
                "ORDER BY TradeDate ASC;";

        values.put("TestingRecords", dh.executeQuery(commandStart + commandEnd));

        return values;
    }

    static private void trainSingleStocks(ArrayList<String> stocks) throws IOException, SQLException {

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

            pwTrain.println("Days,OpenPrice,HighPrice,LowPrice,ClosePrice,TradeVolume,PercentChange,SmoothedClosePrice,SMA5,SMA10,SMA20,SMA200,EMA5,EMA10,EMA20,EMA200,MACD,MACDSig,MACDHist,RSI,ADX10,CCI,AD,OBV,StoOscSlowK,StoOscSlowD,WillR,Sentiment,Prediction");

            HashMap<String, ArrayList<String>> values = getSplit(stock, 200,-1);
            for (String record : values.get("TrainingSet"))
                pwTrain.println(record);
            for (String record : values.get("TrainingRecords"))
                pwTrainRec.println(record);
            for (String record : values.get("TestingRecords"))
                pwTestRec.println(record);

            pwTrain.close();
            pwTestRec.close();
            pwTrainRec.close();

            if(dh.executeQuery("SELECT COUNT(*) FROM predictors WHERE Scope = '" + stock + "_SIMULATION';").get(0).equals("0")) {
                //StockPredictor.trainRandomForest(String.valueOf(StockPredictor.class.getClassLoader().getResource("/Simulator/" + stock + "/TrainingFile.csv")), stock, true);
            }
        }
    }

    static public void generateMultistockTrainingFiles(ArrayList<String> stocks, int timeFrame) throws FileNotFoundException, SQLException {
        File dir = new File(System.getProperty("user.dir") + "/res/Simulator/MultiStock");
        if(!dir.exists())
            dir.mkdirs();

        File trainFile = new File(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingFile.csv");
        File trainRecs = new File(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingRecords.csv");
        File testRecs = new File(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TestingRecords.csv");
        PrintWriter pwTestRec = new PrintWriter(testRecs);
        PrintWriter pwTrain = new PrintWriter(trainFile);
        PrintWriter pwTrainRec = new PrintWriter(trainRecs);

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

        for(String stock : stocks){
            if(!allValues.get(stock).get("TestingRecords").get(0).split(",")[1].equals(date))
                System.err.println(stock + " MISMATCH - Check Stock History (Potentially Due to De-listing or Trade-Day Halting)");
        }
    }

    static public void trainMultiStock(ArrayList<String> stocks) throws Exception {
        StockPredictor.trainRandomForest(System.getProperty("user.dir") + "/res/Simulator/MultiStock/TrainingFile.csv",stocks.size(), true);
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

    static public void simulate(ArrayList<String> stocksToSimulate, boolean singleStock, boolean rebalancePortfolio, int lookback, int holdPeriod) throws Exception {
        Main.getController().updateCurrentTask("Starting Simulation", false, false);
        TreeMap<Date, Double> indexPerformance = new TreeMap<>();

        ArrayList<String> indexRecords = StockQuoteDownloader.downloadIndexHistory("^NDX");
        indexRecords.remove(0);

        for(String indexRecord : indexRecords){
            String[] splitRecord = indexRecord.split(",");
            indexPerformance.put(Date.valueOf(splitRecord[0]),Double.parseDouble(splitRecord[4]));
        }

        if (singleStock) {
            //trainSingleStocks(stocksToSimulate);

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
            StockPredictor.loadLatestSimulationRandomForest();

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
                for(Date date: trainingRecords.get(stock).keySet())
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

        Map<String, Double> portfolio = PortfolioManager.optimisePortfolio(PortfolioManager.OptimisationMethod.GENETIC_ALGORITHM, PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK,30,reducedPrices, false);
        Map<String, Integer> heldStocks = new HashMap<>();
        Map<String, Integer> randomHeldStocks = new HashMap<>();

        double cutoff = portfolio.get("RETURN");
        portfolio.remove("RETURN");

        TreeMap<Date, Double> balance = new TreeMap<>();
        TreeMap<Date, ArrayList<String>> actions = new TreeMap<>();


        Random rng = new Random();
        double currentBalance = -1;
        double initialBalance = -1;
        double randomBalance = -1;
        double stockWorth = 0;
        double randomStockWorth = 0;
        double balanceMin = -1;
        double balanceMax = -1;
        double randomBalanceMin = -1;
        double randomBalanceMax = -1;

        int index = 0;
        double bought = 0, sold = 0;
        double randomBought = 0, randomSold = 0;
        HashMap<String, Boolean> predictions = new HashMap<>();

            for(Date date : testDates) {
            System.out.println("SIMULATING '" + date.toString() + "':");
            //Predict and Perform 1-day buy/sell

            boolean equal = true;
            stockWorth = 0;
            bought = 0;
            sold = 0;

            randomStockWorth = 0;
            randomBought = 0;
            randomSold = 0;

            for (String s : stocksToSimulate) {
                String rec = testingRecords.get(s).get(date);
                if (rec == null || rec.isEmpty()) {
                    equal = false;
                    break;
                }
            }
            if (equal){
                if(initialBalance == -1) {
                    initialBalance = indexPerformance.get(date);
                    currentBalance = initialBalance;
                    randomBalance = initialBalance;
                    balanceMin = currentBalance * (1 - cutoff);
                    balanceMax = currentBalance * (1 + cutoff);
                    randomBalanceMin = randomBalance * (1-cutoff);
                    randomBalanceMax = randomBalance * (1+cutoff);

                }

                if(rebalancePortfolio)
                if (currentBalance + stockWorth <= balanceMin || currentBalance + stockWorth >= balanceMax) {
                    portfolio = PortfolioManager.optimisePortfolio(PortfolioManager.OptimisationMethod.GENETIC_ALGORITHM, PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK, holdPeriod, reducedPrices, false);
                    cutoff = portfolio.get("RETURN");
                    portfolio.remove("RETURN");
                    for (String symbol : heldStocks.keySet()) {
                        double worth = Double.parseDouble(testingRecords.get(symbol).get(date).split(",")[5]) * heldStocks.get(symbol);
                        currentBalance += worth;
                        sold += worth;
                        heldStocks.put(symbol, 0);
                    }

                    balanceMin = currentBalance * (1 - cutoff);
                    balanceMax = currentBalance * (1 + cutoff);
                    randomBalanceMin = randomBalance * (1 - cutoff);
                    randomBalanceMax = randomBalance * (1 + cutoff);
                }

                for (String stock : stocksToSimulate) {

                    String rec = testingRecords.get(stock).get(date);
                    predictions.put(stock, predictStock(stock, 1, stocksToSimulate.indexOf(stock), date, rec,singleStock));
                    reducedPrices.get(stock).remove(reducedPrices.get(stock).firstKey());
                    double price = Double.parseDouble(testingRecords.get(stock).get(date).split(",")[5]);

                    reducedPrices.get(stock).put(date, price);

                    if (portfolio.get(stock) != null) {
                        double allocation = portfolio.get(stock) * currentBalance;
                        double randomAllocation = portfolio.get(stock) * randomBalance;

                        if (predictions.get(stock) && (price <= allocation)) {
                            int amount = (int) Math.floor(allocation / price);
                            if (currentBalance - (amount * price) >= 0) {
                                currentBalance -= amount * price;
                                bought += amount * price;
                                heldStocks.put(stock, heldStocks.getOrDefault(stock, 0) + amount);
                            }
                        }

                        if (rng.nextBoolean() && (price <= randomAllocation)) {
                            int amount = (int) Math.floor(randomAllocation / price);
                            if (randomBalance - (amount * price) >= 0) {
                                randomBalance -= amount * price;
                                randomBought += amount * price;
                                randomHeldStocks.put(stock, randomHeldStocks.getOrDefault(stock, 0) + amount);
                            }
                        }
                    }

                    if (!predictions.get(stock) && heldStocks.get(stock) != null && heldStocks.get(stock) > 0) {
                        currentBalance += heldStocks.get(stock) * price;
                        sold += heldStocks.get(stock) * price;
                        heldStocks.replace(stock, 0);
                    }

                    if (rng.nextBoolean() && randomHeldStocks.get(stock) != null && randomHeldStocks.get(stock) > 0) {
                        randomBalance += randomHeldStocks.get(stock) * price;
                        randomSold += randomHeldStocks.get(stock) * price;
                        randomHeldStocks.replace(stock, 0);
                    }

                    if (heldStocks.get(stock) != null && heldStocks.get(stock) > 0)
                        stockWorth += heldStocks.get(stock) * price;
                    if (randomHeldStocks.get(stock) != null && randomHeldStocks.get(stock) > 0)
                        randomStockWorth += randomHeldStocks.get(stock) * price;

                    Main.getController().addHistoricPrice(stock, index, price);
                }
            }else
                System.err.println("Mismatched Date at " + date);

            System.out.println("\tCurrent Balance: " + currentBalance + ", Stock Worth: "+stockWorth + ", Total: " + (currentBalance+stockWorth) + "(BOUGHT: " + bought + ", SOLD:"+ sold + ")");
            System.out.println("\tRandom Balance: " + randomBalance + ", Random Stock Worth: "+randomStockWorth + ", Random Total: " + (randomBalance+randomStockWorth) + "(BOUGHT: " + randomBought + ", SOLD:"+ randomSold + ")");

            //Rebalance portfolio if it goes past cutoff
            balance.put(date, currentBalance);

            if(equal) {
                Main.getController().addSimulatorBalance(index, currentBalance + stockWorth);
                Main.getController().addSimulatorRandomPerformance(index, randomBalance + randomStockWorth);
                Main.getController().addSimulatorIndexPerformance(index, indexPerformance.get(date));
                Main.getController().realignSimulatorCharts();
                index++;
            }
        }

        currentBalance += stockWorth;

        System.out.println("Finished Simulation with: " + currentBalance + " -> " + ((currentBalance - initialBalance )/ initialBalance * 100) + "% return");
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
        }else {
            features = new double[splitString.length+1];

            features[0] = stockIndex;
            features[1] = numberOfDays;

            for (int i = 2; i < splitString.length; i++)
                features[i] = Double.parseDouble(splitString[i]);

            features[features.length - 1] = newsSentiment;
        }

        String[] values = new String[]{"Index","Days","OpenPrice","HighPrice","LowPrice","ClosePrice","TradeVolume","PercentChange","SmoothedClosePrice","SMA5","SMA10","SMA20","SMA200","EMA5","EMA10","EMA20","EMA200","MACD","MACDSig","MACDHist","RSI","ADX10","CCI","AD","OBV","StoOscSlowK","StoOscSlowD","WillR","Sentiment"};

        ArrayList<Attribute> attributes = new ArrayList<>();

        for(String value : values)
            attributes.add(new Attribute(value));

        ArrayList<String> predictions = new ArrayList<>();
        predictions.add ("0");
        predictions.add("1");

        Attribute classes = new Attribute("Predictions", predictions);

        attributes.add(classes);

        double[] unlabelledData = new double[features.length+1];

        for(int i = 0; i < features.length; i++)
            unlabelledData[i] = features[i];

        unlabelledData[unlabelledData.length-1] = -1;

        Instance dataPoint = new DenseInstance(unlabelledData.length, unlabelledData);
        Instances dataSet = new Instances("ToPredict",attributes,0);
        dataSet.add(dataPoint);
        dataPoint.setMissing(dataSet.numAttributes()-1);

        NumericToNominal ntn = new NumericToNominal();
        String[] opt = new String[2];
        opt[0] = "-R";
        opt[1] = "last";
        ntn.setOptions(opt);
        ntn.setInputFormat(dataSet);
        Instances fixedDataSet = Filter.useFilter(dataSet, ntn);
        dataSet.delete();
        fixedDataSet.setClassIndex(fixedDataSet.numAttributes() - 1);

        return StockPredictor.predictDirection(fixedDataSet.instance(0));
    }
}