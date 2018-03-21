package APIHandler;

import Default.Controller;
import Default.DatabaseHandler;
import Default.Main;
import Processing.StockRecordParser;
import javafx.scene.control.ProgressBar;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

public class BarChartHandler {
    private String apiKey;

    private final int CALL_LIMIT = 1;
    private DatabaseHandler dh;
    private ProgressBar pb;
    private static final String IS_NUMERIC = "[-+]?\\d*\\.?\\d+";

    public void init(String apiKey, DatabaseHandler bcdh, ProgressBar bcpb){
        this.apiKey = apiKey;
        dh = bcdh;
        pb = bcpb;
    }

    private int getCurrentCalls() throws SQLException {
        ArrayList<String> sCalls = dh.executeQuery("SELECT Calls FROM apicalls WHERE Date = CURDATE() AND Name='INTRINIO';");

        if(!sCalls.isEmpty())
            return Integer.parseInt(sCalls.get(0));

        return 0;
    }

    private boolean isOverLimit(int callsToPerform) throws SQLException {
        int limit = Integer.parseInt(dh.executeQuery("SELECT DailyLimit FROM apimanagement WHERE Name='BarChart';").get(0));

        return (callsToPerform + getCurrentCalls()) > limit;
    }

    private void sendToDatabase(ArrayList<String> values, String stock, boolean intraday) throws SQLException {
        ArrayList<String> reducedResult = new ArrayList<>();
        for(String value : values){
            if(value.contains("You have reached the maximum")) return; //TODO: Insert value into API call manager
            String[] splitString = value.replace("\"","").split(",");


            if(splitString.length < 8) {
                Main.getController().updateCurrentTask("Erroneous quote: " + value, true, false);
                return;
            }

            if(splitString[3].matches(IS_NUMERIC)) {
                String[] formattedDate = splitString[1].split("T");
                reducedResult.add(formattedDate[0] + " " + formattedDate[1].split("-")[0] + "," + splitString[3] + "," + splitString[4] + "," + splitString[5] + "," + splitString[6] + "," + splitString[7]);
            }
        }

        if(intraday)
            StockRecordParser.importIntradayMarketData(reducedResult, stock);
        else
            StockRecordParser.importDailyMarketData(reducedResult, stock);
        Main.getController().updateCurrentTask("Successfully committed Intraday history for " + stock + " to the database!", false, false);
    }

