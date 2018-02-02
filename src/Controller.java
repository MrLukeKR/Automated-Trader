import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Controller {
    static final String IS_NUMERIC = "[-+]?\\d*\\.?\\d+";
    static DatabaseHandler dh = new DatabaseHandler();
    static DatabaseHandler sqdh = new DatabaseHandler();
    static DatabaseHandler nlpdh = new DatabaseHandler();
    static DatabaseHandler tadh = new DatabaseHandler();
    static DatabaseHandler nddh = new DatabaseHandler();
    static DatabaseHandler pmdh = new DatabaseHandler();

    static AlphaVantageHandler avh = new AlphaVantageHandler();
    static boolean quit = false;
    final int downloadInterval = 1;
    ArrayList<String> stocks = new ArrayList<>();
    ArrayList<LiveStockRecord> records = new ArrayList<>();
    ArrayList<StockClock> clocks = new ArrayList<>();


    @FXML
    ComboBox<String> stockDropdown;
    @FXML
    FlowPane stockList;
    @FXML
    ProgressBar stockFeedProgress;
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
    static private Thread mainThread;
    @FXML
    PieChart allocationChart;
    @FXML
    PieChart componentChart;
    @FXML
    Circle nlpAvailability;
    @FXML
    Circle newsFeedAvailability;
    @FXML
    ProgressBar newsFeedProgress;
    @FXML
    ProgressBar nlpProgress;
    @FXML
    ComboBox autonomyLevelDropdown;
    @FXML
    FlowPane autonomousToolbox;
    @FXML
    FlowPane manualToolbox;
    @FXML
    Label lossCutoffPercentageLabel;
    @FXML
    Label profitTargetPercentageLabel;
    @FXML
    TextField lossCutoffField;
    @FXML
    TextField profitTargetField;
    @FXML
    LineChart<Integer, Double> portfolioChart;
    @FXML
    ProgressBar technicalAnalyserProgress;
    @FXML
    Circle technicalAnalyserAvailability;
    @FXML
    TextField stockAmountField;
    @FXML
    Button sellButton;
    @FXML
    Button buyButton;
    @FXML
    Button exportToMLFileButton;
    @FXML
    Button rebalanceButton;

    boolean priceUpdating = false;
    boolean newsUpdating = false;
    @FXML
    VBox stockBox;

    public static void shutdown() throws SQLException, InterruptedException {
        quit = true;
        if (mainThread.isAlive())
            mainThread.join();

        dh.close();
        nddh.close();
        nlpdh.close();
        tadh.close();
        sqdh.close();
        pmdh.close();
    }

    @FXML
    private void enableAmountField() {
        stockAmountField.setDisable(false);
    }

    @FXML
    private void exportToMLFile() {
        new Thread(() -> {
            Platform.runLater(() -> exportToMLFileButton.setDisable(true));
            System.out.println("Exporting to ML File...");
            ArrayList<String> priceValues = null;
            try {
                priceValues = dh.executeQuery("SELECT * FROM dailystockprices WHERE SMA10 is not null AND EMA10 is not null AND MACD is not null AND RSI10 is not null AND ADX10 is not null AND CCI10 is not null AND AD is not null AND OBV is not null ORDER BY Symbol ASC, TradeDate ASC ");
            } catch (SQLException e) {
                e.printStackTrace();
            }

            for (String record : priceValues) {
                String[] splitString = record.split(",");
                String symbolBytes = Utils.stringToByteValue(splitString[0]);
            }
            Platform.runLater(() -> exportToMLFileButton.setDisable(false));
        }).start();
    }

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

        Platform.runLater(() -> cutoffLabel.setText(String.valueOf(amount)));
    }

    static public void updateProgress(double value, ProgressBar pb) {
        final double val = value;
        if (value == 0 || value == 1)
            Platform.runLater(() -> pb.setVisible(false));
        else if (!pb.isVisible())
            Platform.runLater(() -> pb.setVisible(true));

        Platform.runLater(() -> pb.setProgress(val));
    }

    private void calculateTargetCutoff(double percentage) throws SQLException {
        double amount = Double.parseDouble(dh.executeQuery("SELECT COALESCE(SUM(Amount),0) FROM banktransactions WHERE Type='DEPOSIT';").get(0)) * (1 + percentage);

        Platform.runLater(() -> targetLabel.setText(String.valueOf(amount)));
    }

    private void initialiseConnections() {
        System.out.println("Initialising Connections...");
        try {
            dh.init("agent", "0Y5q0m28pSB9jj2O");
            nlpdh.init("NaturalLanguageProcessor", "p1pONM8zhI6GgCfy");
            tadh.init("TechnicalAnalyser", "n6qvdUkFOoFCxPq5");
            nddh.init("NewsDownloader", "wu0Ni6YF3yLTVp2A");
            sqdh.init("StockQuoteDownloader", "j2wbvx19Gg1Be22J");
            pmdh.init("PortfolioManager", "mAjwa22NdsrRihi4");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        avh.init("PBATJ7L9N8SNK835");
        NewsAPIHandler.authenticate("be7afde61f5e10bb20393025c35e50c7", "1ff9ab03aa8e5bd073345d70d588abde");
    }

    @FXML
    public void setAutonomyLevel() {
        String level = autonomyLevelDropdown.getValue().toString();
        boolean fullyAutonomous = level == "Full-Autonomy",
                semiAutonomous = level == "Semi-Autonomy";

        autonomousToolbox.setDisable(fullyAutonomous);
        manualToolbox.setDisable(fullyAutonomous || semiAutonomous);

        //TODO: Set autonomy settings
    }

    private void updateProfitLossChart() throws SQLException, ParseException {
        portfolioChart.getData().clear();
        //TODO: Refactor this to not regather all data each iteration
        portfolioChart.setAnimated(false);
        ArrayList<String> portfolioRecords = dh.executeQuery("SELECT Symbol, Held, Investment, LastUpdated FROM portfolio ORDER BY LastUpdated ASC;");

        Map<Long, Double> timeAndPrice = new TreeMap<>();

        DateFormat format = new SimpleDateFormat("yyyy-MM-dd");

        for (String record : portfolioRecords) {
            String[] splitRecord = record.split(",");

            double held = Double.parseDouble(splitRecord[1]),
                    cost = Double.parseDouble(splitRecord[2]);

            for (String iRecord : dh.executeQuery("SELECT ClosePrice, TradeDate FROM dailystockprices WHERE Symbol = '" + splitRecord[0] + "' AND TradeDate >= DATE('" + splitRecord[3] + "');")) {
                String[] splitIRecord = iRecord.split(",");

                double profitLoss = (Double.parseDouble(splitIRecord[0]) * held) - cost;
                long epoch = format.parse(splitIRecord[1]).getTime();

                timeAndPrice.put(epoch, timeAndPrice.getOrDefault(epoch, 0.0) + profitLoss);
            }
        }

        XYChart.Series<Integer, Double> profitLossData = new XYChart.Series<>();

        int i = 0;

        double currProfitLoss = 0;

        for (Long time : timeAndPrice.keySet()) {
            XYChart.Data<Integer, Double> point = new XYChart.Data(i, timeAndPrice.get(time));
            Rectangle rect = new Rectangle(0, 0);
            rect.setVisible(false);
            point.setNode(rect);

            profitLossData.getData().add(i++, point);

            if (i == timeAndPrice.size())
                currProfitLoss = timeAndPrice.get(time);
        }

        final double finalProfitLoss = currProfitLoss;

        Platform.runLater(() ->
        {
            portfolioChart.getData().add(profitLossData);

            if (finalProfitLoss < 0)
                profitLossData.nodeProperty().get().setStyle("-fx-stroke: red;   -fx-stroke-width: 1px;");
            else if (finalProfitLoss > 0)
                profitLossData.nodeProperty().get().setStyle("-fx-stroke: green; -fx-stroke-width: 1px;");
            else profitLossData.nodeProperty().get().setStyle("-fx-stroke: black; -fx-stroke-width: 1px;");
        });
    }


    @FXML
    public void manuallyRebalancePortfolio() {
        Alert alert = new Alert(Alert.AlertType.WARNING, "This will sell all of your currently held stocks. Please confirm.", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Sell All Stocks?");

        Optional<ButtonType> result = alert.showAndWait();

        if (result.get() == ButtonType.YES)
            new Thread(() -> {
                Platform.runLater(() -> rebalanceButton.setDisable(true));
                try {
                    rebalancePortfolio();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                Platform.runLater(() -> rebalanceButton.setDisable(false));
            }).start();
    }

    public void sellAllStock() throws SQLException {
        ArrayList<String> stocks = dh.executeQuery("SELECT Symbol, Held FROM Portfolio WHERE Held > 0");

        for (String stock : stocks) {
            String[] splitStock = stock.split(",");
            sellStock(splitStock[0], Integer.parseInt(splitStock[1]));
        }

        dh.executeCommand("DELETE FROM Portfolio;");
    }

    public void rebalancePortfolio() throws SQLException {
        sellAllStock();

        Map<String, Double> portfolio = PortfolioManager.optimisePortfolio();

        String command = "INSERT INTO Portfolio (Symbol, Allocation, Held) VALUES \r\n";

        int count = 1;
        double availableFunds = Double.parseDouble(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions;").get(0));

        for (String stock : portfolio.keySet()) {
            double allocation = availableFunds * portfolio.get(stock);

            command += "('" + stock + "', " + allocation + ", 0)";
            if (count++ < portfolio.size())
                command += ", \r\n";
            else
                command += ";";
        }

        dh.executeCommand(command);

        Platform.runLater(() -> {
            try {
                updateStocksOwned();
                updateComponentChart();
                updateAllocationChart();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void enforceNumericValues(TextField tf) {
        tf.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*"))
                tf.setText(newValue.replaceAll("[^\\d]", ""));
        });

    }

    private void initialiseListeners() {
        enforceNumericValues(lossCutoffField);
        enforceNumericValues(profitTargetField);

        stockAmountField.textProperty().addListener(((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*"))
                stockAmountField.setText(newValue.replaceAll("[^\\d]", ""));

            boolean disable = !(!newValue.isEmpty() && newValue.matches("\\d*"));

            String stock = stockDropdown.getValue();


            Platform.runLater(() -> {
                try {
                    buyButton.setDisable(disable || !canBuyStock(stock, Integer.parseInt(newValue)));
                    sellButton.setDisable(disable || !canSellStock(stock, Integer.parseInt(newValue)));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });

        }));
    }

    @FXML
    public void initialize() throws SQLException {
        mainThread = new Thread(() -> {
            while (!quit) {
                int s = LocalTime.now().getSecond();
                int m = LocalTime.now().getMinute();
                int h = LocalTime.now().getHour();
                int cycle = m % downloadInterval;

                updateIntradayStockData();
                updateDailyStockData();
                Platform.runLater(() -> {
                    try {
                        updateProfitLossChart();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                try {
                    checkServices();
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                //new Thread(() -> { try { updateNews(); } catch (Exception e) { e.printStackTrace(); }}).start();
            }
        });

        initialiseConnections();
        initialiseDatabase();

        initialiseListeners();

        StockQuoteDownloader.initialise(sqdh, avh, stockFeedProgress);
        NaturalLanguageProcessor.initialise(nlpdh, nlpProgress);
        TechnicalAnalyser.initialise(tadh, technicalAnalyserProgress);
        NewsAPIHandler.initialise(nddh, newsFeedProgress);
        PortfolioManager.initialise(pmdh); //TODO: Get a progessbar for this
        StockRecordParser.initialise(sqdh);

        autonomyLevelDropdown.getItems().addAll("Manual", "Semi-Autonomy", "Full-Autonomy");
        autonomyLevelDropdown.getSelectionModel().selectFirst();
        portfolioChart.getXAxis().setVisible(false);
        portfolioChart.getXAxis().setTickLabelsVisible(false);
        portfolioChart.getXAxis().setOpacity(0);
        ArrayList<String> stocks = dh.executeQuery("SELECT Symbol FROM indices ORDER BY Symbol ASC");
        stockDropdown.getItems().addAll(stocks);

        initialiseStocks();
        initialiseClocks();
        initialiseDisplay();
        startClocks();

        //PortfolioManager.optimisePortfolio(); //TODO: Remove this when finished testing

        new Thread(() -> {
            try {
                updateSystem();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startClocks() {
        System.out.println("Starting Clocks");
        new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                updateClocks();
            }
        }).start();
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
            ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol FROM Portfolio WHERE Held > 0;");

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

    private void initialiseDisplay() throws SQLException {
        System.out.println("Initialising Display");
        ArrayList<String> stockInfo = dh.executeQuery("SELECT Symbol, Name FROM indices;");

        Map<String, String> stockNames = new HashMap<>();

        for (String curr : stockInfo) {
            String[] values = curr.split(",");
            stockNames.put(values[0], values[1]);
        }

        for (String curr : stocks) {
            LiveStockRecord currRec = new LiveStockRecord(curr, stockNames.get(curr), dh);
            records.add(currRec);
        }

        for (LiveStockRecord rec : records)
            Platform.runLater(() -> stockList.getChildren().add(rec.getNode()));
    }

    public void printToInfoBox(String string) {
        infoBox.appendText(string + "\r\n");
    }

    private void initialiseClocks() {
        System.out.println("Initialising Clocks");
        clocks.add(new StockClock("NASDAQ", LocalTime.of(9, 30), LocalTime.of(16, 0), ZoneId.of("America/New_York")));
        clocks.add(new StockClock("London SE", LocalTime.of(8, 0), LocalTime.of(16, 30), ZoneId.of("Europe/London")));
        clocks.add(new StockClock("Tokyo SE", LocalTime.of(9, 0), LocalTime.of(15, 0), ZoneId.of("Asia/Tokyo"))); //TODO: Allow multiple open/close periods
        clocks.add(new StockClock("Hong Kong SE", LocalTime.of(9, 30), LocalTime.of(16, 0), ZoneId.of("Asia/Hong_Kong")));
        clocks.add(new StockClock("Australia SX", LocalTime.of(10, 0), LocalTime.of(16, 0), ZoneId.of("Australia/Canberra")));

        for (StockClock clock : clocks) timePane.getChildren().add(clock.getNode());
    }

    private void updateSystem() throws SQLException, InterruptedException {
        Thread stockThread = new Thread(() -> {
            try {
                processYahooHistories();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            StockQuoteDownloader.downloadStockHistory(stocks);

            try {
                TechnicalAnalyser.calculateTechnicalIndicators(stocks);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread newsThread = new Thread(() -> {
            nddh.setWriteToFile(true);
            try {
                updateNews();
            } catch (Exception e) {
                e.printStackTrace();
            }

            nddh.setWriteToFile(false);
            try {
                nddh.sendSQLFileToDatabase(false);
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                NewsAPIHandler.downloadArticles(); //Has to be done individually to check for duplicate values
            } catch (SQLException e) {
                e.printStackTrace();
            }

            boolean priceDataAvailable = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY
                    && Calendar.getInstance().get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                    && Calendar.getInstance().get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY;

            try {
                NaturalLanguageProcessor.enumerateSentencesFromArticles();
                NaturalLanguageProcessor.determineUselessSentences();

                if (priceDataAvailable)  //TODO: Fix this to make the most of the available data
                    NaturalLanguageProcessor.enumerateNGramsFromArticles(2);

                NaturalLanguageProcessor.determineUselessNGrams();
                NaturalLanguageProcessor.processArticlesForSentiment(2);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        //TODO: Put the following into one large update function?

        updateStockValues();
        updateBankBalance();
        updateTotalWorth();
        updateProfitLoss();
        updateAllocationChart();
        updateComponentChart();
        updateStocksOwned();
        //////////////////////////////////////////////////////////

        try {
            updateProfitLossChart();
        } catch (Exception e) {
            e.printStackTrace();
        }
        calculateLossCutoff(0.1); //TODO: Make this calculation based, based on risk/variance of portfolio
        calculateTargetCutoff(0.1);
        checkServices();

        newsThread.start();
        stockThread.start();
        newsThread.join();
        stockThread.join();

        startRealTime();
    }

    private void checkServices() throws SQLException {
        int newsCalls = 0;
        int callLimit = Integer.parseInt(dh.executeQuery("SELECT COALESCE(DailyLimit,0) FROM apimanagement WHERE Name='INTRINIO';").get(0));

        ArrayList<String> calls = dh.executeQuery("SELECT Calls FROM apicalls WHERE Name='INTRINIO' AND Date=CURDATE()");

        if (!calls.isEmpty())
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
            dh.executeCommand("CREATE TABLE IF NOT EXISTS dailystockprices (Symbol varchar(7) NOT NULL, TradeDate date NOT NULL, OpenPrice double NOT NULL, HighPrice double NOT NULL, LowPrice double NOT NULL, ClosePrice double NOT NULL, TradeVolume bigint(20) NOT NULL, SMA10 double, EMA10 double, MACD double, RSI10 double, ADX10 double, CCI10 double, PRIMARY KEY (Symbol,TradeDate), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS intradaystockprices (Symbol varchar(7) NOT NULL, TradeDateTime datetime NOT NULL, OpenPrice double NOT NULL, HighPrice double NOT NULL, LowPrice double NOT NULL, ClosePrice double NOT NULL, TradeVolume bigint(20) NOT NULL, PRIMARY KEY (Symbol,TradeDateTime), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS apimanagement (Name varchar(20) NOT NULL, DailyLimit int default 0, Delay int default 0, PRIMARY KEY (Name));");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS apicalls (Name varchar(20) NOT NULL, Date date NOT NULL, Calls int default 0, PRIMARY KEY (Name, Date), FOREIGN KEY (Name) REFERENCES apimanagement (Name));");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS ngrams (Hash varchar(32) NOT NULL PRIMARY KEY, Gram text NOT NULL, N int NOT NULL, Increase int DEFAULT 0, Decrease int DEFAULT 0, Occurrences int DEFAULT 0 NOT NULL, Documents int DEFAULT 1 NOT NULL, Blacklisted BIT DEFAULT 0);");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS portfolio (Symbol varchar(7) NOT NULL PRIMARY KEY, Allocation double NOT NULL, Held int NOT NULL DEFAULT 0, Investment double NOT NULL DEFAULT 0, LastUpdated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (Symbol) REFERENCES indices(Symbol));");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS sentences (Hash varchar(32) NOT NULL PRIMARY KEY, Sentence text NOT NULL, Occurrences int DEFAULT 0 NOT NULL, Documents int DEFAULT 0 NOT NULL, Blacklisted BIT DEFAULT 0);");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS newsarticles (ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY, Symbol varchar(7) NOT NULL, Headline text NOT NULL, Description text, Content longtext, Published datetime NOT NULL, URL varchar(1000), Blacklisted BIT DEFAULT 0 NOT NULL, Redirected BIT DEFAULT 0 NOT NULL, Duplicate BIT DEFAULT 0 NOT NULL, Enumerated BIT DEFAULT 0 NOT NULL, Tokenised BIT DEFAULT 0 NOT NULL, Processed BIT DEFAULT 0 NOT NULL, Mood double DEFAULT 0.5, FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS tradetransactions (ID INT AUTO_INCREMENT NOT NULL PRIMARY KEY, TradeDateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, Type varchar(4), Symbol varchar(7) NOT NULL, Volume INT UNSIGNED NOT NULL DEFAULT 0, Price DOUBLE UNSIGNED NOT NULL, FOREIGN KEY (Symbol) REFERENCES indices(Symbol));");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS banktransactions (ID INT AUTO_INCREMENT NOT NULL, TradeDateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, Type varchar(10), Amount double SIGNED NOT NULL, PRIMARY KEY (ID));");

            int bankTransactions = Integer.parseInt(dh.executeQuery("SELECT COUNT(*) FROM banktransactions;").get(0));

            if (bankTransactions == 0)
                dh.executeCommand("INSERT INTO banktransactions(Amount, Type) VALUES (10000, 'DEPOSIT');");

            int apis = Integer.parseInt(dh.executeQuery("SELECT COUNT(*) FROM apimanagement;").get(0));

            if (apis == 0)
                dh.executeCommand("INSERT INTO apimanagement VALUES ('INTRINIO',500,0),('AlphaVantage',0,1667);");
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    @FXML
    public void initialiseStocks() {
        System.out.println("Initialising stocks");
        try {
            stocks = dh.executeQuery("SELECT Symbol FROM indices");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void processYahooHistories() throws SQLException {
        for (String symbol : stocks) {
            if (!sqdh.executeQuery("SELECT COALESCE(COUNT(*),0) FROM dailystockprices WHERE Symbol='" + symbol + "';").isEmpty())
                return;
            File file = new File("res/historicstocks/" + symbol + ".csv");

            if (file.exists())
                try {
                    StockRecordParser.importDailyMarketData(file, symbol);
                    System.out.println("Successfully committed complete Yahoo records of " + symbol + " to the database!");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            else
                System.err.println("No Yahoo history available for " + symbol);
        }
    }


    private void buyStock(String stock, int amount) throws SQLException {
        float cost = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));
        float totalCost = cost * amount;
        float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));

        if (totalCost <= balance) {
            //TODO: Allocation warning if exceeding allocation but still purchasable
            dh.executeCommand("INSERT INTO portfolio (Symbol, Allocation, Held, Investment) VALUES ('" + stock + "', " + totalCost + ", " + amount + ", " + totalCost + ") ON DUPLICATE KEY UPDATE Allocation = GREATEST(VALUES(Allocation), (SELECT Allocation FROM (SELECT Allocation FROM portfolio WHERE Symbol='" + stock + "') as t)), Held = Held+ VALUES(Held), Investment = Investment + VALUES(Investment);");
            dh.executeCommand("INSERT INTO banktransactions(Amount, Type) VALUES (" + -totalCost + ",'TRADE')");
            dh.executeCommand("INSERT INTO tradetransactions(Type,Symbol,Volume,Price) VALUES ('BUY'," +
                    "'" + stock + "'," +
                    amount + "," +
                    cost +
                    ");");

            updateAfterStockAlteration();
        }
    }

    private void updateAfterStockAlteration() {
        Platform.runLater(() -> {
            try {
                updateStocksOwned();
                updateComponentChart();
                updateProfitLoss();
                updateBankBalance();
                updateStockValues();
                updateTotalWorth();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void updateClocks() {
        for (StockClock clock : clocks) clock.updateTime();
    }

    private void updateStocksOwned() throws SQLException {
        ArrayList<String> heldStocks = dh.executeQuery("SELECT Held, Symbol FROM Portfolio");

        stockBox.getChildren().clear();

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            stockBox.getChildren().add(new Label(splitStock[0] + '\t' + splitStock[1]));
        }
    }

    private float updateProfitLoss() throws SQLException {
        float investmentCost = Float.parseFloat(dh.executeQuery("SELECT COALESCE(SUM(Investment),0) FROM Portfolio").get(0));
        float potentialTotal = 0;

        ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol, Held FROM Portfolio WHERE Held > 0");

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            potentialTotal += Float.parseFloat(splitStock[1]) * Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + splitStock[0] + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));
        }

        float total = potentialTotal - investmentCost;

        profitLossLabel.setText(String.valueOf(Math.round(total * 100.0) / 100.0));
        if (total > 0)
            profitLossLabel.setTextFill(Color.GREEN);
        else if (total == 0)
            profitLossLabel.setTextFill(Color.BLACK);
        else
            profitLossLabel.setTextFill(Color.RED);

        return total;
    }

    private void updateTotalWorth() {
        float bankBalance = Float.parseFloat(currentBalanceLabel.getText());
        float stockWorth = Float.parseFloat(stockValueLabel.getText());

        totalBalanceLabel.setText(String.valueOf(Math.round((bankBalance + stockWorth) * 100.0) / 100.0));
    }

    private int getHeldStocks(String stock) {
        try {
            return Integer.parseInt(dh.executeQuery("SELECT COALESCE(Held,0) FROM Portfolio WHERE Symbol='" + stock + "';").get(0));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    @FXML
    public void startRealTime() {
        mainThread.start();
    }

    private void updateComponentChart() throws SQLException {
        ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol, Held FROM portfolio WHERE Held > 0;");

        if (heldStocks.isEmpty()) return;

        ArrayList<PieChart.Data> piechartData = new ArrayList<>();

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            piechartData.add(new PieChart.Data(splitStock[0], Integer.parseInt(splitStock[1])));
        }

        componentChart.getData().clear();
        componentChart.getData().addAll(piechartData);
    }

    private void updateAllocationChart() throws SQLException {
        double total = Double.parseDouble(dh.executeQuery("SELECT COALESCE(SUM(Allocation),0) FROM portfolio;").get(0));

        ArrayList<String> allowance = dh.executeQuery("SELECT Symbol, Allocation FROM portfolio ORDER BY Allocation DESC;");

        ArrayList<PieChart.Data> piechartData = new ArrayList<>();

        for (String stock : allowance) {
            String[] splitStock = stock.split(",");
            float allocation = Float.parseFloat(splitStock[1]);

            piechartData.add(new PieChart.Data(splitStock[0], allocation));
        }

        allocationChart.getData().clear();
        allocationChart.getData().addAll(piechartData);
    }


    public boolean canBuyStock(String stock, int amount) throws SQLException {
        if (amount == 0 || stock.isEmpty())
            return false;

        double availableFunds = Double.parseDouble(dh.executeQuery("SELECT COALESCE(SUM(Amount),0) FROM banktransactions;").get(0)),
                stockCost = Double.parseDouble(dh.executeQuery("SELECT COALESCE(ClosePrice,0) FROM intradaystockprices WHERE Symbol='" + stock + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));

        return (stockCost * amount) <= availableFunds;
    }

    public boolean canSellStock(String stock, int amount) throws SQLException {
        if (amount == 0 || stock.isEmpty())
            return false;

        double availableStocks = Double.parseDouble(dh.executeQuery("SELECT COALESCE(Held,0) FROM portfolio WHERE Symbol = '" + stock + "';").get(0));

        return availableStocks >= amount;
    }

    @FXML
    private void buyStock() throws SQLException {
        String stock = stockDropdown.getValue();
        int amount = Integer.parseInt(stockAmountField.getText());

        stockAmountField.clear();

        buyStock(stock, amount);
    }

    @FXML
    private void sellStock() throws SQLException {
        String stock = stockDropdown.getValue();
        int amount = Integer.parseInt(stockAmountField.getText());

        stockAmountField.clear();

        sellStock(stock, amount);
    }

    private void sellStock(String stock, int amount) throws SQLException {
        float cost = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM intradaystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDateTime DESC LIMIT 1").get(0));

        float totalCost = cost * amount;

        int available = getHeldStocks(stock);

        if (amount <= available) {
            dh.executeCommand("INSERT INTO banktransactions(Amount, Type) VALUES (" + totalCost + ",'TRADE')");
            dh.executeCommand("INSERT INTO tradetransactions(Type,Symbol,Volume,Price) VALUES ('SELL'," +
                    "'" + stock + "'," +
                    amount + "," +
                    cost +
                    ");");

            dh.executeCommand("UPDATE Portfolio SET Held = Held - " + amount + ", Investment = Investment - " + totalCost + " WHERE Symbol='" + stock + "';");

            updateAfterStockAlteration();
        }
    }

    private void updateNews() throws SQLException, InterruptedException {
        if (newsUpdating) return;
        newsUpdating = true;

        try {
            NewsAPIHandler.getHistoricNews(stocks);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Platform.runLater(() -> {
            newsBox.getChildren().clear();

            ArrayList<String> results = null;
            try {
                results = nddh.executeQuery("SELECT DISTINCT Symbol, Headline FROM newsarticles WHERE DATE(Published) = CURDATE() ORDER BY Published DESC");
            } catch (SQLException e) {
                e.printStackTrace();
            }

            for (String result : results) {
                String[] splitString = result.split(",");
                NewsRecord temp = new NewsRecord(splitString[0], splitString[1]);
                newsBox.getChildren().add(temp.getNode());
            }
        });
        newsUpdating = false;
    }

    private void updateDailyStockData() {
        if (priceUpdating) return;
        priceUpdating = true;
        for (LiveStockRecord curr : records) {
            curr.setUpdating(true);
            ArrayList<String> temp = null;

            try {
                temp = StockQuoteDownloader.downloadStockData(curr.getSymbol(), StockQuoteDownloader.Interval.DAILY, StockQuoteDownloader.OutputSize.COMPACT);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (temp != null && temp.size() > 1) {
                StockRecordParser.importDailyMarketData(temp, curr.getSymbol());
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
                temp = StockQuoteDownloader.downloadStockData(curr.getSymbol(), StockQuoteDownloader.Interval.INTRADAY, StockQuoteDownloader.OutputSize.COMPACT);
            } catch (IOException e) {
            } catch (InterruptedException e) {
            }

            if (temp != null && temp.size() >= 2) {
                StockRecordParser.importIntradayMarketData(temp, curr.getSymbol());
                System.out.println("Downloaded " + curr.getSymbol() + " 1 minute update: " + temp.get(1));
            }

            curr.updateRecord(sqdh);
            curr.setUpdating(false);
            Platform.runLater(() -> curr.updateChart(sqdh, false));

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
}