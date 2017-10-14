import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Controller {
    static DatabaseHandler dh = new DatabaseHandler();
    static AlphaVantageHandler avh = new AlphaVantageHandler();
    static boolean quit = false;

    ArrayList<String> stocks = new ArrayList<>();
    ArrayList<LiveStockRecord> records = new ArrayList<>();
    ArrayList<StockClock> clocks = new ArrayList<>();

    @FXML FlowPane stockList;
    @FXML ProgressBar liveStockProgressBar;
    @FXML FlowPane timePane;
    @FXML TextArea infoBox;

 private ArrayList<XYChart.Series<Number, Number>> stockSeries = new ArrayList<>();

@FXML public void initialize()
{
    System.out.println("Initialising...");
    initialiseFeeds();
    initialiseStocks();
    initialiseClocks();
    initialiseDisplay();
    startRealTime();
}

private void initialiseFeeds(){
    try { dh.init("agent", "0Y5q0m28pSB9jj2O"); } catch (Exception e) {}
    avh.init("PBATJ7L9N8SNK835");
}

private void initialiseDisplay(){
        for (String curr : stocks) {
            String name = null;
            try {
                name = dh.executeQuery("SELECT Name FROM indices WHERE Symbol='" + curr + "';").get(0);
                    float currPrice = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol='" + curr + "' ORDER BY TradeDateTime DESC LIMIT 1;").get(0));
                    float prevPrice = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + curr + "' ORDER BY TradeDate DESC LIMIT 2;").get(1));

                    LiveStockRecord currRec = new LiveStockRecord(curr, name, currPrice, prevPrice, dh.executeQuery("SELECT TradeDateTime FROM intradaystockprices WHERE Symbol='" + curr + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));

                    records.add(currRec);
                    Platform.runLater(() -> stockList.getChildren().add(currRec.getNode()));
            } catch (SQLException e) {
            e.printStackTrace();
            }
        }
}

public void printToInfoBox(String string){

}

private void initialiseClocks(){
    clocks.add(new StockClock("NASDAQ", LocalTime.of(9,30), LocalTime.of(16,0), ZoneId.of("America/New_York")));
    clocks.add(new StockClock("London SE", LocalTime.of(8,0), LocalTime.of(16,30),ZoneId.of("Europe/London")));
    clocks.add(new StockClock("Tokyo SE", LocalTime.of(9,0), LocalTime.of(15,0),ZoneId.of("Asia/Tokyo"))); //TODO: Allow multiple open/close periods

    for(StockClock clock : clocks)
        timePane.getChildren().add(clock.getNode());
}

private void updateClocks(){
        new Thread(() -> {
            for (StockClock clock : clocks)
                clock.updateTime();
        }).start();
}

   @FXML public void startRealTime(){
        new Thread(()-> {
            while (!quit) {
                try { TimeUnit.MILLISECONDS.sleep(500); } catch (InterruptedException e) { e.printStackTrace(); }

                int s = LocalTime.now().getSecond();

                liveStockProgressBar.setProgress((s+1)/60.0f);
                updateClocks();

                if(s == 0)
                    updateStockData();
            }
        }).start();
    }

    @FXML public void initialiseStocks() {
       ArrayList<Thread> threads = new ArrayList<>();

        try {
            stocks = dh.executeQuery("SELECT Symbol FROM indices");
        } catch (SQLException e) { e.printStackTrace(); }

           for (String symbol : stocks)
               threads.add(new Thread(() -> {
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
               }));

        for (String symbol : stocks)
            threads.add(new Thread(() -> {
                ArrayList<String> temp = null;
                try {
                    temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&datatype=csv&outputsize=full&apikey=" + avh.getApiKey());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println("Downloaded full history of " + symbol);
                StockRecordParser.importDailyMarketData(temp, symbol, dh);
                System.out.println("Successully committed " + symbol + " full history to the database!");
            }));

        for(Thread thread : threads)
            thread.start();

        for(Thread thread:threads)
            try { thread.join(); } catch (InterruptedException e) { e.printStackTrace(); }
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

    @FXML private void updateStockData(){
        for(LiveStockRecord curr:records){
                new Thread(()-> {
                    curr.setUpdating(true);
                    ArrayList<String> temp = null;
                    try {
                        temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=" + curr.getSymbol() + "&interval=1min&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());
                    } catch (IOException e) {
                    }
                    if(temp != null && temp.size() >= 2) {
                        System.out.println("Downloaded " + curr.getSymbol() + " 1 minute update \r\n" + temp.get(1));
                        StockRecordParser.importIntradayMarketData(temp, curr.getSymbol(), dh);

                        try {
                            temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + curr.getSymbol() + "&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());
                        } catch (IOException e) {
                        }
                        StockRecordParser.importDailyMarketData(temp, curr.getSymbol(), dh);

                        updateRecord(curr);
                        curr.setUpdating(false);
                        curr.updateChart(dh);
                    }
                }).start();
        }
    }
}
