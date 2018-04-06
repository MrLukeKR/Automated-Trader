package Records;

import Default.DatabaseHandler;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.1
 */

public class LiveStockRecord {
    private final String symbol;

    private final HBox hStock = new HBox();
    private final Label stockPrice = new Label();
    private final Label stockChange = new Label();
    private final Label prevClosePrice = new Label();
    private final XYChart.Series<Number, Number> stockData = new XYChart.Series<>();
    private final NumberAxis xAxis = new NumberAxis(0,0,1);
    private final NumberAxis yAxis = new NumberAxis();
    private final LineChart<Number, Number> stockChart = new LineChart<>(xAxis, yAxis);
    private final ProgressIndicator progress = new ProgressIndicator();

    /**
     * Initialises a new Live Stock Record by setting a stock ticker, name and database handler to prevent deadlocks when accessing the database
     *
     * @param symbol    Stock ticker (e.g. AAPL)
     * @param stockName The full name of the stock's company (e.g. Apple Inc.)
     * @param dh        Database Handler for access to the database
     */
    public LiveStockRecord(String symbol, String stockName, DatabaseHandler dh) {
        this.symbol = symbol;
        Label stockNameLabel = new Label(stockName);
        VBox newStockStats = new VBox();
        Label stockSymbol = new Label();
        stockSymbol.setText(symbol);

        stockChart.setVisible(false);

        xAxis.setTickLabelsVisible(false);
        xAxis.setOpacity(0);
        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(Integer.MAX_VALUE);
        yAxis.setUpperBound(Integer.MIN_VALUE);

        stockData.setName(symbol);

        stockChart.setMinSize(300,100);
        stockChart.setMaxSize(300,100);
        stockChart.getData().add(stockData);
        stockChart.setAnimated(false);

        progress.setMaxSize(75,75);
        progress.setVisible(false);

        VBox newStock = new VBox();
        newStock.setMinSize(125,50);
        newStock.setPrefSize(125,50);
        newStock.setMaxSize(125,50);

        newStockStats.setMinSize(100, 50);
        newStockStats.setMaxSize(100, 50);

        stockPrice.setFont(Font.font(null, 14));
        stockChange.setFont(Font.font(null, 12));

        newStockStats.getChildren().add(stockPrice);
        newStockStats.getChildren().add(stockChange);
        newStockStats.getChildren().add(prevClosePrice);

        stockNameLabel.setFont(Font.font(null, FontWeight.BOLD, 14));
        stockSymbol.setFont(Font.font(null, 12));
        prevClosePrice.setFont(Font.font(null, 10));
        stockSymbol.setTextFill(Color.GREY);
        prevClosePrice.setTextFill(Color.BLUE);

        stockNameLabel.setMinSize(100,20);
        stockSymbol.setMinSize(100,10);

        newStock.getChildren().add(stockNameLabel);
        newStock.getChildren().add(stockSymbol);

        hStock.getChildren().add(newStock);
        Separator sep = new Separator(Orientation.VERTICAL);
        sep.setVisible(false);
        hStock.getChildren().add(sep);
        hStock.getChildren().add(newStockStats);
        hStock.getChildren().add(stockChart);
        hStock.getChildren().add(progress);

        updateRecord(dh);
    }

    /**
     * Updates the GUI by retrieving the latest data from the database
     *
     * @param dh Database handler for this stock record
     */
    public void updateRecord(DatabaseHandler dh){
        float currPrice = getCurrentPrice(dh),
                prevPrice = getPreviousPrice(dh),
                change = currPrice - prevPrice,
                percentChange = (change / prevPrice * 100.0f);

        Platform.runLater(() -> {
            stockPrice.setText(String.valueOf(currPrice));
            prevClosePrice.setText(String.valueOf("Prev. close: " + prevPrice));

            if (percentChange < 0) {
                stockChange.setTextFill(Color.RED);
                stockChange.setText("\u25BC ");
            } else if (percentChange == 0) {
                stockChange.setTextFill(Color.BLACK);
                stockChange.setText("\u25B6 ");
            } else {
                stockChange.setTextFill(Color.GREEN);
                stockChange.setText("\u25B2 ");
            }

            stockChange.setText(stockChange.getText() + String.format("%.02f",change) + " (" + String.format("%.02f", percentChange) + "%)");
        });
    }

    /**
     * Determines which was the last trading day before Today
     * @return An integer value of how many days ago trading last occurred
     */
    private int getLastTradeDay() {
        switch (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:
                return 3;
            case Calendar.SUNDAY:
                return 2;
            default:
                return 1;
        }
    }

