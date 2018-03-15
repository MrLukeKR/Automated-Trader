package Utility;

import Default.DatabaseHandler;

import java.sql.SQLException;
import java.util.ArrayList;

public class TradingUtils {
    static private DatabaseHandler databaseHandler = null;

    static public void setDatabaseHandler(DatabaseHandler dh) {
        databaseHandler = dh;
    }

    static public void sellStock(String stock, int amount, boolean automated) throws SQLException {
        float cost = Float.parseFloat(databaseHandler.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
        float totalCost = cost * amount;
        int available = getHeldStocks(stock);
        int auto = 0;

        if (automated)
            auto = 1;

        if (amount <= available) {
            databaseHandler.executeCommand("INSERT INTO banktransactions(Amount, Type) VALUES (" + totalCost + ",'TRADE')");
            databaseHandler.executeCommand("INSERT INTO tradetransactions(Type,Symbol,Volume,Price, Automated) VALUES ('SELL'," +
                    "'" + stock + "'," +
                    amount + "," +
                    cost + "," +
                    auto +
                    ");");

            String lastUpdated = databaseHandler.executeQuery("SELECT MAX(TradeDateTime) FROM intradaystockprices WHERE Symbol = '" + stock + "';").get(0);

            databaseHandler.executeCommand("UPDATE Portfolio SET Held = Held - " + amount + ", Investment = Investment - " + totalCost + ", LastUpdated = '" + lastUpdated + "' WHERE Symbol='" + stock + "';");
        }
    }

    static public boolean canSellStock(String stock, int amount) throws SQLException {
        if (amount == 0 || stock.isEmpty())
            return false;

        ArrayList<String> result = databaseHandler.executeQuery("SELECT COALESCE(Held,0) FROM portfolio WHERE Symbol = '" + stock + "';");

        if (result.isEmpty()) return false;

        double availableStocks = Double.parseDouble(result.get(0));

        return availableStocks >= amount;
    }

    static public boolean canBuyStock(String stock, int amount) throws SQLException {
        if (amount == 0 || stock.isEmpty())
            return false;

        double availableFunds = Double.parseDouble(databaseHandler.executeQuery("SELECT COALESCE(SUM(Amount),0) FROM banktransactions;").get(0)),
                stockCost = Double.parseDouble(databaseHandler.executeQuery("SELECT COALESCE(ClosePrice,0) FROM dailystockprices WHERE Symbol='" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));

        return (stockCost * amount) <= availableFunds;
    }

    static public int getHeldStocks(String stock) throws SQLException {
        return Integer.parseInt(databaseHandler.executeQuery("SELECT COALESCE(Held,0) FROM Portfolio WHERE Symbol='" + stock + "';").get(0));
    }

    static public double getTotalWorth() throws SQLException {
        float bankBalance = Float.parseFloat(databaseHandler.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));
        float stockWorth = 0;

        ArrayList<String> heldStocks = databaseHandler.executeQuery("SELECT Symbol FROM portfolio WHERE Held > 0;");

        for (String stock : heldStocks) {
            int volume = getHeldStocks(stock);
            float currPrice = Float.parseFloat(databaseHandler.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
            stockWorth += volume * currPrice;
        }

        return stockWorth + bankBalance;
    }

    static public float getStockWorth() throws SQLException {
        float potentialTotal = 0;
        ArrayList<String> heldStocks = databaseHandler.executeQuery("SELECT Symbol, Held FROM portfolio WHERE Held > 0");

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            potentialTotal += Float.parseFloat(splitStock[1]) * Float.parseFloat(databaseHandler.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + splitStock[0] + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
        }

        return potentialTotal;
    }

    static public void buyStock(String stock, int amount, boolean automated) throws SQLException {
        float cost = Float.parseFloat(databaseHandler.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
        float totalCost = cost * amount;
        float balance = Float.parseFloat(databaseHandler.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));
        int auto = 0;

        if (automated)
            auto = 1;

        if (totalCost <= balance) {
            //TODO: Allocation warning if exceeding allocation but still purchasable
            String lastUpdated = databaseHandler.executeQuery("SELECT MAX(TradeDateTime) FROM intradaystockprices WHERE Symbol = '" + stock + "';").get(0);
            databaseHandler.executeCommand("INSERT INTO portfolio (Symbol, Allocation, Held, Investment, LastUpdated) VALUES ('" + stock + "', " + totalCost + ", " + amount + ", " + totalCost + ", '" + lastUpdated + "') ON DUPLICATE KEY UPDATE Allocation = GREATEST(VALUES(Allocation), (SELECT Allocation FROM (SELECT Allocation FROM portfolio WHERE Symbol='" + stock + "') as t)), Held = Held+ VALUES(Held), Investment = Investment + VALUES(Investment), LastUpdated = VALUES(LastUpdated);");
            databaseHandler.executeCommand("INSERT INTO banktransactions(Amount, Type) VALUES (" + -totalCost + ",'TRADE')");
            databaseHandler.executeCommand("INSERT INTO tradetransactions(Type,Symbol,Volume,Price,Automated) VALUES ('BUY'," +
                    "'" + stock + "'," +
                    amount + "," +
                    cost + "," +
                    auto +
                    ");");
        }
    }

    static public double getBalance() throws SQLException {
        return Math.floor(100 * Float.parseFloat(databaseHandler.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0))) / 100;
    }
}