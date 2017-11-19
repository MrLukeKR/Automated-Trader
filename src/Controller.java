import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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
    @FXML Label stockValueLabel;
    @FXML Label currentBalanceLabel;
    @FXML Label totalBalanceLabel;
    @FXML
    Label cutoffLabel;
    @FXML
    Label targetLabel;
    @FXML
    Label profitLossLabel;
    @FXML
    PieChart diversificationChart;

    boolean updating = false;

    @FXML
    public void initialize() throws SQLException, JSONException, InterruptedException {
        initialiseConnections();
        initialiseDatabase();
        TechnicalAnalyser.initialise(dh);
        NaturalLanguageProcessor.initialise(dh);
        initialiseStocks();

        initialiseClocks();
        initialiseDisplay();
        updateStockValues();
        updateBankBalance();
        updateTotalWorth();

        for (LiveStockRecord stock : records) stock.updateChart(dh);

        startRealTime();

        new Thread(() -> {
            processYahooHistories();
            downloadStockHistory();
            TechnicalAnalyser.processUncalculated();
        }).start();

        new Thread(() -> {
            try {
                NaturalLanguageProcessor.enumerateNGramsFromArticles(2);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                updateNews();
                downloadArticles();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
            dh.executeCommand("CREATE DATABASE IF NOT EXISTS automated_trader;"); //TODO: Allow login of root to create the initial agent user
            //dh.executeCommand("GRANT ALL ON automated_trader.* TO 'agent'@'%';");
            dh.executeCommand("USE automated_trader");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS indices (Symbol varchar(7) UNIQUE NOT NULL, Name text NOT NULL, StartedTrading date NOT NULL, CeasedTrading date, TwitterUsername varchar(15), PRIMARY KEY (Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS dailystockprices (Symbol varchar(7) NOT NULL, TradeDate date NOT NULL, OpenPrice double NOT NULL, HighPrice double NOT NULL, LowPrice double NOT NULL, ClosePrice double NOT NULL, TradeVolume bigint(20) NOT NULL, PRIMARY KEY (Symbol,TradeDate), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS dailytechnicalindicators (Symbol varchar(7) NOT NULL, TradeDate date NOT NULL, EMA12 double DEFAULT NULL, EMA26 double DEFAULT NULL, MACD double DEFAULT NULL, RSI double DEFAULT NULL, StoOsc double DEFAULT NULL, OBV double DEFAULT NULL, ADX double DEFAULT NULL, PRIMARY KEY (Symbol,TradeDate), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS intradaystockprices (Symbol varchar(7) NOT NULL, TradeDateTime datetime NOT NULL, OpenPrice double NOT NULL, HighPrice double NOT NULL, LowPrice double NOT NULL, ClosePrice double NOT NULL, TradeVolume bigint(20) NOT NULL, PRIMARY KEY (Symbol,TradeDateTime), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS apimanagement (Name varchar(20) NOT NULL, DailyLimit int default 0, Delay int default 0, PRIMARY KEY (Name));");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS apicalls (Name varchar(20) NOT NULL, Date date NOT NULL, Calls int default 0, PRIMARY KEY (Name, Date), FOREIGN KEY (Name) REFERENCES apimanagement (Name));");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS ngrams (Gram varchar(1000) NOT NULL UNIQUE, N int NOT NULL, Increase int DEFAULT 0, Decrease int DEFAULT 0, Occurrences int DEFAULT 0 NOT NULL, Documents int DEFAULT 1 NOT NULL, Blacklisted BIT DEFAULT 0, PRIMARY KEY (Gram));");

            if(!System.getProperty("os.name").contains("Linux")) //MySQL 5.6 or lower doesn't support large unique keys
                dh.executeCommand("CREATE TABLE IF NOT EXISTS newsarticles (ID INT AUTO_INCREMENT NOT NULL, Symbol varchar(7) NOT NULL, Headline varchar(255) NOT NULL, Description text, Content longtext, Published datetime NOT NULL, URL varchar(1000), Blacklisted BIT DEFAULT 0 NOT NULL, Redirected BIT DEFAULT 0 NOT NULL, Duplicate BIT DEFAULT 0 NOT NULL, Enumerated BIT DEFAULT 0 NOT NULL, Processed BIT DEFAULT 0 NOT NULL, Mood double DEFAULT 0.5, PRIMARY KEY (ID), UNIQUE (Symbol, URL), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");

            dh.executeCommand("CREATE TABLE IF NOT EXISTS tradetransactions (ID INT AUTO_INCREMENT NOT NULL, TradeDateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, Type varchar(4) NOT NULL, Symbol varchar(7) NOT NULL, Volume INT UNSIGNED NOT NULL DEFAULT 0, Price DOUBLE UNSIGNED NOT NULL,PRIMARY KEY (ID), FOREIGN KEY (Symbol) REFERENCES indices(Symbol));");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS banktransactions (ID INT AUTO_INCREMENT NOT NULL, TradeDateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, Amount double SIGNED NOT NULL, PRIMARY KEY (ID));");

            int bankTransactions = Integer.parseInt(dh.executeQuery("SELECT COUNT(*) FROM banktransactions;").get(0));

            if (bankTransactions == 0) dh.executeCommand("INSERT INTO banktransactions(Amount) VALUES (10000);");

            int apis = Integer.parseInt(dh.executeQuery("SELECT COUNT(*) FROM apimanagement;").get(0));

            if (apis == 0)
                dh.executeCommand("INSERT INTO apimanagement VALUES ('INTRINIO',500,0),('AlphaVantage',0,1667);");
        } catch (SQLException e) {
            System.err.println(e.getMessage());
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
        infoBox.appendText(string + "\r\n");
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
        new Thread(() -> {

            while (!quit) {
                try { TimeUnit.MILLISECONDS.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
                updateClocks();

                int s = LocalTime.now().getSecond();
                int m = LocalTime.now().getMinute();
                int cycle = m % downloadInterval;

                if(cycle == 0 && s == 0) {
                    new Thread(() -> updateStockData()).start();
                    //new Thread(() -> { try { updateNews(); } catch (Exception e) { e.printStackTrace(); }}).start();
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
    }

    private void processYahooHistories() {
        for (String symbol : stocks) {
            File file = new File("res/historicstocks/" + symbol + ".csv");

            if (file.exists())
                try {
                    StockRecordParser.importDailyMarketData(file, symbol, dh);
                    System.out.println("Successfully committed complete Yahoo records of " + symbol + " to the database!");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            else
                System.err.println("No Yahoo history available for " + symbol);
        }
    }

    private void downloadStockHistory() {
        for (String symbol : stocks) {
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
    private void buyStock(int amount, String stock) throws SQLException {
        float cost = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));
        float totalCost = cost * amount;
        float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));

        if (totalCost <= balance) {
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
    }

    private void updateTotalWorth(){
        float bankBalance = Float.parseFloat(currentBalanceLabel.getText());
        float stockWorth = Float.parseFloat(stockValueLabel.getText());

        totalBalanceLabel.setText(String.valueOf(bankBalance + stockWorth));
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
    private void sellStock(int amount, String stock) throws SQLException {

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
    }

    private void updateStockData(){
        if (updating) return;
        updating = true;
        for (LiveStockRecord curr : records) {
            curr.setUpdating(true);
            ArrayList<String> temp = null;
            try {
                temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=" + curr.getSymbol() + "&interval=1min&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());
            } catch (IOException e) {
            }

            if (temp != null && temp.size() >= 2) {
                StockRecordParser.importIntradayMarketData(temp, curr.getSymbol(), dh);
                System.out.println("Downloaded " + curr.getSymbol() + " 1 minute update: " + temp.get(1));

                try {
                    temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + curr.getSymbol() + "&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());


                    StockRecordParser.importDailyMarketData(temp, curr.getSymbol(), dh);
                    System.out.println("Downloaded " + curr.getSymbol() + " current daily close price: " + temp.get(1));

                    curr.updateRecord(dh);
                    curr.setUpdating(false);
                    curr.updateChart(dh);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Platform.runLater(() -> {
                updateStockValues();
                updateTotalWorth();
            });
        }
        updating = false;
    }

    private String downloadArticle(String url) throws IOException, InterruptedException {
        URL site = new URL(url);
        HttpURLConnection.setFollowRedirects(true);
        HttpURLConnection conn = (HttpURLConnection) site.openConnection(); //Written by https://stackoverflow.com/questions/15057329/how-to-get-redirected-url-and-content-using-httpurlconnection
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");

        conn.connect();

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

        String input;
        StringBuilder html = new StringBuilder();

        while ((input = br.readLine()) != null)
            html.append(input);

        conn.disconnect();
        br.close();

        Document doc = Jsoup.parse(html.toString());
        Elements p = doc.getElementsByTag("p");

        String strippedHTML = "";

        int i = 0;
        for (Element el : p) {
            strippedHTML += el.text();
            if (i++ < p.size()) strippedHTML += " ";
        }

        if (html.toString().toLowerCase() == "redirect")
            return "redirect";

        String cleanHTML = Jsoup.clean(strippedHTML, Whitelist.basic()).replaceAll("'", "").trim();

        if (cleanHTML.isEmpty())
            return null;

        return cleanHTML;
    }

    private void downloadArticles() throws SQLException {
        System.out.println("Downloading missing news article content for entire database (in background)...");
            ArrayList<String> undownloadedArticles = null;
            try {
                undownloadedArticles = dh.executeQuery("SELECT ID, URL FROM newsarticles WHERE Content IS NULL AND Blacklisted = 0 AND Redirected = 0 AND Duplicate = 0 AND URL != \"\";");
            } catch (SQLException e) { e.printStackTrace(); }

            if (undownloadedArticles == null || undownloadedArticles.isEmpty()) return;

            String[] splitArticle = null;

            for (String article : undownloadedArticles) {
                splitArticle = article.split(",");
                int id = Integer.parseInt(splitArticle[0]);

                System.out.println("Downloading news article " + splitArticle[0] + ": " + splitArticle[1]);

                String site = null;
                try {
                    site = downloadArticle(splitArticle[1]);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    if (site != null)
                        if (site == "redirect")
                            dh.executeCommand("UPDATE newsarticles SET Redirected = 1 WHERE ID = " + id + ";");
                        else
                            dh.executeCommand("UPDATE newsarticles SET Content='" + site + "' WHERE ID = " + id + ";");
                    else
                        dh.executeCommand("UPDATE newsarticles SET Blacklisted = 1 WHERE ID = " + id + ";"); //Blacklist if the document could not be retrieved
                } catch (Exception e) {
                    e.printStackTrace();
                    dh.executeCommand("UPDATE newsarticles SET Blacklisted = 1 WHERE ID = " + id + ";"); //Blacklist if the Content causes SQL error (i.e. truncation)
                }
            }
    }
}