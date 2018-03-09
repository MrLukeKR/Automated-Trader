package Utility;

import Default.Controller;
import Default.DatabaseHandler;
import Processing.NaturalLanguageProcessor;
import Processing.TechnicalAnalyser;
import Utility.SmoothingUtils;
import javafx.scene.control.ProgressBar;

import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.TreeMap;

public class TrainingFileUtils {
    static private DatabaseHandler dh;

    static public void initialise(DatabaseHandler tfudh){
        dh = tfudh;
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

    static public void exportClassificationCSV(ArrayList<String> stocks, String path, int[] days, ProgressBar pb, double smoothPriceAlpha, boolean includeIndicators, boolean includeSentiment) throws FileNotFoundException, SQLException {
        final int t = stocks.size() - 1;
        int c = 0;
        File file = new File(path);
        PrintWriter pw = new PrintWriter(file);

        ArrayList<String> dbSchema = dh.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        for (String stock : stocks) {
            String commandStart = "SELECT COALESCE(" + dbSchema.get(0) + ")" ;
            for(int i = 1; i < dbSchema.size(); i++)
                commandStart += ",COALESCE(" + dbSchema.get(i)+")";
            for (String value : convertToClassificationTrainingArray(stock, commandStart+" FROM dailystockprices WHERE Symbol='" + stock + "'", " ORDER BY TradeDate ASC;",c, days, smoothPriceAlpha, includeIndicators, includeSentiment, false))
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

    static public void exportAllFiles(ArrayList<String> stocks, ProgressBar pb) throws FileNotFoundException, SQLException {
        //No technical indicators, no sentiment, no smoothing
        exportClassificationCSV(stocks,"res/TrainingFiles/Standard_NASDAQ.csv",new int[]{1,30,200},pb,1,false, false);

        resetPriceValues();
        TechnicalAnalyser.calculateTechnicalIndicators(stocks,false,true);

        //Technical indicators, no sentiment, no smoothing
        exportClassificationCSV(stocks,"res/TrainingFiles/Standard_TA_NASDAQ.csv",new int[]{1,30,200},pb,1,true, false);

        //No technical indicators, no sentiment, smoothing
        for(double i = 0.1; i < 1; i+=0.1) {
            resetPriceValues();
            SmoothingUtils.smoothStocks(stocks,i);
            exportClassificationCSV(stocks, "res/TrainingFiles/" + i + "Smoothed_NASDAQ.csv", new int[]{1, 30, 200}, pb, i, false, false);
        }

        resetPriceValues();
        TechnicalAnalyser.calculateTechnicalIndicators(stocks,false, true);
        //Technical indicators, sentiment, no smoothing
        exportClassificationCSV(stocks,"res/TrainingFiles/Standard_TA_Sentiment_NASDAQ.csv",new int[]{1,30,200},pb,1,true, true);

        //No technical indicators, sentiment, no smoothing
        exportClassificationCSV(stocks,"res/TrainingFiles/Standard_Sentiment_NASDAQ.csv",new int[]{1,30,200},pb,1,false, true);

        for(double i = 0.1; i <= 0.9; i+=0.1) {
            resetPriceValues();
            SmoothingUtils.smoothStocks(stocks,i);
            TechnicalAnalyser.calculateTechnicalIndicators(stocks,true, true);

            //Technical indicators, smoothing, no sentiment
            exportClassificationCSV(stocks, "res/TrainingFiles/" + i + "Smoothed_TA_NASDAQ.csv", new int[]{1, 30, 200}, pb, i, true, false);
            //Technical indicators, smoothing, sentiment
            exportClassificationCSV(stocks, "res/TrainingFiles/" + i + "Smoothed_TA_Sentiment_NASDAQ.csv", new int[]{1, 30, 200}, pb, i, true, true);
            //No Technical indicators, smoothing, sentiment
            exportClassificationCSV(stocks, "res/TrainingFiles/" + i + "Smoothed_Sentiment_NASDAQ.csv", new int[]{1, 30, 200}, pb, i, false, true);
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
            for (String value : convertToClassificationTrainingArray(stock, "SELECT * FROM dailystockprices WHERE Symbol='" + stock + "'"," ORDER BY TradeDate ASC;", c, days,0.25,true,true, false)) {
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
        ArrayList<String> dbSchema = dh.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");
        String[] removeColumns = {"OpenPrice", "HighPrice", "LowPrice", "ClosePrice", "Symbol", "TradeDate", "TradeVolume", "PercentChange"};

        for(String column:removeColumns)
            dbSchema.remove(column);

        String command = "UPDATE dailystockprices SET " +  dbSchema.get(0) + "=null";

        for(int i = 1; i < dbSchema.size(); i++)
            command += "," + dbSchema.get(i) + "=null";

       dh.executeCommand(command + ";");
    }

    static public ArrayList<String> convertToClassificationTrainingArray(String stock, String commandStart, String commandEnd, int index, int[] amountOfDaysArray, double smoothPriceAlpha, boolean includeIndicators, boolean includeSentiments, boolean ignoreNull) throws SQLException {
        ArrayList<String> dataPoints = new ArrayList<>();

        ArrayList<String> dbSchema = dh.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        StringBuilder command = new StringBuilder(commandStart);

        if(!ignoreNull && includeIndicators)
            for(String column:new String[]{"SMA5", "SMA20", "SMA200", "EMA5", "EMA10", "EMA20", "EMA200", "MACD", "MACDSig", "MACDHist", "RSI", "ADX10", "CCI", "AD", "OBV", "StoOscSlowK", "StoOscSlowD", "WillR"})
                command.append(" AND ").append(column).append(" IS NOT NULL");

        if(!ignoreNull && smoothPriceAlpha!=1)
            command.append(" AND SmoothedClosePrice IS NOT NULL");

        ArrayList<String> priceValues = dh.executeQuery(command + commandEnd);

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
                System.err.println("Not enough records");
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
                dataPoint.append(String.valueOf(amountOfDays));
                String[] splitString = priceValues.get(i).split(",");

                for(String priceColumn : new String[]{"OpenPrice", "HighPrice", "LowPrice", "ClosePrice", "TradeVolume", "PercentChange"})
                    dataPoint.append(",").append(splitString[dbSchema.indexOf(priceColumn)]);

                if(smoothPriceAlpha != 1)
                    dataPoint.append(",").append(splitString[dbSchema.indexOf("SmoothedClosePrice")]);

                if(includeIndicators)
                    for(TechnicalAnalyser.TechnicalIndicator indicator : TechnicalAnalyser.TechnicalIndicator.values()) {
                        if(indicator.equals(TechnicalAnalyser.TechnicalIndicator.MACD)){
                            dataPoint.append(",").append(splitString[dbSchema.indexOf("MACD")]);
                            dataPoint.append(",").append(splitString[dbSchema.indexOf("MACDSig")]);
                            dataPoint.append(",").append(splitString[dbSchema.indexOf("MACDHist")]);
                        }else if (indicator.equals(TechnicalAnalyser.TechnicalIndicator.StoOsc)){
                            dataPoint.append(",").append(splitString[dbSchema.indexOf("StoOscSlowD")]);
                            dataPoint.append(",").append(splitString[dbSchema.indexOf("StoOscSlowK")]);
                        }else
                        dataPoint.append(",").append(splitString[dbSchema.indexOf(indicator.name())]);
                    }

                if(includeSentiments)
                    dataPoint.append(",").append(sentiments[i]);

                dataPoint.append(",").append(String.valueOf((futurePrices[i] - currentPrices[i]) >= 0 ? 1 : 0));
                dataPoints.add(dataPoint.toString());
            }
        }

        System.out.println("Converted data to Classification Training Array for '" + stock + "'");

        return dataPoints;
    }
}
