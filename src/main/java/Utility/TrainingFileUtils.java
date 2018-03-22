package Utility;

import Default.Controller;
import Default.DatabaseHandler;
import Default.Main;
import Processing.NaturalLanguageProcessor;
import Processing.TechnicalAnalyser;
import javafx.scene.control.ProgressBar;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;

import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.TreeMap;

public class TrainingFileUtils {
    static private DatabaseHandler databaseHandler;

    static public void setDatabaseHandler(DatabaseHandler tfudh) {
        databaseHandler = tfudh;
    }

    static public void splitClassificationLibSVM(String path, double splitPoint, String trainingPath, String testingPath) throws IOException {
        FileReader fr = new FileReader(path);
        BufferedReader br = new BufferedReader(fr);

        File trainingFile = new File(trainingPath);
        File testingFile = new File(testingPath);
        PrintWriter trainingWriter = new PrintWriter(trainingFile);
        PrintWriter testingWriter = new PrintWriter(testingFile);

        String line;

        int noOfStocks = 0;
        int noOfDays = 0;

        int prevStock = -1, currStock = 0;
        int prevDays = -1, currDays = 0;

        TreeMap<Integer, Integer> stockRecords = new TreeMap<>();

        while ((line = br.readLine()) != null){
            String[] splitString = line.split(" ");
            splitString.clone();
            currStock = Integer.parseInt(splitString[1].split(":")[1]);

            Integer stockCount = stockRecords.get(currStock);

            if(stockCount == null)
                stockRecords.put(currStock, 0);
            else
                stockRecords.put(currStock, stockCount + 1);

            currDays = Integer.parseInt(splitString[2].split(":")[1]);
            if(currStock != prevStock)
                noOfStocks++;
            if(noOfStocks == 1 && currDays != prevDays)
                noOfDays++;
            prevStock = currStock;
            prevDays = currDays;
        }

        br.reset();

        int trainingAmount = (int) Math.floor(stockRecords.get(0) * splitPoint);
        int testingAmount = stockRecords.get(0) - trainingAmount;

        int counter = 0;
        int completed = 0;

        if((trainingAmount + testingAmount) != stockRecords.get(0))
            System.err.println("Split error!");

        boolean saveToTraining = true;

        while ((line = br.readLine()) != null){
            if(saveToTraining && counter == trainingAmount) {
                saveToTraining = false;
                counter = 0;
            }

            if(!saveToTraining && counter == testingAmount) {
                saveToTraining = true;
                counter = 0;
                // trainingAmount = (int) Math.floor(stock)
            }

            if(saveToTraining)
                trainingWriter.println(line);
            else
                testingWriter.println(line);

            counter++;
        }


        trainingWriter.close();
        testingWriter.close();

        System.out.println("No of Stocks: " + noOfStocks);
        System.out.println("No of Days:" + noOfDays);

        //pw.println(convertToLibSVM(line));
    }

    static public void splitClassificationCSV(String path, double splitPoint, String trainingPath, String testingPath) throws IOException {
        FileReader fr = new FileReader(path);
        BufferedReader br = new BufferedReader(fr);

        File trainingFile = new File(trainingPath);
        File testingFile = new File(testingPath);
        PrintWriter trainingWriter = new PrintWriter(trainingFile);
        PrintWriter testingWriter = new PrintWriter(testingFile);

        String line;

        while ((line = br.readLine()) != null){

        }
        //pw.println(convertToLibSVM(line));
    }

