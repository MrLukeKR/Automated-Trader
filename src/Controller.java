import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
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
    static final String IS_NUMERIC = "[-+]?\\d*\\.?\\d+";
    static DatabaseHandler dh = new DatabaseHandler();
    static DatabaseHandler mdh = new DatabaseHandler(); //Manual DB (No auto-commit)
    static AlphaVantageHandler avh = new AlphaVantageHandler();
    static boolean quit = false;
    final int downloadInterval = 1;
    ArrayList<String> stocks = new ArrayList<>();
    ArrayList<LiveStockRecord> records = new ArrayList<>();
    ArrayList<StockClock> clocks = new ArrayList<>();
    @FXML
    FlowPane stockList;
    @FXML
    ProgressBar liveStockProgressBar;
    @FXML
    FlowPane timePane;
    @FXML
    TextArea infoBox;
    @FXML
    VBox newsBox;
    @FXML
    Label stockValueLabel;
    @FXML
    Label currentBalanceLabel;
    @FXML
    Label totalBalanceLabel;
    @FXML
    Label cutoffLabel;
    @FXML
    Label targetLabel;
    @FXML
    Label profitLossLabel;
    @FXML PieChart allocationChart;
    @FXML PieChart componentChart;
    @FXML Circle newsFeedAvailability;
    @FXML
    Circle nlpAvailability;
    @FXML ProgressBar newsFeedProgress;
    @FXML ProgressBar nlpProgress;
    @FXML VBox stockBox;

    boolean priceUpdating = false;
    boolean newsUpdating = false;

    static public void updateProgress(double current, double total, ProgressBar pb) {
        final double val = current / total;
        if (current == 0 || current == total)
            Platform.runLater(() -> pb.setVisible(false));
        else if (!pb.isVisible())
            Platform.runLater(() -> pb.setVisible(true));

        Platform.runLater(() -> pb.setProgress(val));
    }

    private void calculateLossCutoff(double percentage) throws SQLException {
        double amount = Double.parseDouble(dh.executeQuery("SELECT COALESCE(SUM(Amount),0) FROM banktransactions WHERE Type='DEPOSIT';").get(0)) * (1 - percentage);

        cutoffLabel.setText(String.valueOf(amount));
    }

    static public void updateProgress(double value, ProgressBar pb) {
        final double val = value;
        if (value == 0 || value == 1)
            Platform.runLater(() -> pb.setVisible(false));
        else if (!pb.isVisible())
            Platform.runLater(() -> pb.setVisible(true));

        Platform.runLater(() -> pb.setProgress(val));
    }

    private void initialiseConnections() {
        System.out.println("Initialising Connections...");
        try {
            dh.init("agent", "0Y5q0m28pSB9jj2O");
            mdh.init("agent", "0Y5q0m28pSB9jj2O");
            mdh.executeCommand("USE automated_trader"); //TODO: Fix the init calls for databases

        } catch (Exception e) {
        }
        avh.init("PBATJ7L9N8SNK835");

        NewsAPIHandler.authenticate("be7afde61f5e10bb20393025c35e50c7", "1ff9ab03aa8e5bd073345d70d588abde");
    }

    @FXML
    public void initialize() throws SQLException, JSONException, InterruptedException {
        initialiseConnections();
        initialiseDatabase();

        TechnicalAnalyser.initialise(dh);
        NaturalLanguageProcessor.initialise(mdh);

        initialiseStocks();
        initialiseClocks();
        initialiseDisplay();

        updateDailyStockData();

        updateStockValues();
        updateBankBalance();
        updateTotalWorth();
        updateProfitLoss();
        updateAllocationChart();
        updateComponentChart();
        updateStocksOwned();

        calculateLossCutoff(0.1);
        checkServices();

        startRealTime();

        new Thread(() -> {
            try {processYahooHistories();} catch (SQLException e) { e.printStackTrace(); }
            downloadStockHistory();
        }).start();

        new Thread(() -> {
            try {
                updateNews();
                downloadArticles();
                NaturalLanguageProcessor.enumerateSentencesFromArticles(nlpProgress);
                NaturalLanguageProcessor.determineUselessSentences();
                NaturalLanguageProcessor.enumerateNGramsFromArticles(3, nlpProgress);
            } catch (Exception e) {e.printStackTrace();}
        }).start();
        //new Thread(() -> TechnicalAnalyser.processUncalculated()).start();
    }

    private void updateBankBalance() {
        try {
            float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));

            currentBalanceLabel.setText(String.valueOf(balance));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void updateStockValues() {
        float worth = 0;
        try {
            ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol FROM tradetransactions GROUP BY symbol HAVING SUM(Volume) > 0;");

            for (String stock : heldStocks) {
                int volume = getHeldStocks(stock);
                float currPrice = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));
                worth += volume * currPrice;

                stockValueLabel.setText(String.valueOf(worth));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initialiseDisplay() {
        for (String curr : stocks) {
            try {
                String name = dh.executeQuery("SELECT Name FROM indices WHERE Symbol='" + curr + "';").get(0);
                LiveStockRecord currRec = new LiveStockRecord(curr, name, dh);
                records.add(currRec);
                stockList.getChildren().add(currRec.getNode());
            } catch (SQLException e) {
                e.printStackTrace();
            }
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

    private void checkServices() throws SQLException {
        int newsCalls = 0;
        int callLimit = Integer.parseInt(dh.executeQuery("SELECT COALESCE(DailyLimit,0) FROM apimanagement WHERE Name='INTRINIO';").get(0));

        ArrayList<String> calls = dh.executeQuery("SELECT Calls FROM apicalls WHERE Name='INTRINIO' AND Date=CURDATE()");

        if(!calls.isEmpty())
            newsCalls = Integer.parseInt(calls.get(0)); //TODO: See if this can be done via Event triggers

        if (newsCalls < callLimit) {
            newsFeedAvailability.setFill(Color.GREEN);
            newsFeedAvailability.setStroke(Color.GREEN);
        } else {
            newsFeedAvailability.setFill(Color.RED);
            newsFeedAvailability.setStroke(Color.RED);
        }

        //Green - Online
        //Orange - Waiting
        //Red - Offline/Error

        if (newsUpdating) {
            nlpAvailability.setFill(Color.ORANGE);
            nlpAvailability.setStroke(Color.ORANGE);
        } else {
            nlpAvailability.setFill(Color.GREEN);
            nlpAvailability.setStroke(Color.GREEN);
        }
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
            dh.executeCommand("CREATE TABLE IF NOT EXISTS ngrams (Hash varchar(32) NOT NULL PRIMARY KEY, Gram text NOT NULL, N int NOT NULL, Increase int DEFAULT 0, Decrease int DEFAULT 0, Occurrences int DEFAULT 0 NOT NULL, Documents int DEFAULT 1 NOT NULL, Blacklisted BIT DEFAULT 0);");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS portfolio (Symbol varchar(7) NOT NULL, Allocation double NOT NULL, Held int NOT NULL DEFAULT 0, Investment double NOT NULL DEFAULT 0, PRIMARY KEY (Symbol), FOREIGN KEY (Symbol) REFERENCES indices(Symbol));");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS sentences (Hash varchar(32) NOT NULL PRIMARY KEY, Sentence text NOT NULL, Occurrences int DEFAULT 0 NOT NULL, Documents int DEFAULT 0 NOT NULL, Blacklisted BIT DEFAULT 0);");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS newsarticles (ID INT AUTO_INCREMENT NOT NULL, Symbol varchar(7) NOT NULL, Headline text NOT NULL, Description text, Content longtext, Published datetime NOT NULL, URL varchar(1000), Blacklisted BIT DEFAULT 0 NOT NULL, Redirected BIT DEFAULT 0 NOT NULL, Duplicate BIT DEFAULT 0 NOT NULL, Enumerated BIT DEFAULT 0 NOT NULL, Tokenised BIT DEFAULT 0 NOT NULL, Processed BIT DEFAULT 0 NOT NULL, Mood double DEFAULT 0.5, PRIMARY KEY (ID), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS tradetransactions (ID INT AUTO_INCREMENT NOT NULL, TradeDateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, Type varchar(4), Symbol varchar(7) NOT NULL, Volume INT UNSIGNED NOT NULL DEFAULT 0, Price DOUBLE UNSIGNED NOT NULL,PRIMARY KEY (ID), FOREIGN KEY (Symbol) REFERENCES indices(Symbol));");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS banktransactions (ID INT AUTO_INCREMENT NOT NULL, TradeDateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, Type varchar(10), Amount double SIGNED NOT NULL, PRIMARY KEY (ID));");

            int bankTransactions = Integer.parseInt(dh.executeQuery("SELECT COUNT(*) FROM banktransactions;").get(0));

            if (bankTransactions == 0) dh.executeCommand("INSERT INTO banktransactions(Amount, Type) VALUES (10000, 'DEPOSIT');");

            int apis = Integer.parseInt(dh.executeQuery("SELECT COUNT(*) FROM apimanagement;").get(0));

            if (apis == 0)
                dh.executeCommand("INSERT INTO apimanagement VALUES ('INTRINIO',500,0),('AlphaVantage',0,1667);");
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    @FXML public void initialiseStocks() {
        try {
            stocks = dh.executeQuery("SELECT Symbol FROM indices");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void processYahooHistories() throws SQLException {
        for (String symbol : stocks) {
            if (!dh.executeQuery("SELECT COALESCE(COUNT(*),0) FROM dailystockprices WHERE Symbol='" + symbol + "';").isEmpty())
                return;
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
            } catch (Exception e) {e.printStackTrace();} //TODO: Process offline (503, 500 errors) handling

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
            } catch (Exception e) {e.printStackTrace();}
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
                try {
                    updateProfitLoss();
                } catch (SQLException e) {  e.printStackTrace(); }
            });
        }
    }

    private void updateStocksOwned() throws SQLException {
        ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol, SUM(Volume) FROM tradetransactions GROUP BY Symbol HAVING SUM(Volume) > 0");

        stockBox.getChildren().clear();

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            stockBox.getChildren().add(new Label(splitStock[0] + " x" + splitStock[1]));
        }
    }

    private float updateProfitLoss() throws SQLException {
        float nonLiquidBalance = Float.parseFloat(dh.executeQuery("SELECT COALESCE(SUM(AMOUNT),0) FROM banktransactions WHERE Type!='DEPOSIT'").get(0));
        float potentialTotal = 0;

        ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol, SUM(Volume) FROM tradetransactions GROUP BY Symbol HAVING SUM(Volume) > 0");

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            potentialTotal += Float.parseFloat(splitStock[1]) * Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + splitStock[0] + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));
        }

        float total = nonLiquidBalance + potentialTotal;

        profitLossLabel.setText(String.valueOf(total));
        if (total > 0)
            profitLossLabel.setTextFill(Color.GREEN);
        else if (total == 0)
            profitLossLabel.setTextFill(Color.BLACK);
        else
            profitLossLabel.setTextFill(Color.RED);

        return total;
    }

    private void updateTotalWorth(){
        float bankBalance = Float.parseFloat(currentBalanceLabel.getText());
        float stockWorth = Float.parseFloat(stockValueLabel.getText());

        totalBalanceLabel.setText(String.valueOf(bankBalance + stockWorth));
    }

    private int getHeldStocks(String stock){
        try {
            return Integer.parseInt(dh.executeQuery("SELECT COALESCE(SUM(Volume),0) FROM tradetransactions WHERE Symbol='" + stock + "';").get(0));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    @FXML
    public void startRealTime() {
        new Thread(() -> {

            while (!quit) {
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                updateClocks();

                int s = LocalTime.now().getSecond();
                int m = LocalTime.now().getMinute();
                int h = LocalTime.now().getHour();
                int cycle = m % downloadInterval;

                if (cycle == 0 && s == 0) {
                    if (m == 0 && h == 0) new Thread(() -> updateDailyStockData()).start();
                    new Thread(() -> {
                        updateIntradayStockData();
                        try {
                            checkServices();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }).start();

                    //new Thread(() -> { try { updateNews(); } catch (Exception e) { e.printStackTrace(); }}).start();

                }
            }
        }).start();
    }

    private void updateComponentChart() throws SQLException {
        ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol, Held FROM portfolio;");

        ArrayList<PieChart.Data> piechartData = new ArrayList<>();

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            piechartData.add(new PieChart.Data(splitStock[0], Integer.parseInt(splitStock[1])));
        }

        componentChart.getData().clear();
        componentChart.getData().addAll(piechartData);
    }

    private void updateAllocationChart() throws SQLException {
        int total = Integer.parseInt(dh.executeQuery("SELECT COALESCE(SUM(Allocation),0) FROM portfolio;").get(0));

        ArrayList<String> allowance = dh.executeQuery("SELECT Symbol, Allocation FROM portfolio;");

        ArrayList<PieChart.Data> piechartData = new ArrayList<>();

        for (String stock : allowance) {
            String[] splitStock = stock.split(",");
            float allocation = Float.parseFloat(splitStock[1]);

            piechartData.add(new PieChart.Data(splitStock[0], allocation));
        }

        allocationChart.getData().clear();
        allocationChart.getData().addAll(piechartData);
    }

    private void updateNews() throws SQLException, JSONException, InterruptedException {
        if (newsUpdating) return;
        newsUpdating = true;

        try {
            NewsAPIHandler.getHistoricNews(stocks, dh, newsFeedProgress);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Platform.runLater(() -> {
            newsBox.getChildren().clear();

            ArrayList<String> symbols = null;
            ArrayList<String> headlines = null;
            try {
                symbols = dh.executeQuery("SELECT Symbol FROM newsarticles WHERE Published >= CURDATE() ORDER BY Published DESC");
                headlines = dh.executeQuery("SELECT Headline FROM newsarticles WHERE Published >= CURDATE() ORDER BY Published DESC");
            } catch (SQLException e) {
                e.printStackTrace();
            }

            for (int i = 0; i < headlines.size(); i++) {
                NewsRecord temp = new NewsRecord(symbols.get(i), headlines.get(i));
                newsBox.getChildren().add(temp.getNode());
            }
        });
        newsUpdating = false;
    }

    @FXML
    private void sellStock(int amount, String stock) throws SQLException {

        float cost = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));

        float totalCost = cost * amount;


        int available = getHeldStocks(stock);

        if (amount <= available) {
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
                try {
                    updateProfitLoss();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
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

    private void updateDailyStockData() {
        if (priceUpdating) return;
        priceUpdating = true;
        for (LiveStockRecord curr : records) {
            curr.setUpdating(true);
            ArrayList<String> temp = null;

            try {
                temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + curr.getSymbol() + "&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (temp != null && temp.size() > 1) {
                StockRecordParser.importDailyMarketData(temp, curr.getSymbol(), dh);
                System.out.println("Downloaded " + curr.getSymbol() + " current daily close price: " + temp.get(1));
            }
            curr.setUpdating(false);
        }
        priceUpdating = false;
    }

    private void updateIntradayStockData() {
        if (priceUpdating) return;
        priceUpdating = true;
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
            }

            curr.updateRecord(dh);
            curr.setUpdating(false);
            curr.updateChart(dh, false);

            Platform.runLater(() -> {
                updateStockValues();
                updateTotalWorth();
                try {
                    updateProfitLoss();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
        priceUpdating = false;
    }

    private void downloadArticles() throws SQLException {
        System.out.println("Downloading missing news article content...");
        newsFeedProgress.setProgress(0);
        newsFeedProgress.setVisible(true);
        ArrayList<String> undownloadedArticles = dh.executeQuery("SELECT ID, URL FROM newsarticles WHERE Content IS NULL AND Blacklisted = 0 AND Redirected = 0 AND Duplicate = 0 AND URL != \"\";");

        if (undownloadedArticles == null || undownloadedArticles.isEmpty()) return;

        String[] splitArticle = null;
        double i = 0, t = undownloadedArticles.size();
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
            newsFeedProgress.setProgress(i++ / t);
        }

        newsFeedProgress.setVisible(false);
    }
}