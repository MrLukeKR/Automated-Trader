package CustomFX;

import javafx.application.Platform;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class LiveStockRecord {
    String name;
    String symbol;
    VBox newStock = new VBox();
    HBox hStock = new HBox();
    Label stockSymbol;
    Label stockPrice = new Label();
    Label stockChange = new Label();
    ProgressIndicator prog = new ProgressIndicator();

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

        prog.setMaxHeight(35);
        prog.setMaxWidth(35);
        prog.setVisible(false);

            newStock.setMinWidth(125);
            newStock.setMinHeight(50);
            newStock.setPrefWidth(125);
            newStock.setPrefHeight(50);
            newStock.setMaxWidth(125);
            newStock.setMinHeight(50);

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
