package Prediction;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.9
 */

public class Investment {
    private int investmentPeriod, remainingDays, quantity;
    private double price, investment;

    /**
     * Initialises the Investment class with the length of investment time, quantity of stock bought and the price it was bought at
     *
     * @param investmentPeriod Number of days to hold the stock
     * @param quantity         Amount of shares bought
     * @param price            Price of each share
     */
    Investment(int investmentPeriod, int quantity, double price) {
        this.investmentPeriod = investmentPeriod;
        remainingDays = investmentPeriod;
        this.quantity = quantity;
        this.price = price;
        investment = quantity * price;
    }

    /**
     * Returns whether or not the investment has reached its holding period
     *
     * @return True if the investment should be considered for sale/reinvestment, False if the investment period is not over
     */
    boolean hasExpired() {
        return remainingDays <= 0;
    }

    /**
     * Decrement the investment period (i.e. a day has passed)
     */
    void decrementTime() {
        remainingDays--;
    }

    /**
     * Resets the investment period (i.e. reinvests the stock without selling and re-buying)
     */
    void resetTime() {
        remainingDays = investmentPeriod;
    }

    /**
     * Returns the price at the initial point of purchasing the stock (unaffected by reinvestment)
     * @return Initial stock price
     */
    public double getPrice() {
        return price;
    }

    /**
     * Returns the investment amount (stock price * quantity)
     * @return Investment amount
     */
    public double getInvestment() {
        return investment;
    }

    /**
     * Returns the quantity of stock shares held in this investment
     * @return Quantity of shares held of this stock within this investment
     */
    int getQuantity() {
        return quantity;
    }
}
