package CustomFX;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class LiveStockRecord {
    String name;
    String symbol;
    VBox newStock = new VBox();
    HBox hStock = new HBox();
    Label stockSymbol;
    Label stockPrice = new Label();
    ProgressIndicator prog = new ProgressIndicator();

    public LiveStockRecord(String symbol, String stockName, float currPrice, float prevPrice){
        name = stockName;
        this.symbol = symbol;
        Label stockNameLabel = new Label(stockName);
        VBox newStockStats = new VBox();
        stockSymbol = new Label(symbol);

        prog.setMaxHeight(35);
        prog.setMaxWidth(35);
        prog.setVisible(false);

            newStock.setMinWidth(100);
            newStock.setMinHeight(50);
            newStock.setPrefHeight(50);
            newStock.setPrefWidth(100);
            newStock.setMaxWidth(100);
            newStock.setMinHeight(50);

            newStockStats.setMinWidth(75);
            newStockStats.setMaxWidth(75);
            newStockStats.setMinHeight(50);
            newStockStats.setMaxHeight(50);

            stockPrice.setFont(new Font("Arial", 14));

            updateRecord(currPrice, prevPrice);

            newStockStats.getChildren().add(stockPrice);

            stockNameLabel.setFont(new Font("Arial", 16));
            stockSymbol.setFont(new Font("Arial", 14));
            stockSymbol.setTextFill(Color.GREY);

            stockNameLabel.setMinWidth(100);
            stockNameLabel.setMinHeight(20);

            stockSymbol.setMinWidth(100);
            stockSymbol.setMinHeight(10);

            newStock.getChildren().add(stockNameLabel);
            newStock.getChildren().add(stockSymbol);

            hStock.getChildren().add(newStock);
            hStock.getChildren().add(newStockStats);
            hStock.getChildren().add(prog);
    }

    public void updateRecord(float currPrice, float prevPrice){
        Platform.runLater(() -> {
            String sCurrPrice = String.valueOf(currPrice);

            float percentChange = ((currPrice - prevPrice) / prevPrice * 100.0f);

            if (percentChange < 0) {
                stockPrice.setTextFill(Color.RED);
                stockPrice.setText("▼ " + sCurrPrice + "\r\n(" + String.format("%.04f", percentChange) + "%)");
            } else if (percentChange == 0) {
                stockPrice.setTextFill(Color.BLACK);
                stockPrice.setText("► " + sCurrPrice + "\r\n(" + String.format("%.04f", percentChange) + "%)");
            } else {
                stockPrice.setTextFill(Color.GREEN);
                stockPrice.setText("▲ " + sCurrPrice + "\r\n(" + String.format("%.04f", percentChange) + "%)");
            }
        });
    }

    public void toggleProgress(){Platform.runLater(() -> prog.setVisible(!prog.isVisible()));}
    public String getName() {return name;}
    public String getSymbol() {return symbol;}

    public Node getNode() {return hStock;}
}
