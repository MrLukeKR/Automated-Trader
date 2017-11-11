import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.json.JSONException;

import javax.swing.text.html.parser.DTD;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.Buffer;
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

    final int downloadInterval = 1;
    static final String IS_NUMERIC = "[-+]?\\d*\\.?\\d+";

    @FXML FlowPane stockList;
    @FXML ProgressBar liveStockProgressBar;
    @FXML FlowPane timePane;
    @FXML TextArea infoBox;
    @FXML VBox newsBox;
    @FXML ChoiceBox symbolChoiceBox;
    @FXML TextField amountField;
    @FXML Label stockValueLabel;
    @FXML Label currentBalanceLabel;
    @FXML Label totalBalanceLabel;

    @FXML
    public void initialize() throws SQLException, JSONException, InterruptedException {
        initialiseConnections();
        initialiseDatabase();
        TechnicalAnalyser.setDBHandler(dh);
        initialiseStocks();
        initialiseClocks();
        initialiseDisplay();
        updateNews();
        downloadArticles();
        updateStockValues();
        updateBankBalance();
        updateTotalWorth();

        TechnicalAnalyser.processUncalculated();

        for (LiveStockRecord stock : records) stock.updateChart(dh);

        startRealTime();
    }

    private void initialiseConnections() {
        System.out.println("Initialising Connections...");
        try {
            dh.init("agent", "0Y5q0m28pSB9jj2O");
        } catch (Exception e) {
        }
        avh.init("PBATJ7L9N8SNK835");

        NewsAPIHandler.authenticate("be7afde61f5e10bb20393025c35e50c7", "1ff9ab03aa8e5bd073345d70d588abde");
    }

    private void initialiseDatabase() {
        System.out.println("Initialising Database...");
        try {
            dh.executeCommand("CREATE DATABASE IF NOT EXISTS automated_trader;");//TODO: Allow login of root to create the initial agent user
            //dh.executeCommand("GRANT ALL ON automated_trader.* TO 'agent'@'%';");
            dh.executeCommand("USE automated_trader");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS indices (Symbol varchar(7) UNIQUE NOT NULL, Name text NOT NULL, StartedTrading date NOT NULL, CeasedTrading date, TwitterUsername varchar(15), PRIMARY KEY (Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS dailystockprices (Symbol varchar(7) NOT NULL, TradeDate date NOT NULL, OpenPrice double NOT NULL, HighPrice double NOT NULL, LowPrice double NOT NULL, ClosePrice double NOT NULL, TradeVolume bigint(20) NOT NULL, PRIMARY KEY (Symbol,TradeDate), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS dailytechnicalindicators (Symbol varchar(7) NOT NULL, TradeDate date NOT NULL, EMA12 double DEFAULT NULL, EMA26 double DEFAULT NULL, MACD double DEFAULT NULL, RSI double DEFAULT NULL, StoOsc double DEFAULT NULL, OBV double DEFAULT NULL, ADX double DEFAULT NULL, PRIMARY KEY (Symbol,TradeDate), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS intradaystockprices (Symbol varchar(7) NOT NULL, TradeDateTime datetime NOT NULL, OpenPrice double NOT NULL, HighPrice double NOT NULL, LowPrice double NOT NULL, ClosePrice double NOT NULL, TradeVolume bigint(20) NOT NULL, PRIMARY KEY (Symbol,TradeDateTime), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS apimanagement (Name varchar(20) NOT NULL, DailyLimit int default 0, Delay int default 0);");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS apicalls (Name varchar(20) NOT NULL, Date date NOT NULL, Calls int default 0, PRIMARY KEY (Name, Date), FOREIGN KEY (Name) REFERENCES apimanagement (Name));");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS ngrams (ID int AUTO_INCREMENT NOT NULL, Gram varchar(1000) NOT NULL UNIQUE, N int NOT NULL, Increase int DEFAULT 0, Decrease int DEFAULT 0, Occurrences int DEFAULT 1, Blacklisted BIT DEFAULT 0, Processed BIT DEFAULT 0,  PRIMARY KEY (ID));");

            if(!System.getProperty("os.name").contains("Linux")) //MySQL 5.6 or lower doesn't support large unique keys
                dh.executeCommand("CREATE TABLE IF NOT EXISTS newsarticles (ID INT AUTO_INCREMENT NOT NULL, Symbol varchar(7) NOT NULL, Headline varchar(255) NOT NULL, Description text, Content longtext, Published datetime NOT NULL, URL text, Mood double DEFAULT 0.5, PRIMARY KEY (ID), UNIQUE (Symbol, Headline), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");

            dh.executeCommand("CREATE TABLE IF NOT EXISTS tradetransactions (ID INT AUTO_INCREMENT NOT NULL, TradeDateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, Type varchar(4) NOT NULL, Symbol varchar(7) NOT NULL, Volume INT UNSIGNED NOT NULL DEFAULT 0, Price DOUBLE UNSIGNED NOT NULL,PRIMARY KEY (ID), FOREIGN KEY (Symbol) REFERENCES indices(Symbol));");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS banktransactions (ID INT AUTO_INCREMENT NOT NULL, TradeDateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, Amount double SIGNED NOT NULL, PRIMARY KEY (ID));");

            int bankTransactions = Integer.parseInt(dh.executeQuery("SELECT COUNT(*) FROM banktransactions;").get(0));

            if (bankTransactions == 0) dh.executeCommand("INSERT INTO banktransactions(Amount) VALUES (10000);");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private void updateBankBalance(){
        try {
            float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));

            currentBalanceLabel.setText(String.valueOf(balance));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void updateStockValues(){
        float worth = 0;
        try{
            ArrayList<String> heldStocks = dh.executeQuery("SELECT symbol FROM tradetransactions GROUP BY symbol HAVING SUM(Volume) > 0;");

            for(String stock : heldStocks) {
                int volume = getHeldStocks(stock);
                float currPrice = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));
                worth += volume * currPrice;

                stockValueLabel.setText(String.valueOf(worth));
            }
        }catch(SQLException e){ e.printStackTrace();}
    }

    private void initialiseDisplay() {
        symbolChoiceBox.setItems(FXCollections.observableArrayList(stocks));

        for (String curr : stocks) {
            try {
                String name = dh.executeQuery("SELECT Name FROM indices WHERE Symbol='" + curr + "';").get(0);
                LiveStockRecord currRec = new LiveStockRecord(curr, name, dh);
                records.add(currRec);
                stockList.getChildren().add(currRec.getNode());
            } catch (SQLException e) { e.printStackTrace(); }
        }
    }

    public void printToInfoBox(String string) {

    }

    private void initialiseClocks() {
        clocks.add(new StockClock("NASDAQ", LocalTime.of(9, 30), LocalTime.of(16, 0), ZoneId.of("America/New_York")));
        clocks.add(new StockClock("London SE", LocalTime.of(8, 0), LocalTime.of(16, 30), ZoneId.of("Europe/London")));
        clocks.add(new StockClock("Tokyo SE", LocalTime.of(9, 0), LocalTime.of(15, 0), ZoneId.of("Asia/Tokyo"))); //TODO: Allow multiple open/close periods
        clocks.add(new StockClock("Hong Kong SE", LocalTime.of(9, 30), LocalTime.of(16, 0), ZoneId.of("Asia/Hong_Kong")));
        clocks.add(new StockClock("Australia SX", LocalTime.of(10, 0), LocalTime.of(16, 0), ZoneId.of("Australia/Canberra")));

        for (StockClock clock : clocks) timePane.getChildren().add(clock.getNode());
    }

    private void updateClocks() {
        new Thread(() -> {
            for (StockClock clock : clocks)
                clock.updateTime();
        }).start();
    }

    @FXML
    public void startRealTime() {
        new Thread(()-> {
            while (!quit) {
                try { TimeUnit.MILLISECONDS.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }

                int s = LocalTime.now().getSecond();
                int m = LocalTime.now().getMinute();
                int cycle = m % downloadInterval;

                double progressVal=((s+1) + cycle * 60) / (downloadInterval * 60.0f);

                liveStockProgressBar.setProgress(progressVal);
                updateClocks();

                if(cycle == 0 && s == 0) {
                    new Thread(()-> updateStockData()).start();
                    new Thread(() -> { try { updateNews(); } catch (Exception e) { e.printStackTrace(); }}).start();
                }
            }
        }).start();
    }

    private void updateNews() throws SQLException, JSONException, InterruptedException {
        if(System.getProperty("os.name").contains("Linux")) return;

        try { NewsAPIHandler.getHistoricNews(stocks, dh); } catch (IOException e) { e.printStackTrace(); }

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

        new Thread(() -> {
            for (String symbol : stocks) {
                File file = new File("res/historicstocks/" + symbol + ".csv");
                try {
                    StockRecordParser.importDailyMarketData(file, symbol, dh);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                System.out.println("Successfully committed complete Yahoo records of " + symbol + " to the database!");

                ArrayList<String> temp = null;
                try {
                    temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&datatype=csv&outputsize=full&apikey=" + avh.getApiKey());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println("Downloaded full history of " + symbol);
                StockRecordParser.importDailyMarketData(temp, symbol, dh);
                System.out.println("Successully committed " + symbol + " full history to the database!");
            }
        }).start();

        for (String symbol : stocks) {
            try {
                ArrayList<String> temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=" + symbol + "&interval=1min&datatype=csv&outputsize=full&apikey=" + avh.getApiKey());
                System.out.println("Downloaded intraday history of " + symbol);
                StockRecordParser.importIntradayMarketData(temp, symbol, dh);
                System.out.println("Successully committed " + symbol + " intraday history to the database!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void buyStock(){
        if(!amountField.getText().isEmpty() && amountField.getText().matches(IS_NUMERIC)) {
            try {
                int amount = Integer.parseInt(amountField.getText());

                String stock = symbolChoiceBox.getValue().toString();
                float cost = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + stock  + "' ORDER BY TradeDateTime DESC LIMIT 1" ).get(0));

                float totalCost = cost * amount;

                float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));

                if(totalCost <= balance) {
                    dh.executeCommand("INSERT INTO banktransactions(Amount) VALUES (" + -totalCost + ")");
                    dh.executeCommand("INSERT INTO tradetransactions(Type,Symbol,Volume,Price) VALUES ('BUY'," +
                            "'" + stock + "'," +
                            amount + "," +
                            cost +
                    ");");

                    Platform.runLater(() -> {
                        updateBankBalance();
                        updateStockValues();
                        updateTotalWorth();
                    });
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }

        amountField.clear();
    }

    private void updateTotalWorth(){
        float bankBalance = Float.parseFloat(currentBalanceLabel.getText());
        float stockWorth = Float.parseFloat(stockValueLabel.getText());

        totalBalanceLabel.setText(String.valueOf(bankBalance + stockWorth));
    }

    private void buyStock(String name, int amount){

    }

    private int getHeldStocks(String stock){
        int bought = 0;
        int sold = 0;
        try {
            bought = Integer.parseInt(dh.executeQuery("SELECT COALESCE(SUM(Volume),0) FROM tradetransactions WHERE Symbol='"+ stock +"' AND Type='BUY';").get(0));
            sold = Integer.parseInt(dh.executeQuery("SELECT COALESCE(SUM(Volume),0) FROM tradetransactions WHERE Symbol='"+ stock +"' AND Type='SELL';").get(0));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return bought-sold;
    }

    @FXML
    private void sellStock(){
        if(!amountField.getText().isEmpty() && amountField.getText().matches(IS_NUMERIC)) {
            try {
                int amount = Integer.parseInt(amountField.getText());

                String stock = symbolChoiceBox.getValue().toString();
                float cost = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + stock  + "' ORDER BY TradeDateTime DESC LIMIT 1" ).get(0));

                float totalCost = cost * amount;


                int available = getHeldStocks(stock);

                if(amount <= available) {
                    dh.executeCommand("INSERT INTO banktransactions(Amount) VALUES (" + totalCost + ")");
                    dh.executeCommand("INSERT INTO tradetransactions(Type,Symbol,Volume,Price) VALUES ('SELL'," +
                            "'" + stock + "'," +
                            amount + "," +
                            cost +
                            ");");

                    Platform.runLater(() -> {
                        updateBankBalance();
                        updateStockValues();
                        updateTotalWorth();
                    });
                }
            } catch (SQLException e) { e.printStackTrace(); }
        }

        amountField.clear();
    }

    private void sellStock(String name, int amount){

    }

    private void updateStockData(){
        for (LiveStockRecord curr : records) {
            curr.setUpdating(true);
            ArrayList<String> temp = null;
            try {
                temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=" + curr.getSymbol() + "&interval=1min&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());
            } catch (IOException e) {
            }

            if (temp != null && temp.size() >= 2) {
                System.out.println("Downloaded " + curr.getSymbol() + " 1 minute update: " + temp.get(1));
                StockRecordParser.importIntradayMarketData(temp, curr.getSymbol(), dh);

                try {
                    temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + curr.getSymbol() + "&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());
                } catch (IOException e) { }

                StockRecordParser.importDailyMarketData(temp, curr.getSymbol(), dh);

                curr.updateRecord(dh);
                curr.setUpdating(false);
                curr.updateChart(dh);
            }

            Platform.runLater(() -> {
                updateStockValues();
                updateTotalWorth();
            });
        }
    }

    private String downloadArticle(int ID, String url, String filePath) throws IOException {
        URL site = new URL(url);
        String fileURI = filePath + "/" + ID + ".html";
        BufferedReader br = new BufferedReader(new InputStreamReader(site.openStream()));

        String input;

        File file = new File(fileURI);
        if(!file.exists()) file.createNewFile();

        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        BufferedWriter bw =  new BufferedWriter(fw);

        while((input = br.readLine()) != null)
            bw.write(input);

        bw.close();
        br.close();

        return fileURI;
    }

    private void downloadArticles() {
        System.out.println("Downloading missing news article content for entire database (in background)...");
        new Thread(() -> {
            ArrayList<String> undownloadedArticles = null;
            try {
                undownloadedArticles = dh.executeQuery("SELECT ID, URL FROM newsarticles WHERE Content IS NULL");
            } catch (SQLException e) { e.printStackTrace(); }

            if (undownloadedArticles == null || undownloadedArticles.isEmpty()) return;

            String[] splitArticle = null;

            for (String article : undownloadedArticles) {
                splitArticle = article.split(",");
                int id = Integer.parseInt(splitArticle[0]);

                try {
                    String site = downloadArticle(id, splitArticle[1], "res/newsarticles");
                    dh.executeCommand("UPDATE newsarticles SET Content='" + site + "' WHERE ID = " + id + ";");
                } catch (Exception e) { e.printStackTrace(); }
            }
        }).start();
    }
}