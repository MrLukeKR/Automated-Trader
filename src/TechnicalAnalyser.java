import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MAType;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;
import javafx.scene.control.ProgressBar;

import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;

public class TechnicalAnalyser {
    static private Core ta = new Core();
    static private DatabaseHandler dh;
    static private ProgressBar pb;

    static public void initialise(DatabaseHandler tadh, ProgressBar pb) {
        dh = tadh;
        TechnicalAnalyser.pb = pb;

        System.out.println("Initialised Technical Analyser");
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
            case CCI10:
                return "CCI10";
            case AD:
                return "AD";
            case StoOsc:
                return "StoOsc";
            default:
                return null;
        }
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
            case CCI10:
                return 10;
        }

        return 0;
    }

    static private void sendToDatabase(String stock, String indicator, TreeMap<Date, Double> records) throws SQLException {
        dh.setAutoCommit(false);

        ArrayList<String> result = dh.executeQuery("SELECT TradeDate FROM dailystockprices WHERE Symbol='" + stock + "' AND " + indicator + " is not null ORDER BY TradeDate DESC LIMIT 1");

        Date dateFrom = null;
        if (!result.isEmpty())
            dateFrom = Date.valueOf(result.get(0));

        for (Date key : records.keySet()) {
            if (result.isEmpty() || key.after(dateFrom) || key == dateFrom)
                dh.addBatchCommand("UPDATE dailystockprices SET " + indicator + "=" + "'" + records.get(key) + "' WHERE Symbol = '" + stock + "' AND TradeDate = '" + key + "' AND (" + indicator + " is null OR " + indicator + "!='" + records.get(key) + "');");
        }

        dh.executeBatch();
        dh.setAutoCommit(true);
    }

    static public void calculateTechnicalIndicators(ArrayList<String> stocks) throws SQLException {
        double c = 0, t = (stocks.size() * TechnicalIndicator.values().length) - 1;
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);

        for (String stock : stocks) {
            TreeMap<Date, Double> closePrices = getFromDatabase(stock, "ClosePrice");
            TreeMap<Date, Double> volumes = getFromDatabase(stock, "TradeVolume");
            TreeMap<Date, Double> openPrices = getFromDatabase(stock, "OpenPrice");
            TreeMap<Date, Double> lowPrices = getFromDatabase(stock, "LowPrice");
            TreeMap<Date, Double> highPrices = getFromDatabase(stock, "HighPrice");

            for (TechnicalIndicator ti : TechnicalIndicator.values()) {
                HashMap<String, TreeMap<Date, Double>> indicators = calculateTechnicalIndicator(ti, stock, openPrices, highPrices, lowPrices, closePrices, volumes, technicalIndicatorToDays(ti));
                for (String indicator : indicators.keySet())
                    sendToDatabase(stock, indicator, indicators.get(indicator));
                Controller.updateProgress(c++, t, pb);
            }
        }

        Controller.updateProgress(0, pb);
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

    static private TreeMap<Date, Double> priceArrayToRecordMap(Set<Date> dates, int startInd, int length, double[] values) {
        TreeMap<Date, Double> outputValues = new TreeMap();
        int i = 0;
        int j = 0;

        for (Date key : dates) {
            if (j >= startInd && i < length)
                outputValues.put(key, values[i++]);
            else
                j++;
        }

        return outputValues;
    }

    static private double[] recordMapToPriceArray(TreeMap<Date, Double> map) {
        double[] prices = new double[map.size()];
        int i = 0;

        for (Date key : map.keySet())
            prices[i++] = map.get(key);

        return prices;
    }

    static public HashMap<String, TreeMap<Date, Double>> calculateTechnicalIndicator(TechnicalIndicator indicator, String stock, TreeMap<Date, Double> openPrices, TreeMap<Date, Double> highPrices, TreeMap<Date, Double> lowPrices, TreeMap<Date, Double> closePrices, TreeMap<Date, Double> volumes, int days) {
        System.out.println("Calculating " + technicalIndicatorToString(indicator) + " for " + stock + "...");

        MInteger begin = new MInteger(), length = new MInteger();

        double[] cPrices = recordMapToPriceArray(closePrices);
        double[] oPrices = recordMapToPriceArray(openPrices);
        double[] lPrices = recordMapToPriceArray(lowPrices);
        double[] hPrices = recordMapToPriceArray(highPrices);
        double[] volume = recordMapToPriceArray(volumes);

        RetCode rc = null;
        HashMap<String, TreeMap<Date, Double>> results = new HashMap<>();

        switch (indicator) {
            case SMA10: {
                double[] out = new double[closePrices.size()];
                rc = ta.sma(0, cPrices.length - 1, cPrices, days, begin, length, out);
                results.put("SMA10", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
                break;
            case MACD: {
                double[] macd = new double[closePrices.size()], macdSig = new double[closePrices.size()], macdHist = new double[closePrices.size()];
                rc = ta.macd(0, cPrices.length - 1, cPrices, 12, 26, 9, begin, length, macd, macdSig, macdHist);
                results.put("MACD", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, macd));
                results.put("MACDSig", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, macdSig));
                results.put("MACDHist", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, macdHist));
            }
                break;
            case EMA10: {
                double[] out = new double[closePrices.size()];
                rc = ta.ema(0, cPrices.length - 1, cPrices, days, begin, length, out);
                results.put("EMA10", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
                break;
            case RSI10: {
                double[] out = new double[closePrices.size()];
                rc = ta.rsi(0, cPrices.length - 1, cPrices, days, begin, length, out);
                results.put("RSI10", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
                break;
            case OBV: {
                double[] out = new double[closePrices.size()];
                rc = ta.obv(0, cPrices.length - 1, cPrices, volume, begin, length, out);
                results.put("OBV", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
                break;
            case ADX10: {
                double[] out = new double[closePrices.size()];
                rc = ta.adx(0, cPrices.length - 1, hPrices, lPrices, cPrices, days, begin, length, out);
                results.put("ADX10", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
                break;
            case CCI10: {
                double[] out = new double[closePrices.size()];
                rc = ta.cci(0, cPrices.length - 1, hPrices, lPrices, cPrices, days, begin, length, out);
                results.put("CCI10", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
                break;
            case AD: {
                double[] out = new double[closePrices.size()];
                rc = ta.ad(0, cPrices.length - 1, hPrices, lPrices, cPrices, volume, begin, length, out);
                results.put("AD", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
            break;
            case StoOsc: {
                double[] slowK = new double[closePrices.size()], slowD = new double[closePrices.size()];
                rc = ta.stoch(0, cPrices.length - 1, hPrices, lPrices, cPrices, 5, 3, MAType.Sma, 3, MAType.Sma, begin, length, slowK, slowD);
                results.put("StoOscSlowD", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, slowD));
                results.put("StoOscSlowK", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, slowK));
            }
                break;
        }

        if (rc != null && rc == RetCode.Success) {
            return results;
        } else
            return null;
    }

    public enum TechnicalIndicator {SMA10, MACD, EMA10, RSI10, OBV, ADX10, CCI10, AD, StoOsc}
}