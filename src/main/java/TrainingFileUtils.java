import javafx.scene.control.ProgressBar;

import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;

public class TrainingFileUtils {
    static private DatabaseHandler dh;

    static public void initialise(DatabaseHandler tfudh){
        dh = tfudh;
    }

    static public void exportClassificationCSV(ArrayList<String> stocks, String path, int[] days, ProgressBar pb, double smoothPriceAlpha, boolean includeIndicators, boolean includeSentiment) throws FileNotFoundException, SQLException {
        final int t = stocks.size() - 1;
        int c = 0;
        File file = new File(path);
        PrintWriter pw = new PrintWriter(file);

        for (String stock : stocks) {
            for (String value : convertToClassificationTrainingArray(stock, c, days, smoothPriceAlpha, includeIndicators, includeSentiment))
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
        //exportClassificationCSV(stocks,"res/TrainingFiles/Standard_NASDAQ.csv",new int[]{1,5,20,200},pb,1,false, false);

        //resetPriceValues();
        //TechnicalAnalyser.calculateTechnicalIndicators(stocks,false);

        //Technical indicators, no sentiment, no smoothing
        //exportClassificationCSV(stocks,"res/TrainingFiles/Standard_TA_NASDAQ.csv",new int[]{1,5,20,200},pb,1,true, false);

        //No technical indicators, no sentiment, smoothing
        for(double i = 0.1; i < 1; i+=0.1) {
            resetPriceValues();
            SmoothingUtils.smoothStocks(stocks,i);
            exportClassificationCSV(stocks, "res/TrainingFiles/Standard_" + i + "Smooth_NASDAQ.csv", new int[]{1, 5, 20, 200}, pb, i, false, false);
        }

        //Technical indicators, smoothing, no sentiment
        for(double i = 0.1; i < 1; i+=0.1) {
            resetPriceValues();
            SmoothingUtils.smoothStocks(stocks,i);
            TechnicalAnalyser.calculateTechnicalIndicators(stocks,true);

            exportClassificationCSV(stocks, "res/TrainingFiles/Standard_TA_" + i + "Smooth_NASDAQ.csv", new int[]{1, 5, 20, 200}, pb, i, true, false);
        }

        resetPriceValues();
        TechnicalAnalyser.calculateTechnicalIndicators(stocks,false);
        //Technical indicators, sentiment, no smoothing
        exportClassificationCSV(stocks,"res/TrainingFiles/Standard_TA_Sent_NASDAQ.csv",new int[]{1,5,20,200},pb,1,true, true);

        //No technical indicators, sentiment, no smoothing
        exportClassificationCSV(stocks,"res/TrainingFiles/Standard_Sent_NASDAQ.csv",new int[]{1,5,20,200},pb,1,false, true);

        //Technical indicators, smoothing, sentiment
        for(double i = 0.1; i < 1; i+=0.1) {
            resetPriceValues();
            SmoothingUtils.smoothStocks(stocks,i);
            TechnicalAnalyser.calculateTechnicalIndicators(stocks,true);

            exportClassificationCSV(stocks, "res/TrainingFiles/Standard_TA_Sent_" + i + "Smooth_NASDAQ.csv", new int[]{1, 5, 20, 200}, pb, i, true, true);
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
            for (String value : convertToClassificationTrainingArray(stock, c, days,0.2,true,true)) {
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

    static private ArrayList<String> convertToClassificationTrainingArray(String stock, int index, int[] amountOfDaysArray, double smoothPriceAlpha, boolean includeIndicators, boolean includeSentiments) throws SQLException {
        ArrayList<String> dataPoints = new ArrayList<>();

        ArrayList<String> dbSchema = dh.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        StringBuilder command = new StringBuilder("SELECT * FROM dailystockprices WHERE Symbol='" + stock + "'");

        if(includeIndicators)
            for(String column:new String[]{"SMA5", "SMA20", "SMA200", "EMA5", "EMA10", "EMA20", "EMA200", "MACD", "MACDSig", "MACDHist", "RSI", "ADX10", "CCI", "AD", "OBV", "StoOscSlowK", "StoOscSlowD", "WillR"})
                command.append(" AND ").append(column).append(" IS NOT NULL");

        if(smoothPriceAlpha!=1)
            command.append(" AND SmoothedClosePrice IS NOT NULL");

        ArrayList<String> priceValues = dh.executeQuery(command + " ORDER BY TradeDate ASC;");

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
                StringBuilder dataPoint = new StringBuilder(String.valueOf(index) + "," + String.valueOf(amountOfDays));
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
