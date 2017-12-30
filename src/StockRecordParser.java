import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;


public class StockRecordParser {
    static final String IS_NUMERIC = "[-+]?\\d*\\.?\\d+";

    static public void importDailyMarketData(File csvFile, String symbol, DatabaseHandler dh) throws IOException {
        FileReader fr = new FileReader(csvFile);
        BufferedReader br = new BufferedReader(fr);

        String curr;
        ArrayList<String> csvArray = new ArrayList<>();

        while((curr = br.readLine()) != null)
            csvArray.add(curr);

        importDailyMarketData(csvArray, symbol, dh);
    }

    static public void importDailyMarketData(ArrayList<String> csv, String symbol, DatabaseHandler dh) {
        if (csv == null || csv.isEmpty()) return;

        for(String curr : csv) {
            if (curr != null) {
                String[] split = curr.split(",");
                if (split.length == 6 && split[1].matches(IS_NUMERIC)) {
                    String statement = "INSERT INTO dailystockprices (Symbol, TradeDate, OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume) VALUES(";

                    statement += "'" + symbol + "','" + split[0] + "'" + curr.replaceAll(split[0],"")
                            + ") ON DUPLICATE KEY UPDATE " +
                            "OpenPrice = '" + split[1] +
                            "', HighPrice = '" + split[2] +
                            "', LowPrice = '" + split[3] +
                            "', ClosePrice = '" + split[4] +
                            "', TradeVolume = '" + split[5] + "';";

                    try {
                        dh.executeCommand(statement);
                    } catch (Exception e) {
                        System.err.println(e.getMessage() + " " + statement);
                    }
                }
            }
        }
    }

    static public void importData(ArrayList<String> csv, String columns, String table, String symbol, DatabaseHandler dh, int columnCount)  {
        if (csv == null || csv.isEmpty()) return;

        for(String curr : csv) {
            if (curr != null) {
                String split[] = curr.split(",");
                if (split.length == columnCount && split[1].matches(IS_NUMERIC)) {
                    String statement = "INSERT INTO " + table + columns + " VALUES(";

                    statement += "'" + symbol + "','" + split[0] + "'" + curr.replaceAll(split[0], "") + ")" +
                            " ON DUPLICATE KEY UPDATE " +
                            "OpenPrice = '" + split[1] +
                            "', HighPrice = '" + split[2] +
                            "', LowPrice = '" + split[3] +
                            "', ClosePrice = '" + split[4] +
                            "', TradeVolume = '" + split[5] + "';";

                    try {
                        dh.executeCommand(statement);
                    } catch (Exception e) {
                        System.err.println(e.getMessage() + " " + statement);
                    }
                }
            }
        }
    }

    static public void importIntradayMarketData(ArrayList<String> csv, String symbol, DatabaseHandler dh) {
        if(csv == null || csv.isEmpty()) return;

        importData(csv, "(Symbol, TradeDateTime, OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume)","intradaystockprices", symbol, dh, 6);
    }
}
