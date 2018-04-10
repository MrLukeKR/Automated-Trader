package Processing;

import Default.Controller;
import Default.DatabaseHandler;
import Default.Main;
import javafx.scene.control.ProgressBar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.1
 */

public class StockRecordParser {
    private static final String IS_NUMERIC = "[-+]?\\d*\\.?\\d+";
    private static DatabaseHandler dh;

    /**
     * Initialises the Stock Record Parser with a Database Handler to prevent deadlocks when accessing the database
     *
     * @param sqdh Stock Quote Database Handler
     */
    static public void initialise(DatabaseHandler sqdh) {
        dh = sqdh;

        Main.getController().updateCurrentTask("Initialised Stock Record Parser", false, false);
    }

    /**
     * Imports a list of CSV values into the daily price database
     *
     * @param csv    List of stock history Comma Separated Values
     * @param symbol Stock ticker to associate the data with
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void importDailyMarketData(ArrayList<String> csv, String symbol) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        importData(csv, symbol, false);
    }

    /**
     * Imports Yahoo! CSV files (useful for getting ALL available data since the IPO of the stock)
     *
     * @param csvFile Yahoo! stock history CSV data file
     * @param symbol  Stock to associate the data with
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     * @throws IOException  Throws IOException if the CSV file cannot be found or cannot be read
     */
    private static void importDailyYahooMarketData(File csvFile, String symbol) throws SQLException, IOException {
        FileReader fr = new FileReader(csvFile);
        BufferedReader br = new BufferedReader(fr);

        String curr;
        ArrayList<String> csvArray = new ArrayList<>();

        while ((curr = br.readLine()) != null)
            csvArray.add(curr);

        importDailyYahooMarketData(csvArray, symbol);
    }

