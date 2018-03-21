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


public class StockRecordParser {
    private static final String IS_NUMERIC = "[-+]?\\d*\\.?\\d+";
    private static DatabaseHandler dh;


    static public void initialise(DatabaseHandler sqdh) {
        dh = sqdh;

        System.out.println("Initialised Stock Record Parser");
    }

    static public void importDailyMarketData(File csvFile, String symbol) throws IOException, SQLException {
        FileReader fr = new FileReader(csvFile);
        BufferedReader br = new BufferedReader(fr);

        String curr;
        ArrayList<String> csvArray = new ArrayList<>();

        while((curr = br.readLine()) != null)
            csvArray.add(curr);

        importDailyMarketData(csvArray, symbol);
    }

    static public void importDailyMarketData(ArrayList<String> csv, String symbol) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        importDailyData(csv, symbol);
    }

    static public void importDailyYahooMarketData(File csvFile, String symbol) throws SQLException, IOException {
        FileReader fr = new FileReader(csvFile);
        BufferedReader br = new BufferedReader(fr);

        String curr;
        ArrayList<String> csvArray = new ArrayList<>();

        while ((curr = br.readLine()) != null)
            csvArray.add(curr);

        importDailyYahooMarketData(csvArray, symbol);
    }

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

        importDailyData(newCsv, symbol);
    }

    static public void importIntradayMarketData(ArrayList<String> csv, String symbol) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        importIntradayData(csv, symbol);
    }


    static private TreeMap<Timestamp, String> cleanCSVWithTimestamp(ArrayList<String> csv, int columnCount, Timestamp timeFrom) {
        TreeMap<Timestamp, String> newCSV = new TreeMap<>();

        for (String curr : csv)
            if (!(curr == null || curr.isEmpty())) {
                String split[] = curr.split(",");
                if (split.length == columnCount && split[1].matches(IS_NUMERIC)) {
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

    static private TreeMap<Date, String> cleanCSVWithDate(ArrayList<String> csv, int columnCount, Date dateFrom) {
        TreeMap<Date, String> newCSV = new TreeMap<>();

        for (String curr : csv)
            if (!(curr == null || curr.isEmpty())) {
                String split[] = curr.split(",");
                if (split.length == columnCount && split[1].matches(IS_NUMERIC)) {
                    try {
                        Date currDate = Date.valueOf(split[0]);
                        if (dateFrom == null || currDate.equals(dateFrom) || currDate.after(dateFrom))
                            newCSV.put(currDate, curr);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        return newCSV;
    }

    static public void importCurrentQuote(String csv, String stock) throws SQLException {
        dh.executeCommand("INSERT INTO intradaystockprices VALUES('" + stock + "'," + csv + ", 1) ON DUPLICATE KEY UPDATE OpenPrice=VALUES(OpenPrice), HighPrice=VALUES(HighPrice), LowPrice=VALUES(LowPrice), ClosePrice=VALUES(ClosePrice), TradeVolume=VALUES(TradeVolume), Temporary=1;");
        dh.executeCommand("INSERT INTO dailystockprices(Symbol, TradeDate, OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume) VALUES('" + stock + "'," + csv + ") ON DUPLICATE KEY UPDATE OpenPrice=VALUES(OpenPrice), HighPrice=VALUES(HighPrice), LowPrice=VALUES(LowPrice), ClosePrice=VALUES(ClosePrice), TradeVolume=VALUES(TradeVolume);");
    }

    private static void importIntradayData(ArrayList<String> csv, String symbol) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        ArrayList<String> result = dh.executeQuery("SELECT TradeDateTime FROM intradaystockprices WHERE symbol='" + symbol + "' ORDER BY TradeDateTime DESC LIMIT 1;");

        Timestamp timeFrom = null;

        if (!result.isEmpty())
            timeFrom = Timestamp.valueOf(result.get(0));

        int newValues = 0;
        int count = 0;
        StringBuilder statement = new StringBuilder("INSERT INTO " + "intradaystockprices" + "(Symbol, TradeDateTime, OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume)" + " VALUES ");

        TreeMap<Timestamp, String> newCSV = cleanCSVWithTimestamp(csv, 6, timeFrom);

        for (Timestamp curr : newCSV.keySet()) {
            count++;
            String data = newCSV.get(curr);
            String split[] = data.split(",");

            statement.append("('").append(symbol).append("','").append(split[0]).append("'").append(data.replaceAll(split[0], "")).append(")");
            if (count < newCSV.size() && curr != timeFrom)
                statement.append(",\r\n");
            newValues++;
        }

        statement.append(" ON DUPLICATE KEY UPDATE " + "OpenPrice = VALUES(OpenPrice)" + ", HighPrice = VALUES(HighPrice)" + ", LowPrice = VALUES(LowPrice)" + ", ClosePrice = VALUES(ClosePrice)" + ", TradeVolume = VALUES(TradeVolume);");

        if (newValues > 0)
            try {
                dh.executeCommand(statement.toString());
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.err.println(statement);
            }
    }

    private static void importDailyData(ArrayList<String> csv, String symbol) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        ArrayList<String> result = dh.executeQuery("SELECT TradeDate FROM dailystockprices WHERE symbol='" + symbol + "' ORDER BY TradeDate DESC LIMIT 1;");

        Date dateFrom = null;

        if (!result.isEmpty())
            dateFrom = Date.valueOf(result.get(0));

        int newValues = 0;
        int count = 0;
        StringBuilder statement = new StringBuilder("INSERT INTO " + "dailystockprices" + "(Symbol, TradeDate, OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume)" + " VALUES ");

        TreeMap<Date, String> newCSV = cleanCSVWithDate(csv, 6, dateFrom);

        for (Date curr : newCSV.keySet()) {
            count++;
            String data = newCSV.get(curr);
            String split[] = data.split(",");

            statement.append("('").append(symbol).append("','").append(split[0]).append("'").append(data.replaceAll(split[0], "")).append(")");
            if (count < newCSV.size() && curr != dateFrom)
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
