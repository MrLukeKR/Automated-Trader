import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;


public class StockRecordParser {
    static final String IS_NUMERIC = "[-+]?\\d*\\.?\\d+";
    static DatabaseHandler dh;


    static public void initialise(DatabaseHandler sqdh) {
        dh = sqdh;

        System.out.println("Initialised Stock Record Parser");
    }

    static public void importDailyMarketData(File csvFile, String symbol) throws IOException {
        FileReader fr = new FileReader(csvFile);
        BufferedReader br = new BufferedReader(fr);

        String curr;
        ArrayList<String> csvArray = new ArrayList<>();

        while((curr = br.readLine()) != null)
            csvArray.add(curr);

        importDailyMarketData(csvArray, symbol);
    }

    static public void importDailyMarketData(ArrayList<String> csv, String symbol) {
        if (csv == null || csv.isEmpty()) return;

        importData(csv, "(Symbol, TradeDate, OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume)", "dailystockprices", symbol, 6);

    }

    static public void importIntradayMarketData(ArrayList<String> csv, String symbol) {
        if (csv == null || csv.isEmpty()) return;

        importData(csv, "(Symbol, TradeDateTime, OpenPrice, HighPrice, LowPrice, ClosePrice, TradeVolume)", "intradaystockprices", symbol, 6);
    }

    static public void importData(ArrayList<String> csv, String columns, String table, String symbol, int columnCount) {
        if (csv == null || csv.isEmpty()) return;

        String statement = "INSERT INTO " + table + columns + " VALUES";

        int count = 1;
        for(String curr : csv) {
            if (curr != null) {
                String split[] = curr.split(",");
                if (split.length == columnCount && split[1].matches(IS_NUMERIC)) {

                    statement += "('" + symbol + "','" + split[0] + "'" + curr.replaceAll(split[0], "") + ")";
                    if (++count < csv.size())
                        statement += ",\r\n";
                }
            }
        }

        statement += " ON DUPLICATE KEY UPDATE " +
                "OpenPrice = VALUES(OpenPrice)" +
                ", HighPrice = VALUES(HighPrice)" +
                ", LowPrice = VALUES(LowPrice)" +
                ", ClosePrice = VALUES(ClosePrice)" +
                ", TradeVolume = VALUES(TradeVolume);";

        try {
            dh.executeCommand(statement);
        } catch (Exception e) {
            System.err.println(e.getMessage() + " " + statement);
        }
    }
}