    static public void exportClassificationCSV(ArrayList<String> stocks, String path, int[] days, ProgressBar pb, double smoothPriceAlpha, boolean includeIndicators, boolean includeSentiment, boolean includeHeader, boolean ignoreNull) throws FileNotFoundException, SQLException {
        final int t = stocks.size() - 1;
        int c = 0;
        File file = new File(path);
        PrintWriter pw = new PrintWriter(file);

        ArrayList<String> dbSchema = databaseHandler.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        for (String stock : stocks) {
            String commandStart = "SELECT COALESCE(" + dbSchema.get(0) + ")" ;
            for(int i = 1; i < dbSchema.size(); i++)
                commandStart += ",COALESCE(" + dbSchema.get(i)+")";
            for (String value : convertToClassificationTrainingArray(stock, commandStart + " FROM dailystockprices WHERE Symbol='" + stock + "'", " ORDER BY TradeDate ASC;", c, days, smoothPriceAlpha, includeIndicators, includeSentiment, ignoreNull, includeHeader && stocks.indexOf(stock) == 0))
                pw.println(value);

            Controller.updateProgress(++c, t, pb);
        }

        pw.close();
    }

    static public void exportLibSVMFile(String csvPath, String libSVMPath) throws IOException {
        FileReader fr = new FileReader(csvPath);
        BufferedReader br = new BufferedReader(fr);

        File writeFile = new File(libSVMPath);
        PrintWriter pw = new PrintWriter(writeFile);

        String line;

        while ((line = br.readLine()) != null)
            pw.println(convertToLibSVM(line));

        fr.close();
        pw.close();
    }

    static public String convertToLibSVM(String dataPoint){
        String[] splitString = dataPoint.split(",");
        int index = 1;

        StringBuilder dp = new StringBuilder(splitString[splitString.length - 1]);

        for (int j = 0; j < splitString.length - 1; j++)
            dp.append(" ").append(index++).append(":").append(splitString[j]);

        return dp.toString();
    }

    static public void exportAllFiles(ArrayList<String> stocks, ProgressBar pb, int[] dayArray) throws FileNotFoundException, SQLException {
        //No technical indicators, no sentiment, no smoothing

        for(Integer day : dayArray)
            exportClassificationCSV(stocks, System.getProperty("user.dir") + "/res/TrainingFiles/" + day + "Day_Standard_NASDAQ.csv", new int[]{day}, pb, 1, false, false, true, false);

        resetPriceValues();
        TechnicalAnalyser.calculateTechnicalIndicators(stocks,false,true);

        //Technical indicators, no sentiment, no smoothing
        for(Integer day : dayArray)
            exportClassificationCSV(stocks, System.getProperty("user.dir") + "/res/TrainingFiles/" + day + "Day_Standard_TA_NASDAQ.csv", new int[]{day}, pb, 1, true, false, true, false);

        //Technical indicators, sentiment, no smoothing
        for(Integer day : dayArray)
            exportClassificationCSV(stocks, System.getProperty("user.dir") + "/res/TrainingFiles/" + day + "Day_Standard_TA_Sentiment_NASDAQ.csv", new int[]{day}, pb, 1, true, true, true, false);

        //No technical indicators, sentiment, no smoothing
        for(Integer day : dayArray)
            exportClassificationCSV(stocks, System.getProperty("user.dir") + "/res/TrainingFiles/" + day + "DayStandard_Sentiment_NASDAQ.csv", new int[]{day}, pb, 1, false, true, true, false);

        for(double i = 0.1; i <= 0.9; i+=0.1) {
            resetPriceValues();
            SmoothingUtils.smoothStocks(stocks,i);
            //No technical indicators, no sentiment, smoothing
            for(Integer day : dayArray)
                exportClassificationCSV(stocks, System.getProperty("user.dir") + "/res/TrainingFiles/" + day + "Day_" + i + "Smoothed_NASDAQ.csv", new int[]{day}, pb, i, false, false, true, false);
            //No Technical indicators, smoothing, sentiment
            for(Integer day : dayArray)
                exportClassificationCSV(stocks, System.getProperty("user.dir") + "/res/TrainingFiles/" + day + "Day_" + i + "Smoothed_Sentiment_NASDAQ.csv", new int[]{day}, pb, i, false, true, true, false);

            TechnicalAnalyser.calculateTechnicalIndicators(stocks,true, true);
            //Technical indicators, smoothing, no sentiment
            for(Integer day : dayArray)
                exportClassificationCSV(stocks, System.getProperty("user.dir") + "/res/TrainingFiles/" + day + "Day_" + i + "Smoothed_TA_NASDAQ.csv", new int[]{day}, pb, i, true, false, true, false);
            //Technical indicators, smoothing, sentiment
            for(Integer day : dayArray)
                exportClassificationCSV(stocks, System.getProperty("user.dir") + "/res/TrainingFiles/" + day + "Day_" + i + "Smoothed_TA_Sentiment_NASDAQ.csv", new int[]{day}, pb, i, true, true, true, false);
        }
    }

