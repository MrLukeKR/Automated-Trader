import java.sql.SQLException;
import java.util.ArrayList;

public class TechnicalAnalyser {
    static private DatabaseHandler dh;

    static public void setDBHandler(DatabaseHandler db) {
        dh = db;
    }

    static public void processUncalculated() {
        System.out.println("Processing uncalculated technical indicators (This could take a while)");
        try {
            ArrayList<String> uncalculatedRecords =
                    dh.executeQuery("SELECT dailystockprices.Symbol, dailystockprices.TradeDate FROM dailystockprices " +
                            "LEFT JOIN dailytechnicalindicators ON dailystockprices.TradeDate = dailytechnicalindicators.TradeDate " +
                            "WHERE dailytechnicalindicators.TradeDate IS NULL AND dailystockprices.TradeDate < CURDATE()");

            for (String record : uncalculatedRecords) {
                String symbol = record.split(",")[0];
                String date = record.split(",")[1];
                float EMA12 = calculateEMA12(symbol, date);
                float EMA26 = calculateEMA26(symbol, date);
                float MACD = calculateMACD(symbol, date);
                float RSI = calculateRSI(symbol, date);

                if (RSI >= 0)
                    dh.executeCommand("INSERT INTO dailytechnicalindicators (Symbol, TradeDate, MACD, RSI, EMA12, EMA26) VALUES ('" + symbol + "','" + date + "','" + MACD + "','" + RSI + "','" + EMA12 + "','" + EMA26 + "');");
                else
                    dh.executeCommand("INSERT INTO dailytechnicalindicators (Symbol, TradeDate, MACD, EMA12, EMA26) VALUES ('" + symbol + "','" + date + "','" + MACD + "','" + EMA12 + "','" + EMA26 + "');");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static public float calculateSMA(String stock, String date, int days) {
        float SMA = 0;

        ArrayList<String> records = null;
        try {
            records = dh.executeQuery("SELECT ClosePrice FROM (SELECT * FROM dailystockprices WHERE TradeDate <= '" + date + "' AND Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT " + days + ") AS t ORDER BY t.TradeDate ASC;");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for (int i = Math.max(0, records.size() - days); i < records.size(); i++) {
            SMA += Float.parseFloat(records.get(i));
        }

        return SMA / days;
    }

    static public float calculateEMA12(String stock, String date) {
        ArrayList<String> cPrice = null;
        ArrayList<String> pEMA = null;
        try {
            cPrice = dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE TradeDate = '" + date + "' AND Symbol = '" + stock + "';");
            pEMA = dh.executeQuery("SELECT EMA12 FROM dailytechnicalindicators WHERE TradeDate < '" + date + "' AND Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 1;");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        //TODO: Validation of arrays

        float price = Float.parseFloat(cPrice.get(0));

        if (!pEMA.isEmpty())
            return calculateEMA(Float.parseFloat(pEMA.get(0)), price, 12);
        else
            return calculateEMA(stock, date, 12);
    }

    static public float calculateEMA26(String stock, String date) {
        ArrayList<String> cPrice = null;
        ArrayList<String> pEMA = null;
        try {
            cPrice = dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE TradeDate = '" + date + "' AND Symbol = '" + stock + "';");
            pEMA = dh.executeQuery("SELECT EMA26 FROM dailytechnicalindicators WHERE TradeDate < '" + date + "' AND Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 1;");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        //TODO: Validation of arrays

        float price = Float.parseFloat(cPrice.get(0));

        if (!pEMA.isEmpty())
            return calculateEMA(Float.parseFloat(pEMA.get(0)), price, 26);
        else
            return calculateEMA(stock, date, 26);
    }

    static public float calculateEMA(float prevEMA, float currPrice, float days) {
        float multiplier = 2.0f / (days + 1.0f);
        return currPrice * multiplier + prevEMA * (1 - multiplier);
    }

    static public float calculateEMA(String stock, String date, int days) {
        float EMA = 0;

        ArrayList<String> records = null;
        try {
            records = dh.executeQuery("SELECT ClosePrice, TradeDate FROM (SELECT * FROM dailystockprices WHERE TradeDate <= '" + date + "' AND Symbol = '" + stock + "' ORDER BY TradeDate ASC) AS t ORDER BY t.TradeDate ASC;");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (records.size() < days)
            return calculateSMA(stock, date, days);

        String cDate, cPrice;
        int count = 1;
        for (String record : records) {
            if (records.size() == days) {
                cDate = record.split(",")[1];
                EMA = calculateSMA(stock, cDate, days);
            } else if (count > days) {
                cPrice = record.split(",")[0];
                EMA = calculateEMA(EMA, Float.parseFloat(cPrice), days);
            }
            count++;
        }

        return EMA;
    }

    static public float calculateMACD(String stock, String date) {
        try {
            ArrayList<String> records = dh.executeQuery("SELECT ClosePrice FROM (SELECT * FROM dailystockprices WHERE TradeDate <= '" + date + "' AND Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 26) AS t ORDER BY t.TradeDate ASC;");

            float EMA12 = calculateEMA12(stock, date);
            float EMA26 = calculateEMA26(stock, date);

            return EMA12 - EMA26;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    static public float calculateRSI(String stock, String date) {
        try {
            ArrayList<String> records = dh.executeQuery("SELECT ClosePrice FROM (SELECT * FROM dailystockprices WHERE TradeDate <= '" + date + "' AND Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 28) AS t ORDER BY t.TradeDate ASC;");

            if (records.size() < 14)
                return -1;

            int count = 0;
            float gain = 0;
            float loss = 0;
            float amount;

            float prevPrice = 0;

            for (String record : records) {
                if (count >= 1 && count < 14) {
                    amount = Float.parseFloat(record) - prevPrice;
                    if (amount >= 0)
                        gain += amount;
                    else
                        loss += Math.abs(amount);
                }

                if (count == 14) {
                    gain /= 14.0;
                    loss /= 14.0;
                }

                if (count >= 14) {
                    amount = Float.parseFloat(record) - prevPrice;
                    if (amount >= 0) {
                        gain = (gain * 13 + amount) / 14;
                        loss = (loss * 13 + 0) / 14;
                    } else {
                        gain = (gain * 13 + 0) / 14;
                        loss = (loss * 13 + Math.abs(amount)) / 14;
                    }
                }
                prevPrice = Float.parseFloat(record);
                count++;
            }
            if (loss == 0)
                return 0;
            return 100 - (100 / (1 + (gain / loss)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }
}
