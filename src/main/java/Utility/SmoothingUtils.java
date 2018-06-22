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

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.5
 */

public class SmoothingUtils {
    private static DatabaseHandler dh;
    private static ProgressBar pb;
    private static double ALPHA;

    /**
     * Initialises the Smoothing Utils class, given a Database Handler to prevent deadlocks when accessing the database and a progress bar to visualise the progress of various methods
     *
     * @param sudh Smoothing Utils Database handler
     * @param supb Smoothing Utils Progress Bar
     */
    static public void initialise(DatabaseHandler sudh, ProgressBar supb){
        dh = sudh;
        pb = supb;
    }

    /**
     * Exponentially Smooths a list of price data, giving more importance to more recent data
     * @param closePrices List of close prices for a given stock
     * @param alpha The smoothing factor to apply to the smoothing process (lower is more intense smoothing)
     * @return A list of smoothed price data
     */
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

    /**
     * Performs smoothing of a given stock, using a given smoothing factor
     *
     * @param stock Stock ticker to smooth the price data of (e.g. AAPL for Apple Inc.)
     * @param alpha The smoothing factor to apply to the smoothing process (lower is more intense smoothing)
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    private static void smoothStock(String stock, double alpha) throws SQLException {
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

    /**
     * Performs smoothing of a given list of stocks, using a given smoothing factor
     * @param stocks List of stock tickers to smooth the price data of (e.g. AAL, AAPL, BIIB etc.)
     * @param alpha The smoothing factor to apply to the smoothing process (lower is more intense smoothing)
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
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

    /**
     * Sends the price information to the database
     * @param stock Stock ticker to associate the smoothed price data with
     * @param records Smoothed price data
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static private void sendToDatabase(String stock, TreeMap<Date, Double> records) throws SQLException {
        ArrayList<String> result = dh.executeQuery("SELECT TradeDate FROM dailystockprices WHERE Symbol='" + stock + "' AND SmoothedClosePrice is not null ORDER BY TradeDate DESC LIMIT 1");

        Date dateFrom = null;
        if (!result.isEmpty())
            dateFrom = Date.valueOf(result.get(0));

        for (Date key : records.keySet())
            if (result.isEmpty() || key.after(Objects.requireNonNull(dateFrom)) || key.equals(dateFrom))
                dh.addBatchCommand("UPDATE dailystockprices SET SmoothedClosePrice =" + "'" + records.get(key) + "' WHERE Symbol = '" + stock + "' AND TradeDate = '" + key + "' AND (SmoothedClosePrice is null OR SmoothedClosePrice !='" + records.get(key) + "');");
    }

    /**
     * Retrieves the current smoothing factor
     * @return Smoothing Factor
     */
    static public double getAlpha(){
        return ALPHA;
    }
}