    /**
     * Imports Yahoo! CSV values (useful for getting ALL available data since the IPO of the stock)
     * @param csv Yahoo! stock history CSV list
     * @param symbol Stock to associate the data with
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    private static void importDailyYahooMarketData(ArrayList<String> csv, String symbol) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        ArrayList<String> newCsv = new ArrayList<>();

        for (String curr : csv) {
            String[] splitString = curr.split(",");
            StringBuilder newString = new StringBuilder();

            for (int i = 0; i < 7; i++)
                if (i != 5) {
                    newString.append(splitString[i]);
                    if (i != 6)
                        newString.append(",");
                }

            newCsv.add(newString.toString());
        }

        importData(newCsv, symbol, false);
    }

    /**
     * Imports a list of CSV values into the intraday price database
     * @param csv List of stock history Comma Separated Values
     * @param symbol Stock to associate the data with
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void importIntradayMarketData(ArrayList<String> csv, String symbol) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        importData(csv, symbol, true);
    }

    /**
     * Converts a pure CSV file into a Map of records that can be accessed via a timestamp linked to the record
     *
     * @param csv      List of CSV records
     * @param timeFrom Only consider records that are equal to or after this value
     * @return A Map of CSV records, accessible by timestamp
     */
    static private TreeMap<Timestamp, String> cleanCSVWithTimestamp(ArrayList<String> csv, Timestamp timeFrom) {
        TreeMap<Timestamp, String> newCSV = new TreeMap<>();

        for (String curr : csv)
            if (!(curr == null || curr.isEmpty())) {
                String split[] = curr.split(",");
                if (split.length == 6 && split[1].matches(IS_NUMERIC)) {
                    try {
                        Timestamp currTime = Timestamp.valueOf(split[0]);
                        if (timeFrom == null || currTime.equals(timeFrom) || currTime.after(timeFrom))
                            newCSV.put(currTime, curr);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        return newCSV;
    }

    /**
     * Converts a pure CSV file into a Map of records that can be accessed via a date linked to the record
     *
     * @param csv      List of CSV records
     * @param dateFrom Only consider records that are equal to or after this value
     * @return A Map of CSV records, accessible by date
     */
    static private TreeMap<Date, String> cleanCSVWithDate(ArrayList<String> csv, Date dateFrom) {
        TreeMap<Date, String> newCSV = new TreeMap<>();

        for (String curr : csv)
            if (!(curr == null || curr.isEmpty())) {
                String split[] = curr.split(",");
                if (split.length == 6 && split[1].matches(IS_NUMERIC)) {
                    try {
                        Date currDate = Date.valueOf(split[0].split(" ")[0]);
                        if (dateFrom == null || currDate.equals(dateFrom) || currDate.after(dateFrom))
                            newCSV.put(currDate, curr);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        return newCSV;
    }

    /**
     * Imports the latest quote to both the intraday and daily price database
     * @param csv CSV quote data
     * @param stock Stock to associate the quote data with
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void importCurrentQuote(String csv, String stock) throws SQLException {
        dh.executeCommand("INSERT INTO intradaystockprices VALUES('" + stock + "'," + csv + ", 1) ON DUPLICATE KEY UPDATE OpenPrice=VALUES(OpenPrice), HighPrice=VALUES(HighPrice), LowPrice=VALUES(LowPrice), ClosePrice=VALUES(ClosePrice), TradeVolume=VALUES(TradeVolume), Temporary=1;");
        dh.executeCommand("INSERT INTO dailystockprices(Symbol, TradeDate, OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume) VALUES('" + stock + "'," + csv + ") ON DUPLICATE KEY UPDATE OpenPrice=VALUES(OpenPrice), HighPrice=VALUES(HighPrice), LowPrice=VALUES(LowPrice), ClosePrice=VALUES(ClosePrice), TradeVolume=VALUES(TradeVolume);");
    }

    /**
     * Imports a list of CSV values into the price database
     * @param csv List of CSV price values
     * @param symbol Stock to associate the price values with
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    private static void importData(ArrayList<String> csv, String symbol, boolean isIntraday) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        ArrayList<String> result = dh.executeQuery("SELECT MAX(TradeDate" + (isIntraday ? "Time" : "") + ") FROM " + (isIntraday ? "intradaystockprices" : "dailystockprices") + " WHERE symbol='" + symbol + "' ORDER BY TradeDate" + (isIntraday ? "Time" : "") + ";");
        Object from = null;

        if (!result.isEmpty()) from = (isIntraday) ? Timestamp.valueOf(result.get(0)) : Date.valueOf(result.get(0));

        int newValues = 0;
        int count = 0;

        StringBuilder statement = new StringBuilder("INSERT INTO " + (isIntraday ? "intradaystockprices" : "dailystockprices") + "(Symbol, TradeDate" + ((isIntraday) ? "Time" : "") + ", OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume)" + " VALUES ");

        TreeMap<Object, String> newCSV = new TreeMap<>((isIntraday) ? cleanCSVWithTimestamp(csv, (Timestamp) from) : cleanCSVWithDate(csv, (Date) from));

        for (Object curr : newCSV.keySet()) {
            count++;
            String data = newCSV.get(curr);
            String split[] = data.split(",");

            statement.append("('").append(symbol).append("','").append(split[0]).append("'").append(data.replaceAll(split[0], "")).append(")");
            if (count < newCSV.size() && curr != from)
                statement.append(",\r\n");
            newValues++;
        }

        statement.append(" ON DUPLICATE KEY UPDATE " + "OpenPrice = VALUES(OpenPrice)" + ", HighPrice = VALUES(HighPrice)" + ", LowPrice = VALUES(LowPrice)" + ", ClosePrice = VALUES(ClosePrice)" + ", TradeVolume = VALUES(TradeVolume);");

        if (newValues > 0)
            try {
                dh.executeCommand(statement.toString());
            } catch (Exception e) {
                System.err.println(e.getMessage() + " " + statement);
            }
    }

    /**
     * Imports all Yahoo! stock history files for all given stocks and updates the given progress bar appropriately
     * @param stocks List of stocks to import the Yahoo! files of
     * @param stockFeedProgress ProgressBar to visalise progress of import
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     * @throws IOException Throws IOException if the CSV file cannot be found or cannot be read
     */
    static public void processYahooHistories(ArrayList<String> stocks, ProgressBar stockFeedProgress) throws SQLException, IOException {
        double curr = 0;
        final double t = stocks.size();

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockFeedProgress);

        for (String symbol : stocks) {
            ArrayList<String> results = dh.executeQuery("SELECT COUNT(*) FROM dailystockprices WHERE Symbol='" + symbol + "';");
            if (results.isEmpty() || Integer.parseInt(results.get(0)) == 0) {
                Main.getController().updateCurrentTask("Importing Yahoo! records for: " + symbol, false, false);

                File file = new File(System.getProperty("user.dir") + "/res/historicstocks/" + symbol + ".csv");

                if (file.exists()) {
                    StockRecordParser.importDailyYahooMarketData(file, symbol);
                    Main.getController().updateCurrentTask("Successfully committed complete Yahoo! records of " + symbol + " to the database!", false, false);
                } else
                    Main.getController().updateCurrentTask("No Yahoo history available for " + symbol, true, true);
            }

            Controller.updateProgress(++curr, t, stockFeedProgress);
        }
    }
}