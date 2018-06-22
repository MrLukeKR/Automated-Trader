package Utility;

import Default.Controller;
import Default.DatabaseHandler;
import Default.Main;
import Processing.NaturalLanguageProcessor;
import Processing.TechnicalAnalyser;
import javafx.scene.control.ProgressBar;

import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.6
 */

public class TrainingFileUtils {
    static private DatabaseHandler databaseHandler;

    /**
     * Initialses the TrainingFileUtils class with a database handler to prevent deadlocks when accessing the database
     *
     * @param tfudh Training File Utils Database Handler
     */
    static public void setDatabaseHandler(DatabaseHandler tfudh) {
        databaseHandler = tfudh;
    }

    /**
     * Exports the data from the database into a CSV file for a given stock
     *
     * @param stock             Stock ticker to gather data for (e.g. AAPL for Apple Inc.)
     * @param path              File path to save the file to
     * @param days              List of days to calculate price rise/fall values for
     * @param smoothPriceAlpha  Smoothing factor to apply to the price daa
     * @param includeIndicators True if indicator values should be included, False otherwise
     * @param includeSentiment  True if news sentiment values should be included, False otherwise
     * @param includeHeader     True if the name of each column should be included, False otherwise
     * @param ignoreNull        True if null values are left out, False if they should be included as a 0 value
     * @throws FileNotFoundException Throws FileNotFoundException if the file does not exist
     * @throws SQLException          Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void exportClassificationCSV(String stock, String path, int[] days, double smoothPriceAlpha, boolean includeIndicators, boolean includeSentiment, boolean includeHeader, boolean ignoreNull) throws FileNotFoundException, SQLException {
        File file = new File(path);
        PrintWriter pw = new PrintWriter(file);

        ArrayList<String> dbSchema = databaseHandler.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        StringBuilder commandStart = new StringBuilder("SELECT COALESCE(" + dbSchema.get(0) + ", 0)");
        for (int i = 1; i < dbSchema.size(); i++)
            commandStart.append(",COALESCE(").append(dbSchema.get(i)).append(", 0)");
        for (String value : convertToClassificationTrainingArray(stock, commandStart + " FROM dailystockprices WHERE Symbol='" + stock + "'", " ORDER BY TradeDate ASC;", -1, days, smoothPriceAlpha, includeIndicators, includeSentiment, ignoreNull, includeHeader))
            pw.println(value);

        pw.close();
    }

    /**
     * Exports the data from the database into a CSV file for a given list of stocks
     *
     * @param stocks            List of stock tickers to gather data for (e.g. AAL, AAPL, BIIB etc.)
     * @param path              File path to save the file to
     * @param days              List of days to calculate price rise/fall values for
     * @param pb                Progress Bar to visualise the progress of the export process
     * @param smoothPriceAlpha  Smoothing factor to apply to the price daa
     * @param includeIndicators True if indicator values should be included, False otherwise
     * @param includeSentiment  True if news sentiment values should be included, False otherwise
     * @param includeHeader     True if the name of each column should be included, False otherwise
     * @param ignoreNull        True if null values are left out, False if they should be included as a 0 value
     * @throws FileNotFoundException Throws FileNotFoundException if the file does not exist
     * @throws SQLException          Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void exportClassificationCSV(ArrayList<String> stocks, String path, int[] days, ProgressBar pb, double smoothPriceAlpha, boolean includeIndicators, boolean includeSentiment, boolean includeHeader, boolean ignoreNull) throws FileNotFoundException, SQLException {
        final int t = stocks.size() - 1;
        int c = 0;
        File file = new File(path);
        PrintWriter pw = new PrintWriter(file);

        ArrayList<String> dbSchema = databaseHandler.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        for (String stock : stocks) {
            StringBuilder commandStart = new StringBuilder("SELECT COALESCE(" + dbSchema.get(0) + ", 0)");
            for (int i = 1; i < dbSchema.size(); i++)
                commandStart.append(",COALESCE(").append(dbSchema.get(i)).append(", 0)");
            for (String value : convertToClassificationTrainingArray(stock, commandStart + " FROM dailystockprices WHERE Symbol='" + stock + "'", " ORDER BY TradeDate ASC;", c, days, smoothPriceAlpha, includeIndicators, includeSentiment, ignoreNull, includeHeader && stocks.indexOf(stock) == 0))
                pw.println(value);

            Controller.updateProgress(++c, t, pb);
        }

        pw.close();
    }

    /**
     * Converts a CSV data file to a LibSVM Format data file
     * @param csvPath File path of the CSV file to load
     * @param libSVMPath File path of the LibSVM file to save
     * @throws IOException Throws IOException if either file cannot be found
     */
    static public void exportLibSVMFile(String csvPath, String libSVMPath) throws IOException {
        Main.getController().updateCurrentTask("Converting CSV to LIBSVM file", false, false);
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

    /**
     * Converts a CSV datapoint to LibSVM format
     * @param dataPoint CSV datapoint (stock record)
     * @return LibSVM formatted datapoint (stock record)
     */
    private static String convertToLibSVM(String dataPoint){
        String[] splitString = dataPoint.split(",");
        int index = 1;

        StringBuilder dp = new StringBuilder(splitString[splitString.length - 1]);

        for (int j = 0; j < splitString.length - 1; j++)
            dp.append(" ").append(index++).append(":").append(splitString[j]);

        return dp.toString();
    }

    /**
     * Enumerates all possible variations of the data for use in WEKA experiments to determine well-performing models
     * @param stocks List of stocks to export files for
     * @param pb Progress Bar to visualise the progress of the export process
     * @param dayArray List of days to calculate price rise/fall values for
     * @throws FileNotFoundException Throws FileNotFoundException if the file does not exist
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
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

    /**
     * Resets all indicator values and smoothed prices to null
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void resetPriceValues() throws SQLException {
        ArrayList<String> dbSchema = databaseHandler.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");
        String[] removeColumns = {"OpenPrice", "HighPrice", "LowPrice", "ClosePrice", "Symbol", "TradeDate", "TradeVolume", "PercentChange"};

        for (String column : removeColumns)
            dbSchema.remove(column);

        StringBuilder command = new StringBuilder("UPDATE dailystockprices SET " + dbSchema.get(0) + "=null");

        for (int i = 1; i < dbSchema.size(); i++)
            command.append(",").append(dbSchema.get(i)).append("=null");

        databaseHandler.executeCommand(command + ";");
    }

    /**
     * Converts data gathered from the database into a CSV array
     * @param stock Stock to convert to CSV
     * @param commandStart First part of the SQL command to gather data from the database
     * @param commandEnd End part of the SQL command to gather data from the database
     * @param index Index of the stock from the main stock list
     * @param amountOfDaysArray List of days to calculate price rise/fall information for
     * @param smoothPriceAlpha Smoothing factor to apply to the price daa
     * @param includeIndicators True if indicator values should be included, False otherwise
     * @param includeSentiments True if news sentiment values should be included, False otherwise
     * @param includeHeader True if the name of each column should be included, False otherwise
     * @param ignoreNull True if null values are left out, False if they should be included as a 0 value
     * @return A CSV formatted array of records
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public ArrayList<String> convertToClassificationTrainingArray(String stock, String commandStart, String commandEnd, int index, int[] amountOfDaysArray, double smoothPriceAlpha, boolean includeIndicators, boolean includeSentiments, boolean ignoreNull, boolean includeHeader) throws SQLException {
        String[] indicators = new String[]{"SMA5", "SMA10", "SMA20", "SMA200", "EMA5", "EMA10", "EMA20", "EMA200", "MACD", "MACDSig", "MACDHist", "RSI", "ADX10", "CCI", "AD", "OBV", "StoOscSlowK", "StoOscSlowD", "WillR"};
        String [] stockData = new String[]{"OpenPrice", "HighPrice", "LowPrice", "ClosePrice", "TradeVolume", "PercentChange"};
        ArrayList<String> dataPoints = new ArrayList<>();

        ArrayList<String> dbSchema = databaseHandler.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        StringBuilder header = null;

        if (includeHeader) {
            if (amountOfDaysArray.length == 1)
                header = new StringBuilder("Index," + stockData[0]);
            else
                header = new StringBuilder("Index,PredictionPeriod," + stockData[0]);
            for (int i = 1; i < stockData.length; i++)
                header.append(",").append(stockData[i]);
        }

        StringBuilder command = new StringBuilder(commandStart);

        if(smoothPriceAlpha!=1) {
            if (ignoreNull)
                command.append(" AND SmoothedClosePrice IS NOT NULL");
            if(includeHeader)
                header.append(",SmoothedClosePrice");
        }

        if (includeIndicators) {
            if (ignoreNull)
                for (String column : indicators)
                    command.append(" AND ").append(column).append(" IS NOT NULL");
            if (includeHeader)
                for (String indicator : indicators) header.append(",").append(indicator);
        }

        if(includeHeader && includeSentiments)
            header.append(",Sentiment");

        if (includeHeader) {
            header.append(",Prediction");
            dataPoints.add(header.toString());
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
                if (index >= 0)
                    dataPoint.append(String.valueOf(index)).append(",");
                if (amountOfDaysArray.length > 1)
                    dataPoint.append(String.valueOf(amountOfDays)).append(",");
                String[] splitString = priceValues.get(i).split(",");

                dataPoint.append(splitString[dbSchema.indexOf(stockData[0])]);
                for(int j = 1; j < stockData.length; j++)
                    dataPoint.append(",").append(splitString[dbSchema.indexOf(stockData[j])]);

                if(smoothPriceAlpha != 1)
                    dataPoint.append(",").append(splitString[dbSchema.indexOf("SmoothedClosePrice")]);

                if (includeIndicators)
                    for (String indicator : indicators)
                        dataPoint.append(",").append(splitString[dbSchema.indexOf(indicator)]);

                if(includeSentiments)
                    dataPoint.append(",").append(sentiments[i]);

                dataPoint.append(",").append(String.valueOf((futurePrices[i] - currentPrices[i]) >= 0 ? 1 : 0));

                if (includeHeader && dataPoints.size() == 1 && header.toString().split(",").length != dataPoint.toString().split(",").length)
                    Main.getController().updateCurrentTask("Header/Datapoint Size Mismatch!", true, false);

                dataPoints.add(dataPoint.toString());
            }
        }

        System.out.println("Converted data to Classification Training Array for '" + stock + "'");

        return dataPoints;
    }
}