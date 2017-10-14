import javafx.application.Platform;

import javafx.geometry.Orientation;
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

public class LiveStockRecord {
    String name;
    String symbol;
    VBox newStock = new VBox();
    HBox hStock = new HBox();
    Label stockSymbol;
    Label stockPrice = new Label();
    Label stockChange = new Label();
    ProgressIndicator prog = new ProgressIndicator();
    LineChart stockChart;
    XYChart.Series<Number, Number> stockData = new XYChart.Series<>();

    NumberAxis xAxis = new NumberAxis(), yAxis = new NumberAxis();

    double price, percentChange;
    double change;
    String date;

    public LiveStockRecord(String symbol, String stockName, float currPrice, float prevPrice, String date){
        name = stockName;
        price = currPrice;
        this.date = date;
        this.symbol = symbol;
        Label stockNameLabel = new Label(stockName);
        VBox newStockStats = new VBox();
        stockSymbol = new Label(symbol);
        stockChart = new LineChart(xAxis, yAxis);

        xAxis.setTickLabelsVisible(false);
        xAxis.setOpacity(0);

        stockData.setName(symbol);

        stockChart.setMinSize(300,100);
        stockChart.setMaxSize(300,100);
        stockChart.getData().add(stockData);

        prog.setMaxHeight(35);
        prog.setMaxWidth(35);
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

            updateRecord(currPrice, prevPrice);

            newStockStats.getChildren().add(stockPrice);
            newStockStats.getChildren().add(stockChange);

            stockNameLabel.setFont(Font.font(null, FontWeight.BOLD, 14));
            stockSymbol.setFont(Font.font(null, 12));
            stockSymbol.setTextFill(Color.GREY);

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
            statistics = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE TradeDateTime>(SELECT TradeDate FROM dailystockprices WHERE Symbol='"+ symbol +"' ORDER BY TradeDate DESC LIMIT 1) AND Symbol='" + symbol + "' ORDER BY TradeDateTime ASC;");

            for(int time = 0; time < statistics.size(); time++){
                float price = Float.parseFloat(statistics.get(time));
                float prevPrice = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + symbol + "' ORDER BY TradeDate DESC LIMIT 2;").get(1));
                float priceChange = (price - prevPrice) / prevPrice * 100.0f;
                XYChart.Data<Number, Number> point = new XYChart.Data(time-statistics.size()+1, priceChange);
                Rectangle rect = new Rectangle(0,0);
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
