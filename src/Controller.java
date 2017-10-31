import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class Controller {
    static DatabaseHandler dh = new DatabaseHandler();
    static AlphaVantageHandler avh = new AlphaVantageHandler();
    static boolean quit = false;

    ArrayList<String> stocks = new ArrayList<>();
    ArrayList<LiveStockRecord> records = new ArrayList<>();
    ArrayList<StockClock> clocks = new ArrayList<>();

    final int downloadInterval = 1;

    @FXML FlowPane stockList;
    @FXML ProgressBar liveStockProgressBar;
    @FXML FlowPane timePane;
    @FXML TextArea infoBox;
    @FXML VBox newsBox;

 private ArrayList<XYChart.Series<Number, Number>> stockSeries = new ArrayList<>();

@FXML public void initialize()
{
    NeuralNetwork myNet = new NeuralNetwork();
    myNet.init(1,2,3,1);
    initialiseConnections();

        initialiseDatabase();

    initialiseStocks();
    initialiseClocks();
    initialiseDisplay();
    updateNews();

    for(LiveStockRecord stock : records)
        stock.updateChart(dh);

    updateStockData();
    startRealTime();
}

private void initialiseConnections(){
    System.out.println("Initialising Connections...");
    try {
        dh.init("agent", "0Y5q0m28pSB9jj2O");
    } catch (Exception e) {}
    avh.init("PBATJ7L9N8SNK835");

    NewsAPIHandler.authenticate("be7afde61f5e10bb20393025c35e50c7","1ff9ab03aa8e5bd073345d70d588abde");
}

private void initialiseDatabase(){
    System.out.println("Initialising Database...");
    try{
        dh.executeCommand("CREATE DATABASE IF NOT EXISTS automated_trader;");//TODO: Allow login of root to create the initial agent user
        dh.executeCommand("GRANT ALL ON automated_trader.* TO 'agent'@'%';");
        dh.executeCommand("USE automated_trader");
        dh.executeCommand("CREATE TABLE IF NOT EXISTS indices (Symbol varchar(7) UNIQUE NOT NULL, Name text NOT NULL, StartedTrading date NOT NULL, CeasedTrading date, TwitterUsername varchar(15), PRIMARY KEY (Symbol))");
        dh.executeCommand("CREATE TABLE IF NOT EXISTS dailystockprices (Symbol varchar(7) NOT NULL, TradeDate date NOT NULL, OpenPrice double NOT NULL, HighPrice double NOT NULL, LowPrice double NOT NULL, ClosePrice double NOT NULL, TradeVolume bigint(20) NOT NULL, PRIMARY KEY (Symbol,TradeDate), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
        dh.executeCommand("CREATE TABLE IF NOT EXISTS intradaystockprices (Symbol varchar(7) NOT NULL, TradeDateTime datetime NOT NULL, OpenPrice double NOT NULL, HighPrice double NOT NULL, LowPrice double NOT NULL, ClosePrice double NOT NULL, TradeVolume bigint(20) NOT NULL, PRIMARY KEY (Symbol,TradeDateTime), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
        dh.executeCommand("CREATE TABLE IF NOT EXISTS newsarticles (Symbol varchar(7) NOT NULL, Headline varchar(255) NOT NULL, Description text, Content text, Published datetime NOT NULL, URL text, Mood double DEFAULT 0.5, PRIMARY KEY (Symbol,Headline), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
        dh.executeCommand("CREATE TABLE IF NOT EXISTS tradetransactions (ID INT AUTO_INCREMENT NOT NULL, TradeDateTime datetime NOT NULL, Type varchar(4) NOT NULL, Symbol varchar(7) NOT NULL, Volume INT UNSIGNED NOT NULL DEFAULT 0, Price DOUBLE UNSIGNED NOT NULL,PRIMARY KEY (ID), FOREIGN KEY (Symbol) REFERENCES Indices(Symbol));");
        dh.executeCommand("CREATE TABLE IF NOT EXISTS banktransactions (ID INT AUTO_INCREMENT NOT NULL, TradeDateTime datetime NOT NULL, Amount double SIGNED NOT NULL, PRIMARY KEY (ID));");
    }catch (SQLException e){
        System.out.println(e.getMessage());
    }
}

private void initialiseDisplay(){
        for (String curr : stocks) {
            String name = null;
            try {
                name = dh.executeQuery("SELECT Name FROM indices WHERE Symbol='" + curr + "';").get(0);
                LiveStockRecord currRec = new LiveStockRecord(curr, name);
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
    clocks.add(new StockClock("Hong Kong SE", LocalTime.of(9,30), LocalTime.of(16,0),ZoneId.of("Asia/Hong_Kong")));
    clocks.add(new StockClock("Australia SX", LocalTime.of(10,0), LocalTime.of(16,0),ZoneId.of("Australia/Canberra")));

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
                try { TimeUnit.MILLISECONDS.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }

                int s = LocalTime.now().getSecond();
                int m = LocalTime.now().getMinute();
                int cycle = m % downloadInterval;

                double progressVal=((s+1) + cycle * 60) / (downloadInterval * 60.0f);

                liveStockProgressBar.setProgress(progressVal);
                updateClocks();

                if(cycle == 0 && s == 0)
                    updateStockData();
            }
        }).start();
    }

    private void updateNews(){
        try { NewsAPIHandler.getLatestNews(stocks, dh); } catch (IOException e) { e.printStackTrace(); }

        Platform.runLater(() -> {
           newsBox.getChildren().clear();

           ArrayList<String> symbols = null;
           ArrayList<String> headlines = null;
           try {
               symbols = dh.executeQuery("SELECT Symbol FROM newsarticles WHERE Published >= CURDATE() ORDER BY Published DESC");
               headlines = dh.executeQuery("SELECT Headline FROM newsarticles WHERE Published >= CURDATE() ORDER BY Published DESC");
           } catch (SQLException e) {e.printStackTrace();}

           for(int i = 0 ; i < headlines.size(); i++) {
               NewsRecord temp = new NewsRecord(symbols.get(i), headlines.get(i));
               newsBox.getChildren().add(temp.getNode());
           }
       });
    }

    @FXML public void initialiseStocks() {
        try {
            stocks = dh.executeQuery("SELECT Symbol FROM indices");
        } catch (SQLException e) { e.printStackTrace(); }

           for (String symbol : stocks){
               File file = new File("res/historicstocks/" + symbol + ".csv");
               try {
                   StockRecordParser.importDailyMarketData(file, symbol, dh);
               } catch (Exception e) { e.printStackTrace(); }

               System.out.println("Successfully committed complete Yahoo records of " + symbol + " to the database!");

               ArrayList<String> temp = null;
               try {
                   temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=" + symbol + "&interval=1min&datatype=csv&outputsize=full&apikey=" + avh.getApiKey());
               } catch (Exception e) { e.printStackTrace(); }

               System.out.println("Downloaded intraday history of " + symbol);

               StockRecordParser.importIntradayMarketData(temp, symbol, dh);

               System.out.println("Successully committed " + symbol + " intraday history to the database!");
           }

        for (String symbol : stocks){
                ArrayList<String> temp = null;
                try {
                    temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&datatype=csv&outputsize=full&apikey=" + avh.getApiKey());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println("Downloaded full history of " + symbol);
                StockRecordParser.importDailyMarketData(temp, symbol, dh);
                System.out.println("Successully committed " + symbol + " full history to the database!");
            };
    }

    private void updateRecord(LiveStockRecord record){
        ArrayList<String> cPrice = null;
        ArrayList<String> pPrice = null;
           try {
               cPrice = dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol='" + record.getSymbol() + "' ORDER BY TradeDateTime DESC LIMIT 1;");

               if(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
                   pPrice = (dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + record.getSymbol() + "' AND TradeDate = SUBDATE(CURDATE(),3) ORDER BY TradeDate DESC LIMIT 1;"));
               else
                   pPrice = (dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol='" + record.getSymbol() + "' AND TradeDate = SUBDATE(CURDATE(),1) ORDER BY TradeDate DESC LIMIT 1;"));
           } catch (SQLException e) {e.printStackTrace();}

        if(!cPrice.isEmpty()) {
            float currPrice = Float.parseFloat(cPrice.get(0));
            if(!pPrice.isEmpty()) {
                float prevPrice = Float.parseFloat(pPrice.get(0));
                record.updateRecord(currPrice, prevPrice);
            }
            else
                record.updateRecord(currPrice, currPrice);
        }
    }

    @FXML private void updateStockData(){
        new Thread(()-> {
            for (LiveStockRecord curr : records) {
                curr.setUpdating(true);
                ArrayList<String> temp = null;
                try {
                    temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=" + curr.getSymbol() + "&interval=1min&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());
                } catch (IOException e) {
                }
                if (temp != null && temp.size() >= 2) {
                    System.out.println("Downloaded " + curr.getSymbol() + " 1 minute update \r\n" + temp.get(1));
                    StockRecordParser.importIntradayMarketData(temp, curr.getSymbol(), dh);

                    try {
                        temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + curr.getSymbol() + "&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());
                    } catch (IOException e) { }
                    StockRecordParser.importDailyMarketData(temp, curr.getSymbol(), dh);

                    updateRecord(curr);
                    curr.setUpdating(false);
                    curr.updateChart(dh);
                }
            }
        }).start();
    }
}