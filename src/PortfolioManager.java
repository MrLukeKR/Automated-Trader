import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

public class PortfolioManager {

    static DatabaseHandler dh;

    public static void initialise(DatabaseHandler pmdh) {
        dh = pmdh;
    }

    static private ArrayList<String> getPrices(String symbol) throws SQLException {
        return dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + symbol + "' AND ClosePrice is not null AND ClosePrice != 0 ORDER BY TradeDate ASC");
    }

    static public double calculateVariance(String stock, int amountOfDays) throws SQLException {
        double average = calculateAverageReturn(stock, amountOfDays);
        double total = 0;

        ArrayList<Double> returns = calculateReturns(stock, amountOfDays);
        ArrayList<Double> differences = new ArrayList<>();

        for (Double value : returns) {
            double difference = (value - average) * (value - average);
            differences.add(difference);
            total += difference;
        }

        return total / (differences.size() - 1); // -1 because this is sample based, not population based
    }

    static public ArrayList<Double> calculateReturns(String stock, int amountOfDays) throws SQLException {
        ArrayList<String> results = getPrices(stock);
        ArrayList<Double> returns = new ArrayList<>();

        for (int i = 0; i < results.size() - amountOfDays; i++) {
            double startVal = Double.valueOf(results.get(i)), endVal = Double.valueOf(results.get(i + amountOfDays));
            double ret = (endVal - startVal) / startVal;

            returns.add(ret);
        }

        return returns;
    }

    static public double calculateAverageReturn(String stock, int amountOfDays) throws SQLException {
        ArrayList<Double> returns = calculateReturns(stock, amountOfDays);
        double total = 0;

        for (int i = 0; i < returns.size(); i++)
            total += returns.get(i);

        return total / returns.size();
    }

    static public Map<String, Integer> optimisePortfolio() throws SQLException {
        Map<Double, String> averageReturns1d = new TreeMap<>();
        Map<Double, String> averageReturns5d = new TreeMap<>();
        Map<Double, String> averageReturns30d = new TreeMap<>();
        Map<Double, String> averageReturns200d = new TreeMap<>();
        Map<Double, String> averageReturns260d = new TreeMap<>();

        Map<Double, String> variance1d = new TreeMap<>();
        Map<Double, String> variance5d = new TreeMap<>();
        Map<Double, String> variance30d = new TreeMap<>();
        Map<Double, String> variance200d = new TreeMap<>();
        Map<Double, String> variance260d = new TreeMap<>();

        ArrayList<String> stocks = dh.executeQuery("SELECT Symbol FROM indices ORDER BY Symbol ASC");

        for (String stock : stocks) {
            averageReturns1d.put(calculateAverageReturn(stock, 1), stock);
            averageReturns5d.put(calculateAverageReturn(stock, 5), stock);
            averageReturns30d.put(calculateAverageReturn(stock, 30), stock);
            averageReturns200d.put(calculateAverageReturn(stock, 200), stock);
            averageReturns260d.put(calculateAverageReturn(stock, 260), stock);

            variance1d.put(calculateVariance(stock, 1), stock);
            variance5d.put(calculateVariance(stock, 5), stock);
            variance30d.put(calculateVariance(stock, 30), stock);
            variance200d.put(calculateVariance(stock, 200), stock);
            variance260d.put(calculateVariance(stock, 260), stock);
        }

        return null;
    }
}
