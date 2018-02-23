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
    static final String IS_NUMERIC = "[-+]?\\d*\\.?\\d+";
    static DatabaseHandler dh;


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

        importDailyData(csv, "(Symbol, TradeDate, OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume)", "dailystockprices", symbol, 6);
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

    static public void importDailyYahooMarketData(ArrayList<String> csv, String symbol) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        ArrayList<String> newCsv = new ArrayList<>();

        for (String curr : csv) {
            String[] splitString = curr.split(",");
            String newString = "";

            for (int i = 0; i < 7; i++)
                if (i != 5) {
                    newString += splitString[i];
                    if (i != 6)
                        newString += ",";
                }

            newCsv.add(newString);
        }

        importDailyData(newCsv, "(Symbol, TradeDate, OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume)", "dailystockprices", symbol, 6);
    }

    static public void importIntradayMarketData(ArrayList<String> csv, String symbol) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        importIntradayData(csv, "(Symbol, TradeDateTime, OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume)", "intradaystockprices", symbol, 6);
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

    static public void importIntradayData(ArrayList<String> csv, String columns, String table, String symbol, int columnCount) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        ArrayList<String> result = dh.executeQuery("SELECT TradeDateTime FROM intradaystockprices WHERE symbol='" + symbol + "' ORDER BY TradeDateTime DESC LIMIT 1;");

        Timestamp timeFrom = null;

        if (!result.isEmpty())
            timeFrom = Timestamp.valueOf(result.get(0));

        int newValues = 0;
        int count = 0;
        String statement = "INSERT INTO " + table + columns + " VALUES ";

        TreeMap<Timestamp, String> newCSV = cleanCSVWithTimestamp(csv, columnCount, timeFrom);

        for (Timestamp curr : newCSV.keySet()) {
            count++;
            String data = newCSV.get(curr);
            String split[] = data.split(",");

            statement += "('" + symbol + "','" + split[0] + "'" + data.replaceAll(split[0], "") + ")";
            if (count < newCSV.size() && curr != timeFrom)
                statement += ",\r\n";
            newValues++;
        }

        statement += " ON DUPLICATE KEY UPDATE " +
                "OpenPrice = VALUES(OpenPrice)" +
                ", HighPrice = VALUES(HighPrice)" +
                ", LowPrice = VALUES(LowPrice)" +
                ", ClosePrice = VALUES(ClosePrice)" +
                ", TradeVolume = VALUES(TradeVolume);";

        if (newValues > 0)
            try {
                dh.executeCommand(statement);
            } catch (Exception e) {
                System.err.println(e.getMessage() + " " + statement);
            }
    }

    static public void importDailyData(ArrayList<String> csv, String columns, String table, String symbol, int columnCount) throws SQLException {
        if (csv == null || csv.isEmpty()) return;

        ArrayList<String> result = dh.executeQuery("SELECT TradeDate FROM dailystockprices WHERE symbol='" + symbol + "' ORDER BY TradeDate DESC LIMIT 1;");

        Date dateFrom = null;

        if (!result.isEmpty())
            dateFrom = Date.valueOf(result.get(0));

        int newValues = 0;
        int count = 0;
        String statement = "INSERT INTO " + table + columns + " VALUES ";

        TreeMap<Date, String> newCSV = cleanCSVWithDate(csv, columnCount, dateFrom);

         for (Date curr : newCSV.keySet()) {
            count++;
            String data = newCSV.get(curr);
            String split[] = data.split(",");

            statement += "('" + symbol + "','" + split[0] + "'" + data.replaceAll(split[0], "") + ")";
            if (count < newCSV.size() && curr != dateFrom)
                        statement += ",\r\n";
            newValues++;
            }

        statement += " ON DUPLICATE KEY UPDATE " +
                "OpenPrice = VALUES(OpenPrice)" +
                ", HighPrice = VALUES(HighPrice)" +
                ", LowPrice = VALUES(LowPrice)" +
                ", ClosePrice = VALUES(ClosePrice)" +
                ", TradeVolume = VALUES(TradeVolume);";

        if (newValues > 0)
            try {
                dh.executeCommand(statement);
            } catch (Exception e) {
                System.err.println(e.getMessage() + " " + statement);
            }
    }
}
