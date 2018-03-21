package Utility;

import Default.Controller;
import Default.DatabaseHandler;
import Default.Main;
import javafx.scene.control.ProgressBar;

import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.TreeMap;

public class SmoothingUtils {
    private static DatabaseHandler dh;
    private static ProgressBar pb;
    private static double ALPHA;

    static public void initialise(DatabaseHandler sudh, ProgressBar supb){
        dh = sudh;
        pb = supb;
    }

    static public double calculateMSE(TreeMap<Date,Double> closePrices, TreeMap<Date,Double> smoothedPrices){
        double error = 0;

        for(Date day : closePrices.keySet())
            error += Math.pow(closePrices.get(day) - smoothedPrices.get(day), 2);

        return error / closePrices.size();
    }

    private static TreeMap<Date, Double> exponentialSmooth(TreeMap<Date, Double> closePrices, double alpha){
        TreeMap<Date, Double> smoothedPrices = new TreeMap<>();
        double[] forecasts = new double[closePrices.size()];
        ArrayList<Date> dates = new ArrayList<>(closePrices.keySet());

        forecasts[0] = closePrices.get(dates.get(0));

        for (int i = 1; i < forecasts.length; i++)
            forecasts[i] = alpha * closePrices.get(dates.get(i)) + (1-alpha) * forecasts[i-1];

        int j = 0;

        for(Date day : closePrices.keySet())
            smoothedPrices.put(day, forecasts[j++]);

        return smoothedPrices;
    }

    static public void smoothStock(String stock, double alpha) throws SQLException {
        ALPHA = alpha;

        Main.getController().updateCurrentTask("Smoothing Stock Close Prices for " + stock + "...", false, false);

        TreeMap<Date, Double> priceHistory = new TreeMap<>();
        ArrayList<String> results = dh.executeQuery("SELECT TradeDate, ClosePrice FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate ASC");

        for(String result : results) {
            String[] splitString = result.split(",");
            Date date = Date.valueOf(splitString[0]);
            double price = Double.parseDouble(splitString[1]);

            priceHistory.put(date,price);
        }

        TreeMap<Date, Double> smoothed = exponentialSmooth(priceHistory, alpha);
        priceHistory.clear();
        sendToDatabase(stock, smoothed);
    }

    static public void smoothStocks(ArrayList<String> stocks, double alpha) throws SQLException {
        double t = stocks.size()-1, c = 0;

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);

        dh.setAutoCommit(false);
        for(String stock : stocks){
            smoothStock(stock,alpha);
            Controller.updateProgress(++c, t, pb);
        }
        dh.executeBatch();
        dh.setAutoCommit(true);
        Controller.updateProgress(0, t, pb);
    }

    static private void sendToDatabase(String stock, TreeMap<Date, Double> records) throws SQLException {
        ArrayList<String> result = dh.executeQuery("SELECT TradeDate FROM dailystockprices WHERE Symbol='" + stock + "' AND SmoothedClosePrice is not null ORDER BY TradeDate DESC LIMIT 1");

        Date dateFrom = null;
        if (!result.isEmpty())
            dateFrom = Date.valueOf(result.get(0));

        for (Date key : records.keySet())
            if (result.isEmpty() || key.after(Objects.requireNonNull(dateFrom)) || key.equals(dateFrom))
                dh.addBatchCommand("UPDATE dailystockprices SET SmoothedClosePrice =" + "'" + records.get(key) + "' WHERE Symbol = '" + stock + "' AND TradeDate = '" + key + "' AND (SmoothedClosePrice is null OR SmoothedClosePrice !='" + records.get(key) + "');");
    }

    static public double getAlpha(){
        return ALPHA;
    }
}