    public void downloadDailyHistory(ArrayList<String> stocks) throws IOException, SQLException {
        double t = stocks.size() - 1, c = 0;
        if (isOverLimit(0)) return;

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);
        for(String stock:stocks) {
            Main.getController().updateCurrentTask("Downloading Daily History for " + stock, false, false);
            sendToDatabase(downloadDailyHistory(stock), stock,false);
            Controller.updateProgress(++c,t,pb);
        }
        Controller.updateProgress(0, pb);
    }

    public void downloadIntradayHistory(ArrayList<String> stocks) throws IOException, SQLException {
        double t = stocks.size() - 1, c = 0;
        if (isOverLimit(0)) return;

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);
        for(String stock:stocks) {
            Main.getController().updateCurrentTask("Downloading Intraday History for " + stock, false, false);
            sendToDatabase(downloadIntradayHistory(stock), stock, true);
            Controller.updateProgress(++c,t,pb);
        }
        Controller.updateProgress(0, pb);
    }

    public ArrayList<String> downloadQuotes(ArrayList<String> stocks) throws IOException, SQLException {
        ArrayList<String> results = new ArrayList<>();
        int amount = 0;

        StringBuilder symbols= new StringBuilder();

        String url = "https://marketdata.websol.barchart.com/getQuote.csv?apikey=" + apiKey + "&symbols=";

        for(int i = 0; i < stocks.size(); i++){
            symbols.append(stocks.get(i));
            if(amount++ == 99) {
                results.addAll(submitRequest(url + symbols + "&mode=R"));
                symbols = new StringBuilder("&symbols=");
                amount = 0;
            } else if(i < stocks.size() - 1)
                symbols.append(",");
        }

        results.addAll(submitRequest(url + symbols + "&mode=R"));

        ArrayList<String> cleanedResults = new ArrayList<>();

        for(String curr : results)
            if(!curr.split(",")[0].equals("symbol"))
                cleanedResults.add(curr);

        ArrayList<String> fixedResults = new ArrayList<>();

        for(String result : cleanedResults){
            String[] splitString = result.replace("\"","").split(",");
            String[] splitDate = splitString[7].split("T");
            String fixedDate = splitDate[0] + " " + splitDate[1].split("-")[0];
            String fixedString = splitString[0] + ",'" + fixedDate + "'," + splitString[11] + "," + splitString[12] + "," + splitString[13] + "," + splitString[6] + "," + splitString[16];
            fixedResults.add(fixedString);
        }

        return fixedResults;
    }

    public ArrayList<String> downloadDailyHistory(String stock) throws IOException, SQLException {
        String dateFrom = dh.executeQuery("SELECT COALESCE(MAX(TradeDate),'1900-01-01') FROM dailystockprices WHERE Symbol='" +  stock+ "'").get(0).replace("-","").replace(" ","").replace(":","");
        String url = "https://marketdata.websol.barchart.com/getHistory.csv?apikey=" + apiKey + "&symbol=" + stock + "&startDate=" + dateFrom + "&endDate=21000101&type=daily&order=asc";

        return submitRequest(url);
    }

    public ArrayList<String> downloadIntradayHistory(String stock) throws IOException, SQLException {
        String dateFrom = dh.executeQuery("SELECT COALESCE(DATE_SUB(MAX(TradeDateTime), INTERVAL 1 MINUTE),'2001-01-01 00:00:00') FROM intradaystockprices WHERE Symbol='" +  stock+ "'").get(0).replace("-","").replace(" ","").replace(":","");
        String url = "https://marketdata.websol.barchart.com/getHistory.csv?apikey=" + apiKey + "&symbol=" + stock + "&startDate=" + dateFrom + "&endDate=21000101&type=minutes&interval=1&order=asc";

        return submitRequest(url);
    }

    private ArrayList<String> submitRequest(String request) throws IOException, SQLException {
        int exceeded = 1;
        ArrayList<String> temp = new ArrayList<>();

        do {
            //TimeUnit.SECONDS.sleep(CALL_LIMIT);

            HttpURLConnection connection = null;
            Reader reader= null;
            URL url;
            InputStream is = null;

            try {
                url = new URL(request);
                connection = (HttpURLConnection) url.openConnection();
                is = connection.getInputStream();
                reader = new InputStreamReader(is);

                final char[] buf = new char[10240];
                int read;
                final StringBuilder sb = new StringBuilder();
                while ((read = reader.read(buf,0,buf.length)) > 0)
                    sb.append(buf, 0, read);

                String sTemp = sb.toString();

                temp.addAll(Arrays.asList(sTemp.split("\r\n")));
            }catch (Exception e){
                exceeded++;
            }finally {
                if(is!=null) is.close();
                if(connection != null) connection.disconnect();
                if(reader != null) reader.close();
            }

            dh.executeCommand("INSERT INTO apicalls VALUES('BarChart', CURDATE(), 1) ON DUPLICATE KEY UPDATE Calls = Calls +1;");
            //dh.executeCommand("INSERT INTO apicalls VALUES ('BarChart', CURDATE(), 500) ON DUPLICATE KEY UPDATE Calls = 500;"); //Incase another system uses this program, this database value doesn't get updated, in which case if an error occurs, mark the api as "limit reached"
        } while (exceeded > 1 && exceeded < 10);

        if (temp.size() <= 1)
            Main.getController().updateCurrentTask("Error with quote download", true, false);

        return temp;
    }

    public String getApiKey(){
        return apiKey;
    }
}
