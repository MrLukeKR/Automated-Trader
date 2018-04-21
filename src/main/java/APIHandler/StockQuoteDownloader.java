package APIHandler;

import Default.Controller;
import Default.DatabaseHandler;
import Default.Main;
import Processing.StockRecordParser;
import Records.LiveStockRecord;
import javafx.scene.control.ProgressBar;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.1
 */

public class StockQuoteDownloader {
    static private DatabaseHandler databaseHandler = null;
    static private AlphaVantageHandler alphaVantageHandler = null;
    static private BarChartHandler barChartHandler = null;
    static private ProgressBar stockProgressBar;
    static private int c = 0;
    static private STOCK_API useAPI;
    static private boolean priceUpdating = false;

    /**
     * Initialises the Stock Quote Downloader class
     *
     * @param dh       Stock Quote Downloader Database Handler
     * @param avh      {@link AlphaVantageHandler} (with API key initialised)
     * @param bch      {@link BarChartHandler} (with API key initialised)
     * @param pb       {@link ProgressBar} to show stock download progress
     * @param stockApi {@link STOCK_API} enum value to determine which API to use (AlphaVantage or BarChart)
     */
    static public void initialise(DatabaseHandler dh, AlphaVantageHandler avh, BarChartHandler bch, ProgressBar pb, STOCK_API stockApi) {
        databaseHandler = dh;
        alphaVantageHandler = avh;
        barChartHandler = bch;
        stockProgressBar = pb;
        useAPI = stockApi;

        System.out.println("Initialised Stock Quote Downloader");
    }

