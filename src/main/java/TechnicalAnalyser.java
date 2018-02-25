import com.tictactec.ta.lib.Core;
import com.tictactec.ta.lib.MAType;
import com.tictactec.ta.lib.MInteger;
import com.tictactec.ta.lib.RetCode;
import javafx.scene.control.ProgressBar;

import java.sql.Date;
import java.sql.SQLException;
import java.util.*;

public class TechnicalAnalyser {
    static private final Core ta = new Core();
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
            case RSI:
                return "RSI";
            case ADX10:
                return "ADX10";
            case OBV:
                return "OBV";
            case CCI:
                return "CCI";
            case AD:
                return "AD";
            case StoOsc:
                return "StoOsc";
            case SMA20:
                return "SMA20";
            case SMA200:
                return "SMA200";
                case SMA5:
                    return "SMA5";
            case EMA5:
                return "EMA5";
            case EMA20:
                return "EMA20";
            case EMA200:
                return "EMA200";
            case WillR:
                return "WillR";
            default:
                return null;
        }
    }


    static private int technicalIndicatorToDays(TechnicalIndicator indicator) {
        switch (indicator) {
            case SMA10:
            case EMA10:
            case ADX10:
                return 10;
            case SMA5:
            case EMA5:
                return 5;
            case SMA20:
            case EMA20:
                return 20;
            case SMA200:
            case EMA200:
                return 200;
            case RSI:
            case CCI:
            case WillR:
                return 14;
        }

        return 0;
    }

    static private void sendToDatabase(String stock, String indicator, TreeMap<Date, Double> records) throws SQLException {
        ArrayList<String> result = dh.executeQuery("SELECT TradeDate FROM dailystockprices WHERE Symbol='" + stock + "' AND " + indicator + " is not null ORDER BY TradeDate DESC LIMIT 1");

        Date dateFrom = null;
        if (!result.isEmpty())
            dateFrom = Date.valueOf(result.get(0));

        for (Date key : records.keySet())
            if (result.isEmpty() || key.after(Objects.requireNonNull(dateFrom)) || key.equals(dateFrom))
                dh.addBatchCommand("UPDATE dailystockprices SET " + indicator + "=" + "'" + records.get(key) + "' WHERE Symbol = '" + stock + "' AND TradeDate = '" + key + "' AND (" + indicator + " is null OR " + indicator + "!='" + records.get(key) + "');");
    }

    static public void calculatePercentChanges(ArrayList<String> stocks) throws SQLException {
        double c = 0, t = stocks.size() - 1;
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);

        dh.setAutoCommit(false);

        for(String stock : stocks) {
            System.out.println("Calculating Close Price Percent Changes for " + stock);
            calculatePercentChanges(stock, getFromDatabase(stock, "ClosePrice"));
            Controller.updateProgress(c++, t, pb);
        }

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, t, pb);
        dh.executeBatch();
        dh.setAutoCommit(true);
        Controller.updateProgress(0, t, pb);
    }

    private static void calculatePercentChanges(String stock, TreeMap<Date, Double> records) throws SQLException {
        TreeMap<Date, Double> percentChanges = new TreeMap<>();
        ArrayList<Date> dates = new ArrayList<>(records.keySet());


        double[] percentChangeArray = new double[records.size()];
        double prevPrice = records.get(dates.get(0));

        percentChangeArray[0] = 0;


        for(int i = 1; i < percentChangeArray.length; i++){
            Date currDate = dates.get(i);
            double currPrice = records.get(currDate);

            percentChangeArray[i] = (currPrice - prevPrice)/prevPrice;
            prevPrice = currPrice;
        }

        for(int i = 0; i < percentChangeArray.length; i++)
            percentChanges.put(dates.get(i), percentChangeArray[i]);

        sendToDatabase(stock,"PercentChange",percentChanges);

    }

    static public void calculateTechnicalIndicators(ArrayList<String> stocks) throws SQLException {
        double c = 0, t = (stocks.size() * TechnicalIndicator.values().length) - 1;
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);

        for (String stock : stocks) {
            //TreeMap<Date, Double> closePrices = getFromDatabase(stock, "ClosePrice");
            TreeMap<Date, Double> closePrices = getFromDatabase(stock, "SmoothedClosePrice");
            TreeMap<Date, Double> volumes = getFromDatabase(stock, "TradeVolume");
            TreeMap<Date, Double> openPrices = getFromDatabase(stock, "OpenPrice");
            TreeMap<Date, Double> lowPrices = getFromDatabase(stock, "LowPrice");
            TreeMap<Date, Double> highPrices = getFromDatabase(stock, "HighPrice");

            dh.setAutoCommit(false);

            for (TechnicalIndicator ti : TechnicalIndicator.values()) {
                HashMap<String, TreeMap<Date, Double>> indicators = calculateTechnicalIndicator(ti, stock, openPrices, highPrices, lowPrices, closePrices, volumes, technicalIndicatorToDays(ti));
                for (String indicator : Objects.requireNonNull(indicators).keySet())
                    sendToDatabase(stock, indicator, indicators.get(indicator));
                Controller.updateProgress(c++, t, pb);
            }

            dh.executeBatch();
            dh.setAutoCommit(true);
        }

        Controller.updateProgress(0, pb);
    }

    static private TreeMap<Date, Double> getFromDatabase(String stock, String field) throws SQLException {
        ArrayList<String> temp = dh.executeQuery("SELECT TradeDate, " + field + " FROM dailystockprices WHERE Symbol='" + stock + "' ORDER BY TradeDate ASC");
        TreeMap<Date, Double> records = new TreeMap<>();

        for (String record : temp) {
            String[] splitString = record.split(",");
            double price = Double.parseDouble(splitString[1]);
            records.put(Date.valueOf(splitString[0]), price);
        }

        return records;
    }

    static private TreeMap<Date, Double> priceArrayToRecordMap(Set<Date> dates, int startInd, int length, double[] values) {
        TreeMap<Date, Double> outputValues = new TreeMap<>();
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

    private static HashMap<String, TreeMap<Date, Double>> calculateTechnicalIndicator(TechnicalIndicator indicator, String stock, TreeMap<Date, Double> openPrices, TreeMap<Date, Double> highPrices, TreeMap<Date, Double> lowPrices, TreeMap<Date, Double> closePrices, TreeMap<Date, Double> volumes, int days) {
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
            case SMA5:
            case SMA10:
            case SMA20:
            case SMA200: {
                double[] out = new double[closePrices.size()];
                rc = ta.sma(0, cPrices.length - 1, cPrices, days, begin, length, out);
                results.put(technicalIndicatorToString(indicator), priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
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
            case EMA5:
            case EMA10:
            case EMA20:
            case EMA200: {
                double[] out = new double[closePrices.size()];
                rc = ta.ema(0, cPrices.length - 1, cPrices, days, begin, length, out);
                results.put(technicalIndicatorToString(indicator), priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
                break;
            case RSI: {
                double[] out = new double[closePrices.size()];
                rc = ta.rsi(0, cPrices.length - 1, cPrices, 14, begin, length, out);
                results.put("RSI", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
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
            case CCI: {
                double[] out = new double[closePrices.size()];
                rc = ta.cci(0, cPrices.length - 1, hPrices, lPrices, cPrices, days, begin, length, out);
                results.put("CCI", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
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
            case WillR: {
                double[] out = new double[closePrices.size()];
                rc = ta.willR(0, cPrices.length - 1, hPrices, lPrices, cPrices, days, begin, length, out);
                results.put("WillR", priceArrayToRecordMap(closePrices.keySet(), begin.value, length.value, out));
            }
                break;
        }

        if (rc != null && rc == RetCode.Success) {
            return results;
        } else
            return null;
    }

    public enum TechnicalIndicator {SMA5, SMA10, SMA20, SMA200, MACD, EMA5, EMA10, EMA20, EMA200, RSI, OBV, ADX10, CCI, AD, StoOsc, WillR}
}