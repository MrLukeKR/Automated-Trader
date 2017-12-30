import javafx.scene.control.ProgressBar;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

public class StockQuoteDownloader {
    static private DatabaseHandler databaseHandler = null;
    static private AlphaVantageHandler alphaVantageHandler = null;
    static private ProgressBar stockProgressBar;

    static public void initialise(DatabaseHandler dh, AlphaVantageHandler avh, ProgressBar pb) {
        databaseHandler = dh;
        alphaVantageHandler = avh;
        stockProgressBar = pb;
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
                break;
        }

        switch (outputSize) {
            case COMPACT:
                size += "compact";
                time += "&interval=1min"; //TODO: If necessary, make this alterable
                break;
            case FULL:
                size += "full";
                break;
        }

        return alphaVantageHandler.submitRequest(url + dataType + symbol + size + time + apiKey + "&datatype=csv");
    }

    static public boolean isOutOfDate(String stock, Interval interval) throws SQLException {
        switch (interval) {
            case DAILY:
                return Integer.parseInt(databaseHandler.executeQuery("SELECT COUNT(*) FROM dailystockprices WHERE Symbol = '" + stock + "' AND TradeDate >= SUBDATE(CURDATE(), 100)").get(0)) == 0;
            case INTRADAY:
                return Integer.parseInt(databaseHandler.executeQuery("SELECT COUNT(*) FROM intradaystockprices WHERE Symbol = '" + stock + "' AND TradeDateTime >= DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 100 MINUTE)").get(0)) == 0;
        }
        return true;
    }

    static public void downloadStockHistory(ArrayList<String> stocks) {
        int t = (stocks.size() * 2) - 1, c = 0;
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockProgressBar);

        for (String symbol : stocks) {
            try {
                ArrayList<String> temp;
                if (isOutOfDate(symbol, Interval.DAILY))
                    temp = downloadStockData(symbol, Interval.DAILY, OutputSize.FULL);
                else
                    temp = downloadStockData(symbol, Interval.DAILY, OutputSize.COMPACT);

                System.out.println("Downloaded quote history of " + symbol);
                StockRecordParser.importDailyMarketData(temp, symbol, databaseHandler);
                System.out.println("Successully committed " + symbol + " history to the database!");
            } catch (Exception e) {
                e.printStackTrace();
            } //TODO: Process offline (503, 500 errors) handling
            Controller.updateProgress(c++, t, stockProgressBar);
        }

        for (String symbol : stocks) {
            try {
                ArrayList<String> temp;
                if (isOutOfDate(symbol, Interval.INTRADAY))
                    temp = downloadStockData(symbol, Interval.INTRADAY, OutputSize.FULL);
                else
                    temp = downloadStockData(symbol, Interval.INTRADAY, OutputSize.COMPACT);
                System.out.println("Downloaded intraday history of " + symbol);
                StockRecordParser.importIntradayMarketData(temp, symbol, databaseHandler);
                System.out.println("Successully committed " + symbol + " intraday history to the database!");
            } catch (Exception e) {
                e.printStackTrace();
            }
            Controller.updateProgress(c++, t, stockProgressBar);
        }
    }

    public enum OutputSize {COMPACT, FULL}

    public enum Interval {DAILY, INTRADAY}
}
