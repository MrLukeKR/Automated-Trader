package Prediction;

public class Investment {
    private int investmentPeriod = 0;
    private int remainingDays = 0;
    private int quantity = 0;
    private double price = 0;
    private double investment = 0;

    public Investment(int investmentPeriod, int quantity, double price) {
        this.investmentPeriod = investmentPeriod;
        remainingDays = investmentPeriod;
        this.quantity = quantity;
        this.price = price;
        investment = quantity * price;
    }

    public boolean hasExpired() {
        return remainingDays == 0;
    }

    public void decrementTime() {
        remainingDays--;
    }

    public void resetTime() {
        remainingDays = investmentPeriod;
    }

    public double getPrice() {
        return price;
    }

    public double getInvestment() {
        return investment;
    }

    public int getQuantity() {
        return quantity;
    }
}
