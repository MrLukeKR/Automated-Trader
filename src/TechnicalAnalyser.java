import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;
import javafx.scene.control.ProgressBar;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;

public class TechnicalAnalyser {
    static private PrintWriter pw;
    static private Core ta = new Core();
    static private DatabaseHandler dh;
    static private ProgressBar pb;

    static {
        try {
            pw = new PrintWriter("res/SQLTotal.sql");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    static public void initialise(DatabaseHandler tadh, ProgressBar pb) {
        dh = tadh;
        TechnicalAnalyser.pb = pb;
    }

    static private String technicalIndicatorToString(TechnicalIndicator indicator) {
        switch (indicator) {
            case SMA10:
                return "SMA10";
            case MACD:
                return "MACD";
            case EMA10:
                return "EMA10";
            case RSI10:
                return "RSI10";
            case ADX10:
                return "ADX10";
            case OBV:
                return "OBV";
        }

        return null;
    }

    static private int technicalIndicatorToDays(TechnicalIndicator indicator) {
        switch (indicator) {
            case SMA10:
                return 10;
            case EMA10:
                return 12;
            case RSI10:
                return 10;
            case ADX10:
                return 10;
        }

        return 0;
    }

    static public void calculateTechnicalIndicators(ArrayList<String> stocks) throws SQLException, FileNotFoundException {
        double c = 0, t = (stocks.size() * TechnicalIndicator.values().length) - 1;
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);

        for (String stock : stocks) {
            TreeMap<Date, Double> closePrices = getFromDatabase(stock, "ClosePrice");
            TreeMap<Date, Double> volumes = getFromDatabase(stock, "TradeVolume");
            TreeMap<Date, Double> openPrices = getFromDatabase(stock, "OpenPrice");
            TreeMap<Date, Double> lowPrices = getFromDatabase(stock, "LowPrice");
            TreeMap<Date, Double> highPrices = getFromDatabase(stock, "HighPrice");

            for (TechnicalIndicator ti : TechnicalIndicator.values()) {
                sendToDatabase(stock, technicalIndicatorToString(ti), calculateTechnicalIndicator(ti, stock, openPrices, highPrices, lowPrices, closePrices, volumes, technicalIndicatorToDays(ti)));
                Controller.updateProgress(c++, t, pb);
            }
        }
    }

    static private TreeMap<Date, Double> getFromDatabase(String stock, String field) throws SQLException {
        ArrayList<String> temp = dh.executeQuery("SELECT TradeDate, " + field + " FROM dailystockprices WHERE Symbol='" + stock + "' ORDER BY TradeDate ASC");
        TreeMap<Date, Double> records = new TreeMap();

        for (String record : temp) {
            String[] splitString = record.split(",");
            double price = Double.parseDouble(splitString[1]);
            records.put(Date.valueOf(splitString[0]), price);
        }

        return records;
    }

    static private void sendToDatabase(String stock, String indicator, TreeMap<Date, Double> records) throws SQLException {
        ArrayList<String> dates = dh.executeQuery("SELECT TradeDate FROM dailystockprices WHERE Symbol = '" + stock + "' AND " + indicator + " IS NOT NULL ORDER BY TradeDate DESC LIMIT 1");

        Date updateFrom;

        if (dates.isEmpty())
            updateFrom = Date.valueOf(dh.executeQuery("SELECT TradeDate FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate ASC LIMIT 1").get(0));
        else
            updateFrom = Date.valueOf(dates.get(0));

        for (Date key : records.keySet()) {
            if (key.getTime() >= updateFrom.getTime())
                //pw.println("UPDATE dailystockprices SET " + indicator + "=" + "'" + records.get(key) + "' WHERE Symbol = '" + stock + "' AND TradeDate = '" + key + "'");
                dh.executeCommand("UPDATE dailystockprices SET " + indicator + "=" + "'" + records.get(key) + "' WHERE Symbol = '" + stock + "' AND TradeDate = '" + key + "'");
        }
    }

    static private double[] recordMapToPriceArray(TreeMap<Date, Double> map) {
        double[] prices = new double[map.size()];
        int i = 0;

        for (Date key : map.keySet())
            prices[i++] = map.get(key);

        return prices;
    }

    static private TreeMap<Date, Double> priceArrayToRecordMap(Set<Date> dates, int startInd, int length, double[] values) {
        TreeMap<Date, Double> outputValues = new TreeMap();
        int i = 0;

        for (Date key : dates) {
            if (i >= startInd && i < length)
                outputValues.put(key, values[i]);
            i++;
        }

        return outputValues;
    }

    static public TreeMap<Date, Double> calculateTechnicalIndicator(TechnicalIndicator indicator, String stock, TreeMap<Date, Double> openPrices, TreeMap<Date, Double> highPrices, TreeMap<Date, Double> lowPrices, TreeMap<Date, Double> closePrices, TreeMap<Date, Double> volumes, int days) {
        System.out.println("Calculating " + technicalIndicatorToString(indicator) + " for " + stock + "...");

        MInteger begin = new MInteger(), length = new MInteger();
        double[] out = new double[closePrices.size()];
        double[] cPrices = recordMapToPriceArray(closePrices);
        double[] oPrices = recordMapToPriceArray(openPrices);
        double[] lPrices = recordMapToPriceArray(lowPrices);
        double[] hPrices = recordMapToPriceArray(highPrices);
        double[] volume = recordMapToPriceArray(volumes);

        RetCode rc = null;

        switch (indicator) {
            case SMA10:
                rc = ta.sma(0, cPrices.length - 1, cPrices, days, begin, length, out);
                break;
            case MACD:
                rc = ta.macd(0, cPrices.length - 1, cPrices, 12, 26, 9, begin, length, out, new double[closePrices.size()], new double[closePrices.size()]);
                break;
            case EMA10:
                rc = ta.ema(0, cPrices.length - 1, cPrices, days, begin, length, out);
                break;
            case RSI10:
                rc = ta.rsi(0, cPrices.length - 1, cPrices, days, begin, length, out);
                break;
            case OBV:
                rc = ta.obv(0, cPrices.length - 1, cPrices, volume, begin, length, out);
                break;
            case ADX10:
                rc = ta.adx(0, cPrices.length - 1, hPrices, lPrices, cPrices, days, begin, length, out);
                break;
        }

        if (rc != null && rc == RetCode.Success)
            return priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out);
        else
            return null;
    }

    public enum TechnicalIndicator {SMA10, MACD, EMA10, RSI10, OBV, ADX10}
}