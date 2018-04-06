package Processing;

import Default.Controller;
import Default.DatabaseHandler;
import Default.Main;
import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MAType;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;
import javafx.scene.control.ProgressBar;
import org.apache.commons.lang.ArrayUtils;

import java.sql.Date;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.1
 */

public class TechnicalAnalyser {
    static private final Core ta = new Core();
    static private DatabaseHandler dh;
    static private ProgressBar pb;

    /**
     * Initialises the technical analyser with a Database Handler to prevent deadlocks when accessing the database and a progress bar to visualise the progress of various methods
     *
     * @param tadh Technical Analyser Database Handler
     * @param pb   Technical Analyser Progress Bar
     */
    static public void initialise(DatabaseHandler tadh, ProgressBar pb) {
        dh = tadh;
        TechnicalAnalyser.pb = pb;

        System.out.println("Initialised Technical Analyser");
    }

    /**
     * Converts an indicator to an integer representation of the amount of days they consider
     * @param indicator Technical Indicator to convert to days
     * @return Number of days the the indicator works upon
     */
    static private int technicalIndicatorToDays(TechnicalIndicator indicator) {
        switch (indicator) {
            case SMA10:
            case EMA10:
            case ADX10:
                return 10;
            case SMA5:
            case EMA5:
                return 5;
            case SMA20:
            case EMA20:
                return 20;
            case SMA200:
            case EMA200:
                return 200;
            case RSI:
            case CCI:
            case WillR:
                return 14;
        }

        return 0;
    }