    static public void exportSeparateClassificationCSV(ArrayList<String> stocks, String filePath, int[] days, ProgressBar pb) throws FileNotFoundException, SQLException {
        final int t = stocks.size() - 1;
        int c = 0;
        File featureFile = new File(filePath + "Features.csv");
        File labelFile = new File(filePath + "Labels.csv");
        PrintWriter fpw = new PrintWriter(featureFile);
        PrintWriter lpw = new PrintWriter(labelFile);

        for (String stock : stocks) {
            for (String value : convertToClassificationTrainingArray(stock, "SELECT * FROM dailystockprices WHERE Symbol='" + stock + "'"," ORDER BY TradeDate ASC;", c, days,0.25,true,true, false, stocks.indexOf(stock) == 0)) {
                String[] splitString = value.split(",");
                StringBuilder feature = new StringBuilder(splitString[0]);
                for(int i = 1; i < splitString.length-1; i++)
                    feature.append(",").append(splitString[i]);
                fpw.println(feature);
                lpw.println(splitString[splitString.length-1]);
            }

            Controller.updateProgress(++c, t, pb);
        }

        fpw.close();
        lpw.close();
    }

    static public void resetPriceValues() throws SQLException {
        ArrayList<String> dbSchema = databaseHandler.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");
        String[] removeColumns = {"OpenPrice", "HighPrice", "LowPrice", "ClosePrice", "Symbol", "TradeDate", "TradeVolume", "PercentChange"};

        for(String column:removeColumns)
            dbSchema.remove(column);

        String command = "UPDATE dailystockprices SET " +  dbSchema.get(0) + "=null";

        for(int i = 1; i < dbSchema.size(); i++)
            command += "," + dbSchema.get(i) + "=null";

        databaseHandler.executeCommand(command + ";");
    }