    /**
     * Downloads the latest quote data as a batch request (reduces the need for excessive API calls)
     *
     * @param stocks List of stock tickers to retrieve the quotes for (e.g. AAL, AAPL, BIIB etc.)
     * @return List of stock quote records (one per stock ticker)
     * @throws IOException Throws IOException if the request fails due to server unavailability or connection refusal
     */
    private static ArrayList<String> downloadBatchStockData(ArrayList<String> stocks) throws IOException {
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
            } else if (i < stocks.size() - 1) symbol.append(",");
        }

        results.addAll(alphaVantageHandler.submitRequest(url + symbol + time + apiKey + "&datatype=csv"));

        ArrayList<String> cleanedResults = new ArrayList<>();

        for(String curr : results)
            if(!curr.split(",")[0].equals("symbol"))
                cleanedResults.add(curr);

        return cleanedResults;
    }

    /**
     * Downloads historic and the latest daily/intradaily stock quote information
     *
     * @param stock      Stock ticker to download data for (e.g. AAL, AAPL, BIIB etc.)
     * @param interval   {@link Interval} enum value to determine if the data to be downloaded is Daily or Intraday data
     * @param outputSize {@link OutputSize} enum value to determine if the data should be downloaded in full or compacted (latest 100 values)
     * @return A list of stock price data records for the given stock
     * @throws IOException Throws IOException if the request fails due to server unavailability or connection refusal
     */
    private static ArrayList<String> downloadStockData(String stock, Interval interval, OutputSize outputSize) throws IOException {
        if (databaseHandler == null || alphaVantageHandler == null) return null;

        String url = "https://www.alphavantage.co/query?",
                dataType = "function=TIME_SERIES_" + interval.name().toUpperCase(),
                symbol = "&symbol=" + stock,
                size = "&outputsize=" + outputSize.name().toLowerCase(),
                apiKey = "&apikey=" + alphaVantageHandler.getApiKey(),
                time = (interval == Interval.INTRADAY) ? "&interval=1min" : "";

        return alphaVantageHandler.submitRequest(url + dataType + symbol + time + size + apiKey + "&datatype=csv");
    }

    /**
     * Determines whether or not a download should be performed in full or compacted, based on the latest data in the database
     *
     * @param stock    Stock ticker to check the latest date of (e.g. AAPL for Apple Inc.)
     * @param interval {@link Interval} Enum value to check either Daily or Intraday data
     * @return True if the data should be downloaded in full or False if only the latest 100 records are required
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    private static boolean isOutOfDate(String stock, Interval interval) throws SQLException {
        switch (interval) {
            case DAILY:
                return Integer.parseInt(databaseHandler.executeQuery("SELECT COUNT(*) FROM dailystockprices WHERE Symbol = '" + stock + "' AND TradeDate >= SUBDATE(CURDATE(), 100)").get(0)) == 0;
            case INTRADAY:
                return Integer.parseInt(databaseHandler.executeQuery("SELECT COUNT(*) FROM intradaystockprices WHERE Symbol = '" + stock + "' AND TradeDateTime >= DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 100 MINUTE)").get(0)) == 0;
        }
        return true;
    }


    /**
     * Downloads the entirety of an index's price history (for use in comparing an index's component stock prices with the index price)
     *
     * @param index Index ticker to download the history for (e.g. ^NDX for NASDAQ100)
     * @return A list of historic price records for an index
     * @throws IOException Throws IOException if the request fails due to server unavailability or connection refusal
     */
    static public ArrayList<String> downloadIndexHistory(String index) throws IOException {
        return downloadStockData(index, Interval.DAILY, OutputSize.FULL);
    }

    /**
     * Downloads the stock Daily and/or Intradaily price history for a given list of stock tickers
     * @param stocks List of stock tickers to download the price history for (e.g. AAL, AAPL, BIIB etc.)
     * @param downloadDaily True if daily price data is to be downloaded, false otherwise
     * @param downloadIntraday True if intraday price data is to be downloaded, false otherwise
     * @param forceCompact True if the download should ALWAYS download the latest 100 records, rather than download full history
     * @throws InterruptedException Throws InterruptedException if the sleep function is interrupted by another process
     */
    static public void downloadStockHistory(ArrayList<String> stocks, Boolean downloadDaily, Boolean downloadIntraday, Boolean forceCompact) throws InterruptedException {
        int t = stocks.size() - 1;
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockProgressBar);

        if (!downloadDaily && !downloadIntraday) return;

        if(downloadDaily){
            ArrayList<Thread> threads = new ArrayList<>();
            for (String symbol : stocks) {
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
                        e.printStackTrace(); }

                    Controller.updateProgress(c++, t, stockProgressBar);
                });
                threads.add(downloadThread);
                downloadThread.start();
            }
            for (Thread thread : threads) thread.join();
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

                        new Thread(() -> {
                            try {
                                StockRecordParser.importIntradayMarketData(temp, symbol);
                                System.out.println("Successully committed " + symbol + " intraday history to the database!");
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        }).start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Controller.updateProgress(++c, t, stockProgressBar);
                });
                threads.add(downloadThread);
                downloadThread.start();
            }

            for (Thread thread : threads) thread.join();

            Controller.updateProgress(0, t, stockProgressBar);
        }
    }

    /**
     * Downloads the latest quote for a list of stocks and updates the GUI to reflect these updates
     * @param stocks List of stock tickers to download the latest price quote for (e.g. AAL, AAPL, BIIB etc.)
     * @param records List of {@link LiveStockRecord} objects to have their GUI updated
     * @throws IOException Throws IOException if the request fails due to server unavailability or connection refusal
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void updateBatchStockData(ArrayList<String> stocks, ArrayList<LiveStockRecord> records) throws SQLException, IOException {
        ArrayList<String> temp = (useAPI == STOCK_API.AlphaVantage) ? downloadBatchStockData(stocks) : barChartHandler.downloadQuotes(stocks);

        int i = 0;

        for (LiveStockRecord curr : records) {
            String record = Objects.requireNonNull(temp).get(i++);
            String[] splitString = record.split(",");
            record = record.replace(splitString[0] + ",", "");

            if (splitString[0].equals(curr.getSymbol())) {
                StockRecordParser.importCurrentQuote(record, curr.getSymbol());
                Main.getController().updateCurrentTask("Downloaded " + curr.getSymbol() + " current price:" + record, false, false);
                curr.updateRecord(databaseHandler);
                curr.updateChart(databaseHandler, false);
            } else
                Main.getController().updateCurrentTask("Batch download mismatch", true, true);
        }
    }

    /**
     * Downloads the latest Intraday data for a list of {@link LiveStockRecord} objects and updates their GUI graphs
     * @param records List of {@link LiveStockRecord} objects, representing the data held on a given stock ticker
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     * @throws InterruptedException Throws InterruptedException if the sleep function is interrupted by another process
     */
    static public void updateIntradayStockData(ArrayList<LiveStockRecord> records) throws SQLException, InterruptedException {
        if (priceUpdating) return;

        databaseHandler.executeCommand("DELETE FROM intradaystockprices WHERE Temporary = 1");
        priceUpdating = true;
        databaseHandler.setAutoCommit(false);
        ArrayList<Thread> threads = new ArrayList<>();
        for (LiveStockRecord curr : records) {
            Thread downloadThread = new Thread(() -> {
                curr.setUpdating(true);
                ArrayList<String> temp = null;

                try {
                    switch (useAPI) {
                        case BarChart:
                            temp = barChartHandler.downloadHistory(curr.getSymbol(), true);
                            break;
                        case AlphaVantage:
                            temp = downloadStockData(curr.getSymbol(), Interval.INTRADAY, OutputSize.COMPACT);
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace(); }

                if (temp != null && temp.size() > 1) {
                    try {
                        StockRecordParser.importIntradayMarketData(temp, curr.getSymbol());
                    } catch (SQLException e) {
                        e.printStackTrace(); }

                    Main.getController().updateCurrentTask("Downloaded " + curr.getSymbol() + " 1 minute update: " + temp.get(1), false, false);
                }
                curr.setUpdating(false);
            });

            threads.add(downloadThread);
            downloadThread.start();
        }

        for (Thread thread : threads) thread.join();

        databaseHandler.executeBatch();
        databaseHandler.setAutoCommit(true);

        for (LiveStockRecord curr : records) {
            curr.updateRecord(databaseHandler);
            curr.updateChart(databaseHandler, false);
        }

        priceUpdating = false;
    }

    /**
     * Downloads the latest Daily data for a list of {@link LiveStockRecord} objects and updates their GUI to notify of download status
     * @param records List of {@link LiveStockRecord} objects, representing the data held on a given stock ticker
     * @throws InterruptedException Throws InterruptedException if the sleep function is interrupted by another process
     */
    static public void updateDailyStockData(ArrayList<LiveStockRecord> records) throws InterruptedException {
        if (priceUpdating) return;
        priceUpdating = true;
        ArrayList<Thread> threads = new ArrayList<>();
        for (LiveStockRecord curr : records) {
            Thread downloadThread = new Thread(() -> {
                curr.setUpdating(true);
                ArrayList<String> temp = null;

                try {
                    temp = downloadStockData(curr.getSymbol(), Interval.DAILY, OutputSize.COMPACT);
                } catch (Exception e) {
                    e.printStackTrace(); }

                if (temp != null && temp.size() > 1) {
                    try {
                        StockRecordParser.importDailyMarketData(temp, curr.getSymbol());
                    } catch (SQLException e) {
                        e.printStackTrace(); }

                    Main.getController().updateCurrentTask("Downloaded " + curr.getSymbol() + " current daily close price: " + temp.get(1), false, false);
                }

                curr.setUpdating(false);
            });

            threads.add(downloadThread);
            downloadThread.start();
        }

        for (Thread thread : threads) thread.join();

        priceUpdating = false;
    }

    public enum STOCK_API {AlphaVantage, BarChart}

    public enum OutputSize {COMPACT, FULL}

    public enum Interval {DAILY, INTRADAY}
}