    /**
     * Sends a Map of Technical-Indicator-to-Value pairs for a list of dates to the database, for a given stock
     * @param stock Stock to associate the Technical Indicator data with
     * @param records Technical Indicator data
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static private void sendToDatabase(String stock, TreeMap<Date, HashMap<String, Double>> records) throws SQLException {
        for(Date date : records.keySet()) {
            StringBuilder command = new StringBuilder("UPDATE dailystockprices SET ");
            HashMap<String, Double> temp = records.get(date);
            for(String indicator : temp.keySet())
                command.append(indicator).append("='").append(temp.get(indicator)).append("',");
            command = new StringBuilder(command.substring(0, command.length() - 1) + " WHERE TradeDate = '" + date + "' AND Symbol='" + stock + "';");
            dh.addBatchCommand(command.toString());
        }
    }

    /**
     * Sends a Map of Date-to-Value pairs for a single indicator to the database, for a given stock
     * @param stock Stock to associate the Technical Indicator data with
     * @param indicator Technical Indicator that the data has been calculated by
     * @param records Technical Indicator data for a given list of dayes
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static private void sendToDatabase(String stock, String indicator, TreeMap<Date, Double> records) throws SQLException {
        ArrayList<String> result = dh.executeQuery("SELECT TradeDate FROM dailystockprices WHERE Symbol='" + stock + "' AND " + indicator + " is not null ORDER BY TradeDate DESC LIMIT 1");

        Date dateFrom = null;
        if (!result.isEmpty())
            dateFrom = Date.valueOf(result.get(0));

        for (Date key : records.keySet())
            if (result.isEmpty() || key.after(Objects.requireNonNull(dateFrom)) || key.equals(dateFrom))
                dh.addBatchCommand("UPDATE dailystockprices SET " + indicator + "=" + "'" + records.get(key) + "' WHERE Symbol = '" + stock + "' AND TradeDate = '" + key + "' AND (" + indicator + " is null OR " + indicator + "!='" + records.get(key) + "');");
    }

    /**
     * Calculates the percentage change between multiple prices of a stock
     *
     * @param stock Stock to calculate the price percentage changes for
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    private static void calculatePercentChanges(String stock) throws SQLException {
        Main.getController().updateCurrentTask("Calculating Close Price Percent Changes for " + stock, false, false);
        calculatePercentChanges(stock, getFromDatabase(stock, "ClosePrice"));
    }

    /**
     * Calculates the percentage change between multiple prices of stocks
     * @param stocks Stocks to calculate the price percentage changes for
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void calculatePercentChanges(ArrayList<String> stocks) throws SQLException {
        double c = 0, t = stocks.size() - 1;
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);

        dh.setAutoCommit(false);

        for(String stock : stocks) {
            calculatePercentChanges(stock);
            Controller.updateProgress(++c, t, pb);
        }

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, t, pb);
        dh.executeBatch();
        dh.setAutoCommit(true);
        Controller.updateProgress(0, t, pb);
    }

    /**
     * Calculates the percentage change between prices on different days
     * @param stock Stock to calcualte the price percentage changes for
     * @param records Price history that needs to be processed for percentage changes
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    private static void calculatePercentChanges(String stock, TreeMap<Date, Double> records) throws SQLException {
        TreeMap<Date, Double> percentChanges = new TreeMap<>();
        ArrayList<Date> dates = new ArrayList<>(records.keySet());


        double[] percentChangeArray = new double[records.size()];
        double prevPrice = records.get(dates.get(0));

        percentChangeArray[0] = 0;


        for(int i = 1; i < percentChangeArray.length; i++){
            Date currDate = dates.get(i);
            double currPrice = records.get(currDate);

            percentChangeArray[i] = (currPrice - prevPrice)/prevPrice;
            prevPrice = currPrice;
        }

        for(int i = 0; i < percentChangeArray.length; i++)
            percentChanges.put(dates.get(i), percentChangeArray[i]);

        sendToDatabase(stock,"PercentChange", percentChanges);

    }

    /**
     * Calculates all Technical Indicators for a given stock
     *
     * @param stock           Stock to calculate Technical Indicators for
     * @param useSmoothedData True if the Technical Analysis should used Exponentially Smoothed data, False if it should use raw price data
     * @param fullUpdate      True if the Technical Analysis database should be overwritten by new values, False if only missing values should be saved to the database
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    private static void calculateTechnicalIndicators(String stock, boolean useSmoothedData, boolean fullUpdate) throws SQLException {
        TreeMap<Date, Double> closePrices;
        if(useSmoothedData)
            closePrices = getFromDatabase(stock, "SmoothedClosePrice");
        else
            closePrices = getFromDatabase(stock, "ClosePrice");
        TreeMap<Date, Double> volumes = getFromDatabase(stock, "TradeVolume");
        TreeMap<Date, Double> lowPrices = getFromDatabase(stock, "LowPrice");
        TreeMap<Date, Double> highPrices = getFromDatabase(stock, "HighPrice");

        TreeMap<Date, HashMap<String, Double>> reshapedIndicators = new TreeMap<>();
        HashMap<String, TreeMap<Date, Double>> indicators = new HashMap<>();
        for (TechnicalIndicator ti : TechnicalIndicator.values())
            indicators.putAll(Objects.requireNonNull(calculateTechnicalIndicator(ti, stock, highPrices, lowPrices, closePrices, volumes, technicalIndicatorToDays(ti))));

        for(String ind : indicators.keySet()){
            TreeMap<Date,Double> temp = indicators.get(ind);
            for (Date date : temp.keySet()) {
                reshapedIndicators.computeIfAbsent(date, k -> new HashMap<>());
                reshapedIndicators.get(date).put(ind, temp.get(date));
            }
        }

        if(fullUpdate)
            sendToDatabase(stock, reshapedIndicators);
        else
            for(String ind : indicators.keySet())
                sendToDatabase(stock, ind, indicators.get(ind));
    }

    /**
     * Calculates all Technical Indicators for a given list of stock s
     * @param stocks List of stocks to calculate Technical Indicators for
     * @param useSmoothedData True if the Technical Analysis should used Exponentially Smoothed data, False if it should use raw price data
     * @param fullUpdate True if the Technical Analysis database should be overwritten by new values, False if only missing values should be saved to the database
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void calculateTechnicalIndicators(ArrayList<String> stocks, boolean useSmoothedData, boolean fullUpdate) throws SQLException {
        double c = 0, t = stocks.size()- 1;
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);

        dh.setAutoCommit(false);

        for (String stock : stocks) {
            calculateTechnicalIndicators(stock, useSmoothedData, fullUpdate);
            Controller.updateProgress(++c, t, pb);
        }

        dh.executeBatch();
        dh.setAutoCommit(true);

        Controller.updateProgress(0, pb);
    }

    /**
     * Retrieves the requested field from the database, for a given stock and formats it as a Date-accessible Map
     * @param stock Stock to retrieve data for
     * @param field Database column to request from the database
     * @return Date-accessible Map of values
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static private TreeMap<Date, Double> getFromDatabase(String stock, String field) throws SQLException {
        ArrayList<String> temp = dh.executeQuery("SELECT TradeDate, " + field + " FROM dailystockprices WHERE Symbol='" + stock + "' ORDER BY TradeDate ASC");
        TreeMap<Date, Double> records = new TreeMap<>();

        for (String record : temp) {
            String[] splitString = record.split(",");
            double price = Double.parseDouble(splitString[1]);
            records.put(Date.valueOf(splitString[0]), price);
        }

        return records;
    }

    /**
     * Takes a list of values and dates and merges them into a Date-accessible Map of values
     * @param dates Dates to merge with price values
     * @param startInd Index to start the merge at
     * @param length Number of values to merge
     * @param values Price values to assign a date to
     * @return A Date-accessible Map of price values
     */
    static private TreeMap<Date, Double> priceArrayToRecordMap(Set<Date> dates, int startInd, int length, double[] values) {
        TreeMap<Date, Double> outputValues = new TreeMap<>();
        int i = 0;
        int j = 0;

        for (Date key : dates) {
            if (j >= startInd && i < length)
                outputValues.put(key, values[i++]);
            else
                j++;
        }

        return outputValues;
    }

