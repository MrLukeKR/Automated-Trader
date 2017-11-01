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

public class LiveStockRecord {
    String name;
    String symbol;
    String date;

    VBox newStock = new VBox();
    HBox hStock = new HBox();
    Label stockSymbol = new Label();
    Label stockPrice = new Label();
    Label stockChange = new Label();
    Label prevClosePrice = new Label();
    XYChart.Series<Number, Number> stockData = new XYChart.Series<>();
    NumberAxis xAxis = new NumberAxis(0,0,1), yAxis = new NumberAxis();
    LineChart stockChart = new LineChart(xAxis, yAxis);
    ProgressIndicator progress = new ProgressIndicator();

    public LiveStockRecord(String symbol, String stockName, DatabaseHandler dh){ //TODO: Change the constructor to allow for prevPrice again
        name = stockName;
        this.symbol = symbol;
        Label stockNameLabel = new Label(stockName);
        VBox newStockStats = new VBox();
        stockSymbol.setText(symbol);

        xAxis.setTickLabelsVisible(false);
        xAxis.setOpacity(0);
        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);

        stockData.setName(symbol);

        stockChart.setMinSize(300,100);
        stockChart.setMaxSize(300,100);
        stockChart.getData().add(stockData);
        stockChart.setAnimated(false);

        progress.setMaxSize(75,75);
        progress.setVisible(false);

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

    public void updateRecord(DatabaseHandler dh){
        float currPrice = getCurrentPrice(dh),
              prevPrice = getPreviousPrice(dh),
              change = currPrice - prevPrice,
              percentChange = (change / prevPrice * 100.0f);

        Platform.runLater(() -> {
            //TODO: If left to run after hours, make the previousClose Today's close
            stockPrice.setText(String.valueOf(currPrice));
            prevClosePrice.setText(String.valueOf("Prev. close:" + prevPrice));

            if (percentChange < 0) {
                stockChange.setTextFill(Color.RED);
                stockChange.setText("▼ ");
            } else if (percentChange == 0) {
                stockChange.setTextFill(Color.BLACK);
                stockChange.setText("► ");
            } else {
                stockChange.setTextFill(Color.GREEN);
                stockChange.setText("▲ ");
            }

            stockChange.setText(stockChange.getText() + String.format("%.02f",change) + " (" + String.format("%.02f", percentChange) + "%)");
        });
    }

    private float getPreviousPrice(DatabaseHandler dh){
        ArrayList<String> pPrice = null;
        try {
            int subtractDays = 1;

            if(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
                subtractDays = 3;

            pPrice = (dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + symbol + "' AND TradeDate = SUBDATE(CURDATE()," + subtractDays + ") ORDER BY TradeDate DESC LIMIT 1;"));
        } catch (SQLException e) { e.printStackTrace(); }

        if(pPrice == null || pPrice.isEmpty())
            return -1;
        else
            return Float.parseFloat(pPrice.get(0));
    }

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

    public void updateChart(DatabaseHandler dh){
        try {
            float prevPrice = getPreviousPrice(dh), //TODO: remove the need to keep passing the database handler
                  currPrice = getCurrentPrice(dh);  //TODO: remove the need to keep passing the database handler

            if(prevPrice < 0 || currPrice < 0) return;

            ArrayList<String> statistics = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE DATE(TradeDateTime) = CURDATE() AND Symbol='" + symbol + "' ORDER BY TradeDateTime ASC;");
            yAxis.setLowerBound(Integer.MAX_VALUE);
            yAxis.setUpperBound(Integer.MIN_VALUE);

            xAxis.setLowerBound(-statistics.size() + 1);

            for(int time = 0; time < statistics.size(); time++){
                float price = Float.parseFloat(statistics.get(time));
                XYChart.Data<Number, Number> point = new XYChart.Data(time-statistics.size()+1, price);
                Rectangle rect = new Rectangle(0,0);
                yAxis.setLowerBound(Math.min(yAxis.getLowerBound(),price));
                yAxis.setUpperBound(Math.max(yAxis.getUpperBound(),price));
                rect.setVisible(false);
                point.setNode(rect);

                if(stockData.getData().size() > statistics.size())
                    Platform.runLater(()->stockData.getData().removeAll());

                final int t = time;
                if(stockData.getData().size() < statistics.size() && time >= stockData.getData().size())
                    Platform.runLater(()->stockData.getData().add(t,point));
                else
                    Platform.runLater(()->stockData.getData().set(t, point));
            }

            if(prevPrice > currPrice)
                stockData.nodeProperty().get().setStyle("-fx-stroke: red; -fx-stroke-width: 1px;");
            else if (prevPrice < currPrice)
                stockData.nodeProperty().get().setStyle("-fx-stroke: green; -fx-stroke-width: 1px;");
            else
                stockData.nodeProperty().get().setStyle("-fx-stroke: black; -fx-stroke-width: 1px;");
        }catch (Exception e) { e.printStackTrace(); }
    }

    public void setUpdating(boolean isUpdating) {Platform.runLater(() -> progress.setVisible(isUpdating));}
    public String getDate(){return date;}
    public String getName() {return name;}
    public String getSymbol() {return symbol;}

    public Node getNode() {return hStock;}
}