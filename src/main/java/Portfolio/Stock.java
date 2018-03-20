package Portfolio;

import java.sql.Date;
import java.util.ArrayList;
import java.util.TreeMap;

public class Stock {
    private String name;
    private TreeMap<Date, Double> prices = new TreeMap<>();
    private ArrayList<Double> returns = new ArrayList<>();
    private double expectedReturn;
    private double returnsVariance;
    private double returnsStandardDeviation;

    public Stock(String name){this.name = name;}
    public void setPrices(TreeMap<Date, Double> prices) { this.prices = prices; }
    public void setReturns(ArrayList<Double> returns) {this.returns = returns;}
    public void setExpectedReturn(double expectedReturn) { this.expectedReturn = expectedReturn; }
    public void setReturnsVariance(double returnsVariance){this.returnsVariance = returnsVariance;}
    public void setReturnsStandardDeviation(double returnsStandardDeviation) {this.returnsStandardDeviation = returnsStandardDeviation;}

    public String getName(){return name;}
    public ArrayList<Double> getReturns(){return returns;}
    public TreeMap<Date, Double> getPrices() { return prices; }
    public double getExpectedReturn() {return expectedReturn;}
    public double getReturnsVariance() { return returnsVariance;}
    public double getReturnsStandardDeviation(){return returnsStandardDeviation;}
}
