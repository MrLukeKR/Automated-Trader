package Utility;

import Default.DatabaseHandler;
import Default.Main;
import Prediction.StockPredictor;

import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.7
 */

public class TradingUtils {
    static private DatabaseHandler databaseHandler = null;

    /**
     * Passes a new database handler to the class, preventing deadlocks from occurring when accesing the database
     *
     * @param dh Trading Utils Database Handler
     */
    static public void setDatabaseHandler(DatabaseHandler dh) {
        databaseHandler = dh;
    }

    /**
     * Sells a stock, given its ticker and an amount to sell
     *
     * @param stock     Stock ticker to consider (e.g. AAPL for Apple Inc.)
     * @param amount    Quantity of the stock to sell
     * @param automated True if this sale was done by the automated trader, False if it was manual
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void sellStock(String stock, int amount, boolean automated) throws SQLException {
        float cost = Float.parseFloat(databaseHandler.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
        float totalCost = cost * amount;
        int longtermInvestments = Integer.parseInt(databaseHandler.executeQuery("SELECT COALESCE(SUM(Amount), 0) FROM investments WHERE Symbol='" + stock + "'").get(0));
        int available = getHeldStocks(stock) - longtermInvestments;
        int auto = 0;

        if (automated) auto = 1;

        if (amount <= available) {
            databaseHandler.executeCommand("INSERT INTO banktransactions(Amount, Type) VALUES (" + totalCost + ",'TRADE')");
            databaseHandler.executeCommand("INSERT INTO tradetransactions(Type,Symbol,Volume,Price, Automated) VALUES ('SELL'," +
                    "'" + stock + "'," +
                    amount + "," +
                    cost + "," +
                    auto +
                    ");");

            String lastUpdated = databaseHandler.executeQuery("SELECT MAX(TradeDateTime) FROM intradaystockprices WHERE Symbol = '" + stock + "';").get(0);

            databaseHandler.executeCommand("UPDATE portfolio SET Held = Held - " + amount + ", Investment = Investment - " + totalCost + ", LastUpdated = '" + lastUpdated + "' WHERE Symbol='" + stock + "';");
        }
    }

    /**
     * Determines whether or not a sale can be made, based on the amount of currently held shares
     * @param stock Stock ticker to consider (e.g. AAPL for Apple Inc.)
     * @param amount Quantity of stock to sell
     * @return True if the stock can be sold in this quantity, False otherwise
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public boolean canSellStock(String stock, int amount) throws SQLException {
        if (amount == 0 || stock.isEmpty()) return false;

        ArrayList<String> result = databaseHandler.executeQuery("SELECT COALESCE(Held, 0) FROM portfolio WHERE Symbol = '" + stock + "';");

        if (result.isEmpty()) return false;

        int longtermInvestments = Integer.parseInt(databaseHandler.executeQuery("SELECT COALESCE(SUM(Amount), 0) FROM investments WHERE Symbol='" + stock + "'").get(0));
        int availableStocks = Integer.parseInt(result.get(0)) - longtermInvestments;

        return availableStocks >= amount;
    }

    /**
     * Determines whether or not a purchase can be maade, based on the cost of the stock and the current balance
     * @param stock Stock ticker to consider (e.g. AAPL for Apple Inc.)
     * @param amount Quantity of stock to buy
     * @return True if the stock can be bought in this quantity, False otherwise
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public boolean canBuyStock(String stock, int amount) throws SQLException {
        if (amount == 0 || stock.isEmpty())
            return false;

        double availableFunds = Double.parseDouble(databaseHandler.executeQuery("SELECT COALESCE(SUM(Amount),0) FROM banktransactions;").get(0)),
                stockCost = Double.parseDouble(databaseHandler.executeQuery("SELECT COALESCE(ClosePrice,0) FROM dailystockprices WHERE Symbol='" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));

        return (stockCost * amount) <= availableFunds;
    }

    /**
     * Retrieves the number of stocks owned for a given stock
     *
     * @param stock Stock ticker to consider (e.g. AAPL for Apple Inc.)
     * @return Number of shares owned for this stock
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    private static int getHeldStocks(String stock) throws SQLException {
        return Integer.parseInt(databaseHandler.executeQuery("SELECT COALESCE(Held,0) FROM portfolio WHERE Symbol='" + stock + "';").get(0));
    }

    /**
     * Retrieves the total worth of all held stocks, as well as any free funds available
     * @return Total worth of stocks + "bank balance"
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public double getTotalWorth() throws SQLException {
        float bankBalance = Float.parseFloat(databaseHandler.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));
        float stockWorth = 0;

        ArrayList<String> heldStocks = databaseHandler.executeQuery("SELECT Symbol FROM portfolio WHERE Held > 0;");

        for (String stock : heldStocks) {
            int volume = getHeldStocks(stock);
            float currPrice = Float.parseFloat(databaseHandler.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));
            stockWorth += volume * currPrice;
        }

        return stockWorth + bankBalance;
    }

    /**
     * Retrieves the worth of all held stocks
     * @return Total worth of stocks
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public float getStockWorth() throws SQLException {
        float potentialTotal = 0;
        ArrayList<String> heldStocks = databaseHandler.executeQuery("SELECT Symbol, Held FROM portfolio WHERE Held > 0");

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            potentialTotal += Float.parseFloat(splitStock[1]) * Float.parseFloat(databaseHandler.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + splitStock[0] + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
        }

        return potentialTotal;
    }

    /**
     * Buys a stock, given its ticker and an amount to buy
     * @param stock Stock ticker to consider (e.g. AAPL for Apple Inc.)
     * @param amount Quantity of the stock to buy
     * @param investmentPeriod Number of days to hold the stock for
     * @param automated True if this purchase was done by the automated trader, False if it was manual
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void buyStock(String stock, int amount, int investmentPeriod, boolean automated) throws SQLException {
        float cost = Float.parseFloat(databaseHandler.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDateTime DESC LIMIT 1;").get(0));
        float totalCost = cost * amount;
        float balance = Float.parseFloat(databaseHandler.executeQuery("SELECT SUM(Amount) FROM banktransactions;").get(0));

        if (totalCost > balance || amount == 0) return;

        //If the investment period is not 0, treat it as a dated investment (i.e. disallow sale until a given date)
        if (investmentPeriod > 0)
            databaseHandler.executeCommand("INSERT INTO investments(Symbol, Amount, EndDate, Period) VALUES ('" + stock + "', " + amount + ", DATE_ADD(CURRENT_DATE, INTERVAL " + investmentPeriod + " DAY), " + investmentPeriod + ");");

        String lastUpdated = databaseHandler.executeQuery("SELECT MAX(TradeDateTime) FROM intradaystockprices WHERE Symbol = '" + stock + "';").get(0);
        databaseHandler.executeCommand("INSERT INTO portfolio (Symbol, Allocation, Held, Investment, LastUpdated) VALUES ('" + stock + "', " + totalCost + ", " + amount + ", " + totalCost + ", '" + lastUpdated + "') ON DUPLICATE KEY UPDATE Allocation = GREATEST(VALUES(Allocation), (SELECT Allocation FROM (SELECT Allocation FROM portfolio WHERE Symbol='" + stock + "') as t)), Held = Held+ VALUES(Held), Investment = Investment + VALUES(Investment), LastUpdated = VALUES(LastUpdated);");
        databaseHandler.executeCommand("INSERT INTO banktransactions(Amount, Type) VALUES (" + -totalCost + ",'TRADE');");
        databaseHandler.executeCommand("INSERT INTO tradetransactions(Type,Symbol,Volume,Price,Automated) VALUES ('BUY'," +
                "'" + stock + "'," +
                amount + "," +
                cost + "," +
                (automated ? 1 : 0) +
                ");");
    }

    /**
     * Automatically trades a set of stocks, given a list of trading days
     * @param stocks List of stock tickers to consider
     * @param dayArray List of trading periods
     * @throws Exception Including SQLException
     */
    public static void autoTrade(ArrayList<String> stocks, int[] dayArray) throws Exception {
        Main.getController().updateCurrentTask("Auto-Trading...", false, false);
        ArrayList<String> portfolio = databaseHandler.executeQuery("SELECT * FROM portfolio ORDER BY Allocation DESC;");

        ArrayList<String> expiredInvestments = databaseHandler.executeQuery("SELECT ID, Symbol, Period, Amount FROM investments WHERE EndDate <= CURRENT_DATE;");

        for (String investment : expiredInvestments) {
            String[] splitInvestment = investment.split(",");

            if (StockPredictor.predictStock(stocks, splitInvestment[1], Integer.parseInt(splitInvestment[2])))
                databaseHandler.executeCommand("UPDATE investments SET EndDate = DATE_ADD(CURRENT_DATE, INTERVAL " + splitInvestment[2] + " DAY) WHERE ID = " + splitInvestment[0] + ";");
            else {
                databaseHandler.executeCommand("DELETE FROM investments WHERE ID = " + splitInvestment[0]);
                Main.getController().updateCurrentTask("> AUTOMATED TRADER: SELLING " + splitInvestment[3] + " " + splitInvestment[1], false, true);
                TradingUtils.sellStock(splitInvestment[1], Integer.parseInt(splitInvestment[3]), true);
            }
        }

        for (String record : portfolio) {
            double balance = Double.parseDouble(databaseHandler.executeQuery("SELECT COALESCE(SUM(Amount),0) FROM banktransactions").get(0));
            String[] splitString = record.split(",");
            String symbol = splitString[0];
            double allocation = Double.parseDouble(splitString[1]) - Double.parseDouble(splitString[3]);
            double currentPrice = Double.parseDouble(databaseHandler.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + symbol + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
            int splitAmount = 0;

            for (int currDay : dayArray)
                if (StockPredictor.predictStock(stocks, symbol, currDay)) splitAmount++;

            if (splitAmount == 0) return;
            int buyAmount = (int) Math.floor((int) Math.floor(allocation / currentPrice) / splitAmount);

            double allocationRemaining = Double.parseDouble(portfolio.get(1)) - Double.parseDouble(portfolio.get(3));

            if (buyAmount >= splitAmount) {
                for (int day : dayArray)
                    if (StockPredictor.predictStock(stocks, symbol, day) && buyAmount * currentPrice <= balance && allocationRemaining - (buyAmount * currentPrice) >= 0) {
                        Main.getController().updateCurrentTask("> AUTOMATED TRADER: BUYING " + buyAmount + " " + symbol, false, true);

                        TradingUtils.buyStock(symbol, buyAmount, day, true);
                    }
            }
        }

        Main.getController().updateGUI();
    }

    /**
     * Sells all held stocks
     * @param automated True if this purchase was done by the automated trader, False if it was manual
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     * @throws ParseException Throws ParseException if the GUI can't be updated
     */
    static public void sellAllStock(boolean automated) throws SQLException, ParseException {
        ArrayList<String> stocks = databaseHandler.executeQuery("SELECT Symbol, Held FROM portfolio WHERE Held > 0");

        for (String stock : stocks) {
            String[] splitStock = stock.split(",");
            TradingUtils.sellStock(splitStock[0], Integer.parseInt(splitStock[1]), automated);
        }

        databaseHandler.executeCommand("DELETE FROM portfolio;");
        Main.getController().updateGUI();
    }

    /**
     * Gets the current "bank balance"
     * @return Funds available within the "bank"
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public double getBalance() throws SQLException {
        return Math.floor(100 * Float.parseFloat(databaseHandler.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0))) / 100;
    }
}