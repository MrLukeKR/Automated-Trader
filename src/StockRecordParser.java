 import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
 import java.util.Arrays;
 import java.util.List;

 public class StockRecordParser {

    static public void importDailyMarketData(File csvFile, String symbol, DatabaseHandler dh) throws IOException, SQLException {
        FileReader fr = new FileReader(csvFile);
        BufferedReader br = new BufferedReader(fr);

        String curr = null;

        ArrayList<String> csvArray = new ArrayList<>();

        while((curr = br.readLine()) != null){
            String[] split = curr.split(",");
            curr = "";
            for (int i = 0; i < 5; i++)
                curr += split[i] + ",";
            curr +=split[6];
            csvArray.add(curr);
        }

        importDailyMarketData(csvArray, symbol, dh);
    }

    static public void importDailyMarketData(String csvBuffer, String symbol, DatabaseHandler dh) {
        importDailyMarketData(csvBuffer.split("\r\n"), symbol, dh);
    }

    static public void importDailyMarketData(ArrayList<String> csv, String symbol, DatabaseHandler dh) {
        importData(csv,"dailystockprices" ,symbol, dh, 6);
    }

    static public void importDailyMarketData(String[] csv, String symbol, DatabaseHandler dh) {
        List<String> temp = Arrays.asList(csv);
        importData(new ArrayList<>(temp),"dailystockprices", symbol, dh, 6);
    }

    static public void importData(ArrayList<String> csv, String table, String symbol, DatabaseHandler dh, int columns)  {
        String split[] = null;
        Boolean skippedHeader = false;

        if(csv == null) return;

        for(String curr : csv) {
            if(!skippedHeader)
                skippedHeader = true;
            else if(curr != null && curr.split(",").length == columns)
                {
                    split = curr.split(",");

                    String statement = "INSERT IGNORE INTO " + table + " VALUES("; //TODO: Update values if primary key exists but values are different

                        statement += "'" + symbol + "','" + split[0] + "'";
                        for (int i = 1; i < columns; i++)
                            statement += "," + split[i];
                        statement += ");";

                        try {
                            dh.executeCommand(statement);
                        } catch (Exception e) {
                            System.err.println(e.getMessage() + " " + statement);
                        }
            }
        }
    }

     static public void importIntradayMarketData(String[] csv, String symbol, DatabaseHandler dh) {
        List<String> temp = Arrays.asList(csv);
         importData(new ArrayList<>(temp), "intradaystockprices", symbol, dh, 6);
     }

    static public void importIntradayMarketData(ArrayList<String> csv, String symbol, DatabaseHandler dh) {
        if(csv == null || csv.isEmpty()) return;

        importData(csv, "intradaystockprices", symbol, dh, 6);
    }
}