    static public ArrayList<String> convertToClassificationTrainingArray(String stock, String commandStart, String commandEnd, int index, int[] amountOfDaysArray, double smoothPriceAlpha, boolean includeIndicators, boolean includeSentiments, boolean ignoreNull, boolean includeHeader) throws SQLException {
        String[] indicators = new String[]{"SMA5", "SMA10", "SMA20", "SMA200", "EMA5", "EMA10", "EMA20", "EMA200", "MACD", "MACDSig", "MACDHist", "RSI", "ADX10", "CCI", "AD", "OBV", "StoOscSlowK", "StoOscSlowD", "WillR"};
        String [] stockData = new String[]{"OpenPrice", "HighPrice", "LowPrice", "ClosePrice", "TradeVolume", "PercentChange"};
        ArrayList<String> dataPoints = new ArrayList<>();

        ArrayList<String> dbSchema = databaseHandler.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        String header = null;

        if(includeHeader) {
            if(amountOfDaysArray.length == 1)
                header = "Index," + stockData[0];
            else
                header = "Index,PredictionPeriod," + stockData[0];
            for (int i = 1; i < stockData.length; i++)
                header += "," + stockData[i];
        }

        StringBuilder command = new StringBuilder(commandStart);

        if(smoothPriceAlpha!=1) {
            if (ignoreNull)
                command.append(" AND SmoothedClosePrice IS NOT NULL");
            if(includeHeader)
                header += ",SmoothedClosePrice";
        }

        if(includeIndicators) {
            if (ignoreNull)
            for (String column : indicators)
                command.append(" AND ").append(column).append(" IS NOT NULL");
            if(includeHeader)
                for (int i = 0; i < indicators.length; i++)
                    header += "," + indicators[i];
        }

        if(includeHeader && includeSentiments)
            header+=",Sentiment";

        if(includeHeader) {
            header += ",Prediction";
            dataPoints.add(header);
        }

        ArrayList<String> priceValues = databaseHandler.executeQuery(command + commandEnd);

        int columnToPredict;

        if(smoothPriceAlpha != 1)
            columnToPredict = dbSchema.indexOf("SmoothedClosePrice");
        else
            columnToPredict = dbSchema.indexOf("ClosePrice");

        double[] currentPrices = new double[priceValues.size()];
        double[] sentiments = null;

        if(includeSentiments)
            sentiments = NaturalLanguageProcessor.getAverageSentiments(stock, priceValues.size());

        for (int amountOfDays : amountOfDaysArray) {
            if((priceValues.size() - amountOfDays) < 0){
                Main.getController().updateCurrentTask("Not enough records", true, false);
                break;
            }

            double[] futurePrices = new double[priceValues.size() - amountOfDays];

            for (int i = 0; i < priceValues.size() - amountOfDays; i++) {
                String[] splitString = priceValues.get(i).split(",");
                currentPrices[i] = Double.parseDouble(splitString[columnToPredict]);
            }

            for (int i = amountOfDays; i < priceValues.size(); i++) {
                String[] splitString = priceValues.get(i).split(",");
                futurePrices[i - amountOfDays] = Double.parseDouble(splitString[columnToPredict]);
            }

            for (int i = 0; i < futurePrices.length; i++) {
                StringBuilder dataPoint = new StringBuilder();
                if(index >= 0)
                    dataPoint.append(String.valueOf(index) + ",");
                if(amountOfDaysArray.length > 1)
                    dataPoint.append(String.valueOf(amountOfDays) + ",");
                String[] splitString = priceValues.get(i).split(",");

                dataPoint.append(splitString[dbSchema.indexOf(stockData[0])]);
                for(int j = 1; j < stockData.length; j++)
                    dataPoint.append(",").append(splitString[dbSchema.indexOf(stockData[j])]);

                if(smoothPriceAlpha != 1)
                    dataPoint.append(",").append(splitString[dbSchema.indexOf("SmoothedClosePrice")]);

                if(includeIndicators)
                    for(String indicator : indicators)
                            dataPoint.append(",").append(splitString[dbSchema.indexOf(indicator)]);

                if(includeSentiments)
                    dataPoint.append(",").append(sentiments[i]);

                dataPoint.append(",").append(String.valueOf((futurePrices[i] - currentPrices[i]) >= 0 ? 1 : 0));

                if(includeHeader && dataPoints.size() == 1 && header.split(",").length != dataPoint.toString().split(",").length)
                    Main.getController().updateCurrentTask("Header/Datapoint Size Mismatch!", true, false);

                dataPoints.add(dataPoint.toString());
            }
        }

        System.out.println("Converted data to Classification Training Array for '" + stock + "'");

        return dataPoints;
    }

    static public Instances loadAttributeCSV(String source) throws IOException {
        CSVLoader csvLoader = new CSVLoader();
        csvLoader.setSource(new File(source));
        return csvLoader.getDataSet();
    }

    static public void exportARFFFile(String source, String destination) throws IOException {
        Instances dataSet = loadAttributeCSV(source);

        ArffSaver arffSaver = new ArffSaver();
        arffSaver.setInstances(dataSet);
        arffSaver.setDestination(new File(destination));
        arffSaver.writeBatch();
    }
}
