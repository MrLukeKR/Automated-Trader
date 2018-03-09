package APIHandler;

import APIHandler.AlphaVantageHandler;
import Default.Controller;
import Default.DatabaseHandler;
import Processing.StockRecordParser;
import javafx.scene.control.ProgressBar;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

public class StockQuoteDownloader {
    static private DatabaseHandler databaseHandler = null;
    static private AlphaVantageHandler alphaVantageHandler = null;
    static private ProgressBar stockProgressBar;
    static private int c = 0;

    static public void initialise(DatabaseHandler dh, AlphaVantageHandler avh, ProgressBar pb) {
        databaseHandler = dh;
        alphaVantageHandler = avh;
        stockProgressBar = pb;

        System.out.println("Initialised Stock Quote Downloader");
    }

    static public ArrayList<String> downloadBatchStockData(ArrayList<String> stocks) throws IOException {
        if (databaseHandler == null || alphaVantageHandler == null) return null;

        ArrayList<String> results = new ArrayList<>();

        String url = "https://www.alphavantage.co/query?function=BATCH_STOCK_QUOTES";StringBuilder symbol = new StringBuilder("&symbols=");
        String apiKey = "&apikey=" + alphaVantageHandler.getApiKey();
        String time = "";

        int amount = 0;

        for(int i = 0; i < stocks.size(); i++){
            symbol.append(stocks.get(i));
            if(amount++ == 99) {
                results.addAll(alphaVantageHandler.submitRequest(url + symbol + time + apiKey + "&datatype=csv"));
                symbol = new StringBuilder("&symbols=");
                amount = 0;
            }
            else if(i < stocks.size() - 1)
                symbol.append(",");
        }

        results.addAll(alphaVantageHandler.submitRequest(url + symbol + time + apiKey + "&datatype=csv"));

        ArrayList<String> cleanedResults = new ArrayList<>();

        for(String curr : results)
            if(!curr.split(",")[0].equals("symbol"))
                cleanedResults.add(curr);

        return cleanedResults;
    }

    static public ArrayList<String> downloadStockData(String stock, Interval interval, OutputSize outputSize) throws IOException {
        if (databaseHandler == null || alphaVantageHandler == null) return null;

        String url = "https://www.alphavantage.co/query?",
                dataType = "function=",
                symbol = "&symbol=" + stock,
                size = "&outputsize=",
                apiKey = "&apikey=" + alphaVantageHandler.getApiKey(),
                time = "";

        switch (interval) {
            case DAILY:
                dataType += "TIME_SERIES_DAILY";
                break;
            case INTRADAY:
                dataType += "TIME_SERIES_INTRADAY";
                time += "&interval=1min";
                break;
        }

        switch (outputSize) {
            case COMPACT:
                size += "compact";
                break;
            case FULL:
                size += "full";
                break;
        }

        return alphaVantageHandler.submitRequest(url + dataType + symbol + time + size  + apiKey + "&datatype=csv");
    }

    private static boolean isOutOfDate(String stock, Interval interval) throws SQLException {
        switch (interval) {
            case DAILY:
                return Integer.parseInt(databaseHandler.executeQuery("SELECT COUNT(*) FROM dailystockprices WHERE Symbol = '" + stock + "' AND TradeDate >= SUBDATE(CURDATE(), 100)").get(0)) == 0;
            case INTRADAY:
                return Integer.parseInt(databaseHandler.executeQuery("SELECT COUNT(*) FROM intradaystockprices WHERE Symbol = '" + stock + "' AND TradeDateTime >= DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 100 MINUTE)").get(0)) == 0;
        }
        return true;
    }

    static public void downloadStockHistory(ArrayList<String> stocks, Boolean downloadDaily, Boolean downloadIntraday, Boolean forceCompact) throws InterruptedException {
        int t = stocks.size() - 1;
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockProgressBar);

        if(downloadDaily){
            ArrayList<Thread> threads = new ArrayList<>();
        for (String symbol : stocks) {
            //if(!upToDate(symbol))
            Thread downloadThread = new Thread(() -> {
            try {
                ArrayList<String> temp;
                if (isOutOfDate(symbol, Interval.DAILY) && !forceCompact)
                    temp = downloadStockData(symbol, Interval.DAILY, OutputSize.FULL);
                else
                    temp = downloadStockData(symbol, Interval.DAILY, OutputSize.COMPACT);

                System.out.println("Downloaded quote history of " + symbol);

                if (temp != null && !temp.isEmpty())
                    StockRecordParser.importDailyMarketData(temp, symbol);

                System.out.println("Successully committed " + symbol + " history to the database!");
            } catch (Exception e) {
                e.printStackTrace();
            }
            //else
            //    System.out.println(symbol + " is up to date!");
            Controller.updateProgress(c++, t, stockProgressBar);
            });
            threads.add(downloadThread);
            downloadThread.start();
        }
            for(Thread thread : threads)
                thread.join();
        }

        Controller.updateProgress(c = 0, t, stockProgressBar);
        if(downloadIntraday){
            ArrayList<Thread> threads = new ArrayList<>();
        for (String symbol : stocks) {
            Thread downloadThread = new Thread(() -> {
                try {
                    ArrayList<String> temp;
                    System.out.println("Downloading intraday history of " + symbol + "...");
                    if (isOutOfDate(symbol, Interval.INTRADAY) && !forceCompact)
                        temp = downloadStockData(symbol, Interval.INTRADAY, OutputSize.FULL);
                    else
                        temp = downloadStockData(symbol, Interval.INTRADAY, OutputSize.COMPACT);
                    System.out.println("Downloaded intraday history of " + symbol);

                    new Thread(()-> {
                        try {
                            StockRecordParser.importIntradayMarketData(temp, symbol);
                            System.out.println("Successully committed " + symbol + " intraday history to the database!");
                        } catch (SQLException e) { e.printStackTrace(); } }).start();
                } catch (Exception e) { e.printStackTrace(); }

                Controller.updateProgress(++c, t, stockProgressBar);
            });
            threads.add(downloadThread);
            downloadThread.start();
        }
        for(Thread thread : threads)
            thread.join();

        Controller.updateProgress(0, t, stockProgressBar);
        }
    }


    static private boolean upToDate(String symbol) throws SQLException {
        String latestDate = databaseHandler.executeQuery("SELECT MAX(TradeDate) = CURRENT_DATE FROM dailystockprices WHERE Symbol='" + symbol + "'").get(0);

        return Integer.parseInt(latestDate) == 1;
    }

    public enum OutputSize {COMPACT, FULL}

    public enum Interval {DAILY, INTRADAY}

}
