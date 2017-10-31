import javafx.application.Platform;

import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
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

import java.util.ArrayList;
import java.util.Calendar;

public class LiveStockRecord {
    String name;
    String symbol;
    double prevPrice;
    VBox newStock = new VBox();
    HBox hStock = new HBox();
    Label stockSymbol;
    Label stockPrice = new Label();
    Label stockChange = new Label();
    ProgressIndicator prog = new ProgressIndicator();
    LineChart stockChart;
    XYChart.Series<Number, Number> stockData = new XYChart.Series<>();
    Label prevClosePrice = new Label();

    NumberAxis xAxis = new NumberAxis(), yAxis = new NumberAxis();

    double price, percentChange;
    double change;
    String date;

    public LiveStockRecord(String symbol, String stockName){ //TODO: Change the constructor to allow for prevPrice again
        name = stockName;
        this.symbol = symbol;
        this.prevPrice = prevPrice;
        Label stockNameLabel = new Label(stockName);
        VBox newStockStats = new VBox();
        stockSymbol = new Label(symbol);
        stockChart = new LineChart(xAxis, yAxis);


        xAxis.setTickLabelsVisible(false);
        xAxis.setOpacity(0);
        xAxis.setAutoRanging(false);
        yAxis.setAutoRanging(false);

        stockData.setName(symbol);

        stockChart.setMinSize(300,100);
        stockChart.setMaxSize(300,100);
        stockChart.getData().add(stockData);
        stockChart.setAnimated(false);

        prog.setMaxHeight(75);
        prog.setMaxWidth(75);
        prog.setVisible(false);

            newStock.setMinSize(125,50);
            newStock.setPrefSize(125,50);
            newStock.setMaxSize(125,50);

            newStockStats.setMinWidth(100);
            newStockStats.setMaxWidth(100);
            newStockStats.setMinHeight(50);
            newStockStats.setMaxHeight(50);

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

            stockNameLabel.setMinWidth(100);
            stockNameLabel.setMinHeight(20);

            stockSymbol.setMinWidth(100);
            stockSymbol.setMinHeight(10);

            newStock.getChildren().add(stockNameLabel);
            newStock.getChildren().add(stockSymbol);

            hStock.getChildren().add(newStock);
            Separator sep = new Separator(Orientation.VERTICAL);
            sep.setVisible(false);
            hStock.getChildren().add(sep);
            hStock.getChildren().add(newStockStats);
            hStock.getChildren().add(stockChart);
            hStock.getChildren().add(prog);
    }

    public void updateRecord(float currPrice, float prevPrice){
        Platform.runLater(() -> {
            String sCurrPrice = String.valueOf(currPrice);
            prevClosePrice.setText("Prev. Close: " + String.valueOf(prevPrice));
            change = currPrice - prevPrice;
            percentChange = (change / prevPrice * 100.0f);

            if (percentChange < 0) {
                stockChange.setTextFill(Color.RED);
                stockPrice.setText(sCurrPrice);
                stockChange.setText("▼" + String.format("%.02f",change) + " (" + String.format("%.02f", percentChange) + "%)");
            } else if (percentChange == 0) {
                stockChange.setTextFill(Color.BLACK);
                stockPrice.setText(sCurrPrice);
                stockChange.setText("► " + String.format("%.02f",change) + " (" + String.format("%.02f", percentChange) + "%)");
            } else {
                stockChange.setTextFill(Color.GREEN);
                stockPrice.setText(sCurrPrice);
                stockChange.setText("▲ " + String.format("%.02f",change) + " (" + String.format("%.02f", percentChange) + "%)");
            }
        });
    }

    public void updateChart(DatabaseHandler dh){
        ArrayList<String> statistics = null;
        try {
            ArrayList<String> pPrice;
            if(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
                pPrice = (dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + symbol + "' AND TradeDate = SUBDATE(CURDATE(),3) ORDER BY TradeDate DESC LIMIT 1;"));
            else
                pPrice = (dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + symbol + "' AND TradeDate = SUBDATE(CURDATE(),1) ORDER BY TradeDate DESC LIMIT 1;"));


            ArrayList<String> cPrice = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE DATE(TradeDateTime) = CURDATE() AND Symbol='" + symbol + "' ORDER BY TradeDateTime DESC LIMIT 1;");

            if(cPrice.isEmpty() || pPrice.isEmpty())
                return;

            float prevPrice = Float.parseFloat(pPrice.get(0));
            float currPrice = Float.parseFloat(cPrice.get(0));
            statistics = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE DATE(TradeDateTime) = CURDATE() AND Symbol='" + symbol + "' ORDER BY TradeDateTime ASC;");
            yAxis.setLowerBound(Integer.MAX_VALUE);
            yAxis.setUpperBound(Integer.MIN_VALUE);

            xAxis.setLowerBound(-statistics.size() + 1);
            xAxis.setUpperBound(0);

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
                if(stockData.getData().size() < statistics.size() && time >= stockData.getData().size()){
                    Platform.runLater(()->stockData.getData().add(t,point));
                }
                else
                    Platform.runLater(()->stockData.getData().set(t, point));
            }

            if(prevPrice > currPrice)
                stockData.nodeProperty().get().setStyle("-fx-stroke: red; -fx-stroke-width: 1px;");
            else if (prevPrice < currPrice)
                stockData.nodeProperty().get().setStyle("-fx-stroke: green; -fx-stroke-width: 1px;");
            else
                stockData.nodeProperty().get().setStyle("-fx-stroke: black; -fx-stroke-width: 1px;");
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setUpdating(boolean isUpdating) {Platform.runLater(() -> prog.setVisible(isUpdating));}

    public double getChange() {return change;}
    public String getDate(){return date;}
    public double getPrice(){return price;}

    public String getName() {return name;}
    public String getSymbol() {return symbol;}

    public Node getNode() {return hStock;}

    public double getPercentChange() {
        return percentChange;
    }
}