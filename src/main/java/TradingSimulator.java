import org.apache.spark.mllib.linalg.DenseVector;

import java.io.*;
import java.sql.Date;
import java.sql.SQLException;
import java.util.*;


class TradingSimulator {
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
        for(String stock : stocks) {
            File dir = new File("res/Simulator/" + stock);
            if(!dir.exists())
                dir.mkdirs();

            File trainFile = new File("res/Simulator/" + stock + "/TrainingFile.csv");
            File trainRecs = new File("res/Simulator/" + stock + "/TrainingRecords.csv");
            File testRecs = new File("res/Simulator/" + stock + "/TestingRecords.csv");
            PrintWriter pwTrain = new PrintWriter(trainFile);
            PrintWriter pwTrainRec = new PrintWriter(trainRecs);
            PrintWriter pwTestRec = new PrintWriter(testRecs);

            HashMap<String, ArrayList<String>> values = getSplit(stock, 200,-1);
            for (String record : values.get("TrainingSet"))
                pwTrain.println(record);
            for (String record : values.get("TrainingRecords"))
                pwTrainRec.println(record);
            for (String record : values.get("TestingRecords"))
                pwTestRec.println(record);

            pwTrain.close();
            pwTrainRec.close();
            pwTestRec.close();

            if(dh.executeQuery("SELECT COUNT(*) FROM predictors WHERE Scope = '" + stock + "';").get(0).equals("0")) {
                TrainingFileUtils.exportLibSVMFile("res/Simulator/" + stock + "/TrainingFile.csv", "res/Simulator/" + stock + "/TrainingFile.libsvm");
                StockPredictor.trainRandomForest("res/Simulator/" + stock + "/TrainingFile.libsvm", stock);
            }
        }
    }

    static private void trainMultiStock(ArrayList<String> stocks) throws IOException, SQLException {
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

            if(dh.executeQuery("SELECT COUNT(*) FROM predictors WHERE Scope = 'MultiStock';").get(0).equals("0")) {
                TrainingFileUtils.exportLibSVMFile("res/Simulator/MultiStock/TrainingFile.csv", "res/Simulator/MultiStock/TrainingFile.libsvm");
                StockPredictor.trainRandomForest("res/Simulator/MultiStock/TrainingFile.libsvm",stocks.size());
            }
    }

    static public void simulate(ArrayList<String> stocksToSimulate) throws SQLException, IOException {
        //trainMultiStock(stocksToSimulate);
        trainSingleStocks(stocksToSimulate);

        FileReader fr = new FileReader("res/Simulator/TrainingRecords.csv");
        BufferedReader br = new BufferedReader(fr);

        TreeMap<String, ArrayList<Double>> prices = new TreeMap<>();
        TreeMap<String, ArrayList<Double>> reducedPrices = new TreeMap<>();
        int portfolioTimeFrame = 20;

        String line;

        TreeSet<Date> testDates = new TreeSet<>();
        HashMap<String, TreeMap<Date, String>> records = new HashMap<>();

        while ((line = br.readLine()) != null) {
            String[] splitString = line.split(",");

            if(prices.get(splitString[0]) == null)
                prices.put(splitString[0],new ArrayList<>());
            if(records.get(splitString[0]) == null)
                records.put(splitString[0], new TreeMap<>());

            records.get(splitString[0]).put(Date.valueOf(splitString[1]), line);
            prices.get(splitString[0]).add(Double.parseDouble(splitString[5]));
            testDates.add(Date.valueOf(splitString[1]));
        }

        for(String stock : prices.keySet())
            reducedPrices.put(stock, new ArrayList<>(prices.get(stock).subList(prices.get(stock).size() - portfolioTimeFrame,prices.get(stock).size())));

        Map<String, Double> portfolio = PortfolioManager.optimisePortfolio(PortfolioManager.OptimisationMethod.GENETIC_ALGORITHM,1,reducedPrices);

        double cutoff = portfolio.get("RETURN");
        portfolio.remove("RETURN");

        TreeMap<Date, Double> balance = new TreeMap<>();
        TreeMap<Date, ArrayList<String>> actions = new TreeMap<>();

        double currentBalance = 10000;
        double stockWorth = 0;

        //TODO: Simulate buy/sell with test set from here on
        for(Date date : testDates){
            System.out.println("SIMULATING '"+date.toString()+"':");
            //Predict and Perform 1-day buy/sell
            for(String stock : stocksToSimulate) {
                predictStock(stock,1,stocksToSimulate.indexOf(stock),date,records.get(stock).get(date));
            }
            //Rebalance portfolio if it goes past cutoff
            balance.put(date, currentBalance+stockWorth);
        }

System.out.println("Finished Simulation");
    }

    static private boolean predictStock(String stock, int numberOfDays, int stockIndex, Date date, String record) throws SQLException {
        String[] splitString = record.split(",");
        double newsSentiment = NaturalLanguageProcessor.getAverageSentimentOnDate(stock, date.toString());
        double features[] = new double[splitString.length + 1];

        features[0] = stockIndex;
        features[1] = numberOfDays;

        for (int i = 2; i < splitString.length - 1; i++)
            features[i] = Double.parseDouble(splitString[i + 1]);

        features[features.length - 1] = newsSentiment;

        return StockPredictor.predictDirection(new DenseVector(features));
    }
}