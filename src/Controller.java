import CustomFX.LiveStockRecord;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Controller {
    static DatabaseHandler dh = new DatabaseHandler();
    static AlphaVantageHandler avh = new AlphaVantageHandler();
    static boolean initialUpdate = true;
    static boolean quit = false;

    ArrayList<String> stocks = new ArrayList<>();
    ArrayList<LiveStockRecord> records = new ArrayList<>();

    @FXML FlowPane stockList;
    @FXML ProgressBar liveStockProgressBar;

   @FXML public void startRealTime(){
        new Thread(()-> {
            while (!quit) {
                try {
                    updateStockData();
                    for(int i = 0; i < 60; i++) {
                        TimeUnit.SECONDS.sleep(1);
                        liveStockProgressBar.setProgress(i/60.0);
                    }
                    liveStockProgressBar.setProgress(0);
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

            if(initialUpdate)
                initialUpdate = false;
        }

        for (String curr : stocks) {
            ArrayList<String> name = null;
            ArrayList<String> statistics = null;
            try {
                statistics = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol='" + curr + "' ORDER BY TradeDateTime DESC LIMIT 2");
                name = dh.executeQuery("SELECT Name FROM indices WHERE Symbol='" + curr + "';");
            } catch (SQLException e) { e.printStackTrace(); }
            float currPrice = Float.parseFloat(statistics.get(0)), prevPrice = Float.parseFloat(statistics.get(1));

            LiveStockRecord currRec = new LiveStockRecord(curr, name.get(0), currPrice, prevPrice);

            records.add(currRec);
            stockList.getChildren().add(currRec.getNode());
        }
    }

    private void updateRecord(LiveStockRecord record){
        ArrayList<String> statistics = null;
           try {
               statistics = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol='" + record.getSymbol() + "' ORDER BY TradeDateTime DESC LIMIT 2");
           } catch (SQLException e) {e.printStackTrace();}

           float currPrice = Float.parseFloat(statistics.get(0)), prevPrice = Float.parseFloat(statistics.get(1));

            record.updateRecord(currPrice, prevPrice);
    }

    @FXML private void updateStockData(){
        for(LiveStockRecord curr:records){
                new Thread(()-> {
                    curr.toggleProgress();
                    ArrayList<String> temp = null;
                    try {
                        temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=" + curr.getSymbol() + "&interval=1min&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());
                    } catch (IOException e) {
                    }
                    System.out.println("Downloaded " + curr.getSymbol() + " 1 minute update");
                    StockRecordParser.importIntradayMarketData(temp, curr.getSymbol(), dh);

                    updateRecord(curr);
                    curr.toggleProgress();
                }).start();
        }
    }
}
