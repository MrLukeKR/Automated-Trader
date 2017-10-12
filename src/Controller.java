import CustomFX.LiveStockRecord;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Controller {
    static DatabaseHandler dh = new DatabaseHandler();
    static AlphaVantageHandler avh = new AlphaVantageHandler();
    static boolean quit = false;

    ArrayList<String> stocks = new ArrayList<>();
    ArrayList<LiveStockRecord> records = new ArrayList<>();

    @FXML FlowPane stockList;
    @FXML ProgressBar liveStockProgressBar;
    @FXML LineChart liveStockChart;

 private ArrayList<XYChart.Series<Number, Number>> stockSeries = new ArrayList<>();

@FXML public void initialize()
{
    initialiseStocks();
    startRealTime();
}

   @FXML public void startRealTime(){
        new Thread(()-> {
            while (!quit) {
                try {
                    liveStockProgressBar.setProgress(0);

                    for(int i = 0; i < 60000; i++) {
                        TimeUnit.MILLISECONDS.sleep(1);
                        liveStockProgressBar.setProgress(i/60000.0);
                    }

                    updateStockData();
                } catch (InterruptedException e) {
                }
            }
        }).start();
    }

    @FXML public void initialiseStocks() {
        try {
            dh.init("agent", "0Y5q0m28pSB9jj2O");
        } catch (Exception e) {
        }

        avh.init("PBATJ7L9N8SNK835");

        try {
            stocks = dh.executeQuery("SELECT Symbol FROM indices");
        } catch (SQLException e) {
            e.printStackTrace();
        }


        for (String symbol : stocks) {
            new Thread(() -> {
                File file = new File("res/historicstocks/" + symbol + ".csv");
                try {
                    StockRecordParser.importDailyMarketData(file, symbol, dh);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                System.out.println("Successfully committed complete Yahoo records of " + symbol + " to the database!");

                ArrayList<String> temp = null;
                try {
                    temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=" + symbol + "&interval=1min&datatype=csv&outputsize=full&apikey=" + avh.getApiKey());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                System.out.println("Downloaded intraday history of " + symbol);

                StockRecordParser.importIntradayMarketData(temp, symbol, dh);

                System.out.println("Successully committed " + symbol + " intraday history to the database!");
            }).start();
        }

        for(String symbol : stocks) {
            new Thread(()-> {
                ArrayList<String> temp = null;
                try {
                    temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&datatype=csv&outputsize=full&apikey=" + avh.getApiKey());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println("Downloaded full history of " + symbol);
                StockRecordParser.importDailyMarketData(temp, symbol, dh);
                System.out.println("Successully committed " + symbol + " full history to the database!");
            }).start();
        }

        for (String curr : stocks) {
            ArrayList<String> name = null;
            ArrayList<String> statistics = null;
            try {
                statistics = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol='" + curr + "' ORDER BY TradeDateTime DESC LIMIT 1;");
                statistics.add(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + curr + "' ORDER BY TradeDate DESC LIMIT 2;").get(1));
                statistics.add(dh.executeQuery("SELECT TradeDateTime FROM intradaystockprices WHERE Symbol='" + curr + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));
                name = dh.executeQuery("SELECT Name FROM indices WHERE Symbol='" + curr + "';");
            } catch (SQLException e) { e.printStackTrace(); }

            float currPrice, prevPrice;

            if(!statistics.isEmpty()) {
                currPrice = Float.parseFloat(statistics.get(0));
                prevPrice = Float.parseFloat(statistics.get(1));
            }else {
                currPrice = 0;
                prevPrice = 0;
            }

            LiveStockRecord currRec = new LiveStockRecord(curr, name.get(0), currPrice, prevPrice, statistics.get(2));

            records.add(currRec);
            stockList.getChildren().add(currRec.getNode());
        }

        for(LiveStockRecord record : records) {
         XYChart.Series<Number, Number> temp = new XYChart.Series<>();
         temp.setName(record.getSymbol());
            stockSeries.add(temp);
        }

        liveStockChart.getData().addAll(stockSeries);

        for(XYChart.Series<Number, Number> series : stockSeries){
            ArrayList<String> statistics = null;
            try {
                statistics = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol='" + series.getName() + "' ORDER BY TradeDateTime DESC LIMIT 60;");
                statistics.add(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + series.getName() + "' ORDER BY TradeDate DESC LIMIT 2;").get(1));
            }catch (Exception e) { }

            for(int time = 0; time < 60; time++){
                float price = Float.parseFloat(statistics.get(time));
                float prevPrice = Float.parseFloat(statistics.get(60));
                float priceChange = (price - prevPrice) / prevPrice * 100.0f;
                XYChart.Data<Number, Number> point = new XYChart.Data(time-59, priceChange);
                Rectangle rect = new Rectangle(0,0);
                rect.setVisible(false);
                point.setNode(rect);

                series.getData().add(point);
            }
        }
    }

    private void updateRecord(LiveStockRecord record){
        ArrayList<String> statistics = null;
           try {
               statistics = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol='" + record.getSymbol() + "' ORDER BY TradeDateTime DESC LIMIT 1;");
               statistics.add(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + record.getSymbol() + "' ORDER BY TradeDate DESC LIMIT 2;").get(1));
           } catch (SQLException e) {e.printStackTrace();}

           float currPrice = Float.parseFloat(statistics.get(0)), prevPrice = Float.parseFloat(statistics.get(1));

            record.updateRecord(currPrice, prevPrice);
    }

    private void updateStockChart(String symbol){
        Platform.runLater(() -> {
            XYChart.Series series = null;
            for (XYChart.Series curr : stockSeries)
                if(curr.getName() == symbol)
                    series = curr;

            if(series == null)
                return;

                for(int i = 0; i < 59; i++) {
                    XYChart.Data<Number, Number> prev = (XYChart.Data<Number,Number>)series.getData().get(i+1);
                    XYChart.Data<Number, Number> temp = new XYChart.Data<>((int)prev.getXValue() - 1,prev.getYValue());
                    stockSeries.remove(prev);

                    Rectangle rect = new Rectangle(0,0);
                    rect.setVisible(false);
                    temp.setNode(rect);

                    series.getData().set(i, temp);
                }

                LiveStockRecord record = null;
                for(LiveStockRecord rec : records)
                    if(rec.getSymbol() == symbol) {
                        record = rec;
                        break;
                    }

                    if(record != null)
                    {
                        XYChart.Data<Number, Number> point = new XYChart.Data<>(0, record.getPercentChange());
                        Rectangle rect = new Rectangle(0,0);
                        rect.setVisible(false);
                        point.setNode(rect);
                        series.getData().set(59, point);
                    }
        });
    }

    @FXML private void updateStockData(){
        for(LiveStockRecord curr:records){
                new Thread(()-> {
                    curr.setUpdating(true);
                    ArrayList<String> temp = null;
                    try {
                        temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=" + curr.getSymbol() + "&interval=1min&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());
                    } catch (IOException e) {
                    }
                    System.out.println("Downloaded " + curr.getSymbol() + " 1 minute update");
                    StockRecordParser.importIntradayMarketData(temp, curr.getSymbol(), dh);

                    updateRecord(curr);
                    curr.setUpdating(false);

                    updateStockChart(curr.getSymbol());
                }).start();
        }
    }
}