    /**
     * Retrieves the price of a stock on the last trading day prior to Today
     * @param dh Database handler
     * @return The price retrieved from the last trading day
     */
    private float getPreviousPrice(DatabaseHandler dh){
        ArrayList<String> pPrice = null;
        try {
            pPrice = (dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + symbol + "' AND TradeDate < CURDATE() ORDER BY TradeDate DESC LIMIT 1;"));
        } catch (SQLException e) { e.printStackTrace(); }

        if(pPrice == null || pPrice.isEmpty())
            return -1;
        else
            return Float.parseFloat(pPrice.get(0));
    }

    /**
     * Retrieves the current price of a stock for today
     * @param dh Database handler
     * @return Today's price retrieved from the database
     */
    private float getCurrentPrice(DatabaseHandler dh){
        ArrayList<String> cPrice = null;
        try {
            cPrice = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol='" + symbol + "' ORDER BY TradeDateTime DESC LIMIT 1;");
        } catch (SQLException e) { e.printStackTrace(); }

        if(cPrice == null || cPrice.isEmpty())
            return -1;
        else
            return Float.parseFloat(cPrice.get(0));
    }

    /**
     * Updates the price graph with the latest price information
     * @param dh Database handler to use for accessing the database
     * @param forceClear True if the graph should be cleared before adding more data, False if it should be added to existing data
     */
    public void updateChart(DatabaseHandler dh, boolean forceClear) {
        try {
            float prevPrice = getPreviousPrice(dh),
                    currPrice = getCurrentPrice(dh);

            if(prevPrice < 0 || currPrice < 0) return;

            ArrayList<String> statistics = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE DATE(TradeDateTime) = CURDATE() AND Symbol='" + symbol + "' ORDER BY TradeDateTime ASC;");

            if (statistics.isEmpty())
                statistics = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE DATE(TradeDateTime) = SUBDATE(CURDATE(), " + getLastTradeDay() + ") AND Symbol='" + symbol + "' ORDER BY TradeDateTime ASC;");

            ArrayList<String> finalStatistics = statistics;
            Platform.runLater(()->xAxis.setLowerBound(-finalStatistics.size() + 1));

            if (statistics.size() < stockData.getData().size() || forceClear) {
                Platform.runLater(()-> {
                            stockData.getData().clear();
                            yAxis.setLowerBound(Integer.MAX_VALUE);
                            yAxis.setUpperBound(Integer.MIN_VALUE);
                        }
                );
            }

            for(int time = 0; time < statistics.size(); time++){
                float price = Float.parseFloat(statistics.get(time));
                XYChart.Data<Number, Number> point = new XYChart.Data<>(time-statistics.size()+1, price);
                Rectangle rect = new Rectangle(0,0);
                rect.setVisible(false);
                point.setNode(rect);
                Platform.runLater(()-> {
                    yAxis.setLowerBound(Math.min(yAxis.getLowerBound(), price));
                    yAxis.setUpperBound(Math.max(yAxis.getUpperBound(), price));
                });

                final int t = time, size = statistics.size();

                Platform.runLater(() -> {
                    if (stockData.getData().size() < size && t + 1 > stockData.getData().size())
                        stockData.getData().add(t, point);
                    else
                        stockData.getData().set(t, point);
                });
            }

            if (prevPrice > currPrice)
                Platform.runLater(()-> stockData.nodeProperty().get().setStyle("-fx-stroke: red;   -fx-stroke-width: 1px;"));
            else if (prevPrice < currPrice)
                Platform.runLater(()-> stockData.nodeProperty().get().setStyle("-fx-stroke: green; -fx-stroke-width: 1px;"));
            else Platform.runLater(()-> stockData.nodeProperty().get().setStyle("-fx-stroke: black; -fx-stroke-width: 1px;"));
            if (!stockChart.isVisible()) Platform.runLater(()-> stockChart.setVisible(true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the update flag to prevent the same record from being updated multiple times in parallel
     * @param isUpdating True if the record is currently being updated, False otherwise
     */
    public void setUpdating(boolean isUpdating) {
        Platform.runLater(() -> progress.setVisible(isUpdating));
    }

    /**
     * Returns the stock ticker of this record
     * @return Stock ticker/symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Returns the record's GUI node for inclusion with the main application GUI
     * @return Record's JavaFX GUI node
     */
    public Node getNode() {
        return hStock;
    }
}