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

public class StockQuoteDownloader {
    static private DatabaseHandler databaseHandler = null;
    static private AlphaVantageHandler alphaVantageHandler = null;
    static private BarChartHandler barChartHandler = null;
    static private ProgressBar stockProgressBar;
    static private int c = 0;
    static private STOCK_API useAPI;
    static private boolean priceUpdating = false;

    static public void initialise(DatabaseHandler dh, AlphaVantageHandler avh, BarChartHandler bch, ProgressBar pb, STOCK_API stockApi) {
        databaseHandler = dh;
        alphaVantageHandler = avh;
        barChartHandler = bch;
        stockProgressBar = pb;
        useAPI = stockApi;

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
            } else if (i < stocks.size() - 1) symbol.append(",");
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

    static public ArrayList<String> downloadIndexHistory(String index) throws IOException {
        return downloadStockData(index, Interval.DAILY, OutputSize.FULL);
    }

    static public void downloadStockHistory(ArrayList<String> stocks, Boolean downloadDaily, Boolean downloadIntraday, Boolean forceCompact) throws InterruptedException {
        int t = stocks.size() - 1;
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockProgressBar);

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
                        e.printStackTrace();
                    }
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

    static public void updateBatchStockData(ArrayList<String> stocks, ArrayList<LiveStockRecord> records) throws SQLException, IOException {
        ArrayList<String> temp = null;
        switch (useAPI) {
            case AlphaVantage:
                temp = StockQuoteDownloader.downloadBatchStockData(stocks);
                break;
            case BarChart:
                temp = barChartHandler.downloadQuotes(stocks);
                break;
        }

        int i = 0;

        for (LiveStockRecord curr : records) {
            String record = Objects.requireNonNull(temp).get(i++);
            String[] splitString = record.split(",");
            record = record.replace(splitString[0] + ",", "");

            ArrayList<String> tempRec = new ArrayList<>();
            tempRec.add(record);

            if (splitString[0].equals(curr.getSymbol())) {
                StockRecordParser.importCurrentQuote(tempRec.get(0), curr.getSymbol());
                Main.getController().updateCurrentTask("Downloaded " + curr.getSymbol() + " current price:" + record, false, false);
                curr.updateRecord(databaseHandler);
                curr.updateChart(databaseHandler, false);
            } else
                Main.getController().updateCurrentTask("Batch download mismatch", true, true);
        }

    }

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
                            temp = barChartHandler.downloadIntradayHistory(curr.getSymbol());
                            break;
                        case AlphaVantage:
                            temp = StockQuoteDownloader.downloadStockData(curr.getSymbol(), StockQuoteDownloader.Interval.INTRADAY, StockQuoteDownloader.OutputSize.COMPACT);
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (temp != null && temp.size() > 1) {
                    try {
                        StockRecordParser.importIntradayMarketData(temp, curr.getSymbol());
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
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

    static public void updateDailyStockData(ArrayList<LiveStockRecord> records) throws InterruptedException {
        if (priceUpdating) return;
        priceUpdating = true;
        ArrayList<Thread> threads = new ArrayList<>();
        for (LiveStockRecord curr : records) {
            Thread downloadThread = new Thread(() -> {
                curr.setUpdating(true);
                ArrayList<String> temp = null;

                try {
                    temp = StockQuoteDownloader.downloadStockData(curr.getSymbol(), StockQuoteDownloader.Interval.DAILY, StockQuoteDownloader.OutputSize.COMPACT);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (temp != null && temp.size() > 1) {
                    try {
                        StockRecordParser.importDailyMarketData(temp, curr.getSymbol());
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
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