    /**
     * Extracts the prices from a Date-to-Price Map
     * @param map Map of Date-to-Price values
     * @return List of price values
     */
    static private double[] recordMapToPriceArray(TreeMap<Date, Double> map) {
        return ArrayUtils.toPrimitive(map.values().toArray(new Double[0]));
    }

    /**
     * Calculates a given Technical Indicator for a given stock
     *
     * @param indicator   Technical Indicator to calculate
     * @param stock       Stock to calculate the Technical Indicator for
     * @param highPrices  List of high prices associated with the stock
     * @param lowPrices   List of low prices associated with the stock
     * @param closePrices List of close prices associated with the stock
     * @param volumes     List of trade volumes associated with the stock
     * @param days        Number of days to calculate technical indicators for
     * @return A Map of Indicator-Name-Accessible values, which are ordered by TradeDate
     */
    private static HashMap<String, TreeMap<Date, Double>> calculateTechnicalIndicator(TechnicalIndicator indicator, String stock, TreeMap<Date, Double> highPrices, TreeMap<Date, Double> lowPrices, TreeMap<Date, Double> closePrices, TreeMap<Date, Double> volumes, int days) {
        Main.getController().updateCurrentTask("Calculating " + indicator.name() + " for " + stock + "...", false, false);

        MInteger begin = new MInteger(), length = new MInteger();

        double[] cPrices = recordMapToPriceArray(closePrices);
        double[] lPrices = recordMapToPriceArray(lowPrices);
        double[] hPrices = recordMapToPriceArray(highPrices);
        double[] volume = recordMapToPriceArray(volumes);

        RetCode rc = null;
        HashMap<String, TreeMap<Date, Double>> results = new HashMap<>();

        switch (indicator) {
            case SMA5:
            case SMA10:
            case SMA20:
            case SMA200: {
                double[] out = new double[closePrices.size()];
                rc = ta.sma(0, cPrices.length - 1, cPrices, days, begin, length, out);
                results.put(indicator.name(), priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
            break;
            case MACD: {
                double[] macd = new double[closePrices.size()], macdSig = new double[closePrices.size()], macdHist = new double[closePrices.size()];
                rc = ta.macd(0, cPrices.length - 1, cPrices, 12, 26, 9, begin, length, macd, macdSig, macdHist);
                results.put("MACD", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, macd));
                results.put("MACDSig", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, macdSig));
                results.put("MACDHist", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, macdHist));
            }
            break;
            case EMA5:
            case EMA10:
            case EMA20:
            case EMA200: {
                double[] out = new double[closePrices.size()];
                rc = ta.ema(0, cPrices.length - 1, cPrices, days, begin, length, out);
                results.put(indicator.name(), priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
            break;
            case RSI: {
                double[] out = new double[closePrices.size()];
                rc = ta.rsi(0, cPrices.length - 1, cPrices, 14, begin, length, out);
                results.put("RSI", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
            break;
            case OBV: {
                double[] out = new double[closePrices.size()];
                rc = ta.obv(0, cPrices.length - 1, cPrices, volume, begin, length, out);
                results.put("OBV", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
            break;
            case ADX10: {
                double[] out = new double[closePrices.size()];
                rc = ta.adx(0, cPrices.length - 1, hPrices, lPrices, cPrices, days, begin, length, out);
                results.put("ADX10", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
            break;
            case CCI: {
                double[] out = new double[closePrices.size()];
                rc = ta.cci(0, cPrices.length - 1, hPrices, lPrices, cPrices, days, begin, length, out);
                results.put("CCI", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
            break;
            case AD: {
                double[] out = new double[closePrices.size()];
                rc = ta.ad(0, cPrices.length - 1, hPrices, lPrices, cPrices, volume, begin, length, out);
                results.put("AD", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
            break;
            case StoOsc: {
                double[] slowK = new double[closePrices.size()], slowD = new double[closePrices.size()];
                rc = ta.stoch(0, cPrices.length - 1, hPrices, lPrices, cPrices, 5, 3, MAType.Sma, 3, MAType.Sma, begin, length, slowK, slowD);
                results.put("StoOscSlowD", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, slowD));
                results.put("StoOscSlowK", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, slowK));
            }
            break;
            case WillR: {
                double[] out = new double[closePrices.size()];
                rc = ta.willR(0, cPrices.length - 1, hPrices, lPrices, cPrices, days, begin, length, out);
                results.put("WillR", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
            break;
        }

        if (rc != null && rc == RetCode.Success) {
            return results;
        } else
            return null;
    }

    public enum TechnicalIndicator {SMA5, SMA10, SMA20, SMA200, MACD, EMA5, EMA10, EMA20, EMA200, RSI, OBV, ADX10, CCI, AD, StoOsc, WillR}
}