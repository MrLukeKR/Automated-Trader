import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.AreaChart;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
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

    static private Thread mainThread;

    //Historic Stock FXML Items
    @FXML
    ComboBox<String> historicStockDropdown;
    @FXML
    Label historicDateRange;
    @FXML
    CheckBox showSMA10;
    @FXML
    CheckBox showEMA10;
    @FXML
    CheckBox showMACD;
    @FXML
    CheckBox showMACDSig;
    @FXML
    CheckBox showMACDHist;
    @FXML
    CheckBox showRSI10;
    @FXML
    CheckBox showADX10;
    @FXML
    CheckBox showCCI10;
    @FXML
    CheckBox showAD;
    @FXML
    CheckBox showOBV;
    @FXML
    CheckBox showStoOscSlowK;
    @FXML
    CheckBox showStoOscSlowD;
    @FXML
    LineChart<Number, Double> historicPriceChart;
    @FXML
    AreaChart<Number, Number> historicVolumeChart;
    @FXML
    Button displayHistoricDataButton;
    //-------------------------

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
    ProgressBar stockForecastProgress;
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
    @FXML
    Button setLossCutoffButton;
    @FXML
    Button setProfitTargetButton;
    @FXML
    FlowPane controlPanel;
    @FXML
    VBox stockBox;

    boolean priceUpdating = false;
    boolean newsUpdating = false;

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
    private void displayHistoricData() {
        if (!historicPriceChart.getData().isEmpty()) historicPriceChart.getData().clear();
        if (!historicVolumeChart.getData().isEmpty()) historicVolumeChart.getData().clear();

        new Thread(() -> {

            String stock = historicStockDropdown.getValue();

            ArrayList<String> dbData = null;

            try {
                dbData = dh.executeQuery("SELECT * FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate ASC;");
            } catch (SQLException e) {
                e.printStackTrace();
            }

            XYChart.Series<Number, Double> openPrices = new XYChart.Series<>();
            XYChart.Series<Number, Double> highPrices = new XYChart.Series<>();
            XYChart.Series<Number, Double> lowPrices = new XYChart.Series<>();
            XYChart.Series<Number, Double> closePrices = new XYChart.Series<>();
            AreaChart.Series<Number, Number> volumes = new AreaChart.Series<>();
            XYChart.Series<Number, Double> sma10 = new XYChart.Series<>();
            XYChart.Series<Number, Double> ema10 = new XYChart.Series<>();

            openPrices.setName("Open Prices");
            highPrices.setName("High Prices");
            lowPrices.setName("Low Prices");
            closePrices.setName("Close Prices");
            volumes.setName("Trade Volume");
            sma10.setName("Simple Moving Average (10-Day)");
            ema10.setName("Exponential Moving Average (10-Day)");

            int count = 0;


            for (String record : dbData) {
                String[] splitRecord = record.split(",");

                XYChart.Data<Number, Number> volumeData = new XYChart.Data<>(count, Integer.parseInt(splitRecord[6]));
                XYChart.Data<Number, Double> openData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[2]));
                XYChart.Data<Number, Double> highData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[3]));
                XYChart.Data<Number, Double> lowData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[4]));
                XYChart.Data<Number, Double> closeData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[5]));


                Rectangle[] rects = new Rectangle[7];

                for (int i = 0; i < rects.length; i++) {
                    rects[i] = new Rectangle(0, 0);
                    rects[i].setVisible(false);
                }

                volumeData.setNode(rects[0]);
                openData.setNode(rects[1]);
                highData.setNode(rects[2]);
                lowData.setNode(rects[3]);
                closeData.setNode(rects[4]);

                volumes.getData().add(volumeData);
                openPrices.getData().add(openData);
                highPrices.getData().add(highData);
                lowPrices.getData().add(lowData);
                closePrices.getData().add(closeData);

                if (showSMA10.isSelected() && !splitRecord[7].equals("null")) {
                    XYChart.Data<Number, Double> sma10Data = new XYChart.Data<>(count, Double.parseDouble(splitRecord[7]));
                    sma10.getData().add(sma10Data);
                    sma10Data.setNode(rects[5]);
                }

                if (showEMA10.isSelected() && !splitRecord[8].equals("null")) {
                    XYChart.Data<Number, Double> ema10Data = new XYChart.Data<>(count, Double.parseDouble(splitRecord[8]));
                    ema10.getData().add(ema10Data);
                    ema10Data.setNode(rects[6]);
                }

                count++;
            }

            Platform.runLater(() -> {
                historicPriceChart.setCreateSymbols(false);
                historicPriceChart.getData().addAll(openPrices, highPrices, lowPrices, closePrices, sma10, ema10);
                historicVolumeChart.getData().add(volumes);

                volumes.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                openPrices.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                highPrices.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                lowPrices.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                closePrices.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                sma10.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                ema10.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
            });
        }).start();
    }

    @FXML
    private void enableAmountField() {
        stockAmountField.setDisable(false);
    }

    private double getAverageSentimentOnDate(String stock, String date) throws SQLException {
        ArrayList<String> result = dh.executeQuery("SELECT COALESCE(AVG(Mood),0.5) FROM newsarticles WHERE Symbol = '" + stock + "' AND DATE(Published) = '" + date + "' AND Processed = 1");

        if (result == null || result.isEmpty())
            return 0.5;
        else
            return Double.parseDouble(result.get(0));
    }

    private ArrayList<String> convertToClassificationTrainingArray(String stock) throws SQLException {
        ArrayList<String> dataPoints = new ArrayList<>();

        ArrayList<String> priceValues = null;
        try {
            priceValues = dh.executeQuery("SELECT * FROM dailystockprices WHERE Symbol = '" + stock + "' AND SMA10 is not null AND EMA10 is not null AND MACD is not null AND MACDSig is not null AND MACDHist is not null AND RSI10 is not null AND ADX10 is not null AND CCI10 is not null AND AD is not null AND OBV is not null AND StoOscSlowK is not null AND StoOscSlowD is not null ORDER BY TradeDate ASC ");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        int amountOfDays = 1;
        int columnToPredict = 7; //Use column 7 (SMA10) for smoothed data

        double[] sentiments = new double[priceValues.size()];
        double[] currentPrices = new double[priceValues.size()];
        double[] futurePrices = new double[priceValues.size() - amountOfDays];

        //1-day
        for (int i = 0; i < priceValues.size(); i++) {
            String[] splitString = priceValues.get(i).split(",");
            sentiments[i] = getAverageSentimentOnDate(stock, splitString[1]);
        }

        for (int i = 0; i < priceValues.size() - amountOfDays; i++) {
            String[] splitString = priceValues.get(i).split(",");
            currentPrices[i] = Double.parseDouble(splitString[columnToPredict]);
        }

        for (int i = amountOfDays; i < priceValues.size(); i++) {
            String[] splitString = priceValues.get(i).split(",");
            futurePrices[i - amountOfDays] = Double.parseDouble(splitString[columnToPredict]);
        }

        String byteStock = convertStockToNumber(stock);
        for (int i = 0; i < futurePrices.length; i++) {
            String dataPoint = byteStock;
            String[] splitString = priceValues.get(i).split(",");

            for (int j = 2; j < splitString.length; j++)
                dataPoint += "," + splitString[j];

            dataPoint += "," + sentiments[i] + ",";
            if (futurePrices[i] >= currentPrices[i])
                dataPoint += "1";
            else
                dataPoint += "0";

            dataPoints.add(dataPoint);
        }

        System.out.println("Converted data to Classification Training Array for '" + stock + "'");

        return dataPoints;
    }

    private String convertStockToNumber(String stock) {
        return Utils.stringToByteValue(stock);
    }

    private void exportToFile(ArrayList<String> values, String path) throws FileNotFoundException {
        File file = new File(path);
        PrintWriter pw = new PrintWriter(file);
        for (String value : values)
            pw.println(value);
        pw.close();
    }

    @FXML
    private void exportToMLFile() {
        new Thread(() -> {
            Platform.runLater(() -> exportToMLFileButton.setDisable(true));

            System.out.println("Exporting to ML File...");

            updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockForecastProgress);

            final int t = stocks.size() - 1;
            int c = 0;

            try {

               /* ArrayList<String> trainingCSV = new ArrayList<>();
                for(String stock : stocks) {
                    trainingCSV.addAll(convertToClassificationTrainingArray(stock));
                    updateProgress(++c,t,stockForecastProgress);
                }

                updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockForecastProgress);
                ArrayList<String> libSVMtrainingFile = StockPredictor.convertCSVToLibSVM(trainingCSV);

                exportToFile(trainingCSV, "res/TrainingFiles/SmoothedNASDAQTraining.csv");
                trainingCSV.clear();
                exportToFile(libSVMtrainingFile, "res/TrainingFiles/SmoothedNASDAQTrainingLibSVM.txt");
                libSVMtrainingFile.clear();
                */
                StockPredictor.trainRandomForest("res/TrainingFiles/SmoothedNASDAQTrainingLibSVM.txt", stocks.size());
            } catch (Exception e) {
                e.printStackTrace();
            }

            updateProgress(0, stockForecastProgress);
            /*
            ArrayList<String> priceValues = null;
            try {
                priceValues = dh.executeQuery("SELECT * FROM dailystockprices WHERE SMA10 is not null AND EMA10 is not null AND MACD is not null AND MACDSig is not null AND MACDHist is not null AND RSI10 is not null AND ADX10 is not null AND CCI10 is not null AND AD is not null AND OBV is not null AND StoOscSlowK is not null AND StoOscSlowD is not null ORDER BY Symbol ASC, TradeDate ASC ");
            } catch (SQLException e) {
                e.printStackTrace();
            }

            for (String record : priceValues) {
                String[] splitString = record.split(",");
                String symbolBytes = Utils.stringToByteValue(splitString[0]);
            }
            */
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
        Platform.runLater(() -> portfolioChart.getData().clear());
        //TODO: Refactor this to not regather all data each iteration
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

        updateGUI();
    }

    private void initialiseListeners() {
        lossCutoffField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*\\.?\\d*"))
                lossCutoffField.setText(newValue.replaceAll("[^\\d\\.]", ""));

        });

        profitTargetField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*\\.?\\d*"))
                profitTargetField.setText(newValue.replaceAll("[^\\d\\.]", ""));

        });

        stockAmountField.textProperty().addListener(((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*"))
                stockAmountField.setText(newValue.replaceAll("[^\\d]", ""));

            boolean disable = newValue.isEmpty();

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

                try {
                    updateIntradayStockData();
                    updateDailyStockData();
                    } catch (Exception e) {
                        e.printStackTrace();
                }

                try {
                    updateProfitLossChart();
                    checkServices();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //new Thread(() -> { try { updateNews(); } catch (Exception e) { e.printStackTrace(); }}).start();
            }
        });

        initialiseConnections();
        initialiseDatabase();

        initialiseListeners();
        alignCharts();

        StockQuoteDownloader.initialise(sqdh, avh, stockFeedProgress);
        StockRecordParser.initialise(sqdh);
        NaturalLanguageProcessor.initialise(nlpdh, nlpProgress);
        TechnicalAnalyser.initialise(tadh, technicalAnalyserProgress);
        NewsAPIHandler.initialise(nddh, newsFeedProgress);
        PortfolioManager.initialise(pmdh); //TODO: Get a progessbar for this
        StockPredictor.initialise();

        autonomyLevelDropdown.getItems().addAll("Manual", "Semi-Autonomy", "Full-Autonomy");
        autonomyLevelDropdown.getSelectionModel().selectFirst();

        portfolioChart.getXAxis().setVisible(false);
        portfolioChart.getXAxis().setTickLabelsVisible(false);
        portfolioChart.getXAxis().setOpacity(0);

        ArrayList<String> stocks = dh.executeQuery("SELECT Symbol FROM indices ORDER BY Symbol ASC");
        stockDropdown.getItems().addAll(stocks);
        historicStockDropdown.getItems().addAll(stocks);

        portfolioChart.setAnimated(false);

        initialiseStocks();
        initialiseClocks();
        initialiseDisplay();
        startClocks();

        new Thread(() -> {
            try {
                updateSystem();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void alignCharts() {
        historicPriceChart.getYAxis().setMaxWidth(50.); //TODO: Make this variable
        historicVolumeChart.getYAxis().setMaxWidth(50.);
        historicPriceChart.getYAxis().setPrefWidth(50.);
        historicVolumeChart.getYAxis().setPrefWidth(50.);
        historicPriceChart.getYAxis().setMinWidth(50.);
        historicVolumeChart.getYAxis().setMinWidth(50.);
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

            Platform.runLater(() -> currentBalanceLabel.setText(String.valueOf(balance)));
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

                float finalWorth = worth;

                Platform.runLater(() -> stockValueLabel.setText(String.valueOf(finalWorth)));
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
        clocks.add(new StockClock("Deutsche Börse", LocalTime.of(8, 0), LocalTime.of(22, 0), ZoneId.of("CET")));
        clocks.add(new StockClock("SIX Swiss Exchange", LocalTime.of(9, 0), LocalTime.of(17, 30), ZoneId.of("CET")));
        clocks.add(new StockClock("Bombay SE", LocalTime.of(9, 15), LocalTime.of(15, 30), ZoneId.of("Asia/Calcutta")));

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

            try {
                NaturalLanguageProcessor.enumerateSentencesFromArticles();
                NaturalLanguageProcessor.determineUselessSentences();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        Thread nlpThread = new Thread(() -> {
            try {
                NaturalLanguageProcessor.enumerateNGramsFromArticles(2);
                NaturalLanguageProcessor.determineUselessNGrams();
                NaturalLanguageProcessor.processArticlesForSentiment(2);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        updateGUI();
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

        nlpThread.start();
        nlpThread.join();

        controlPanel.setDisable(false);

        mainThread.start();
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

        double curr = 0;
        final double t = stocks.size();

        updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockFeedProgress);

        for (String symbol : stocks) {
            ArrayList<String> results = sqdh.executeQuery("SELECT COUNT(*) FROM dailystockprices WHERE Symbol='" + symbol + "';");
            if (results.isEmpty() || Integer.parseInt(results.get(0)) == 0) {
                System.out.println("Importing Yahoo! records for: " + symbol);

                File file = new File("res/historicstocks/" + symbol + ".csv");

                if (file.exists())
                    try {
                        StockRecordParser.importDailyYahooMarketData(file, symbol);
                        System.out.println("Successfully committed complete Yahoo! records of " + symbol + " to the database!");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                else
                    System.err.println("No Yahoo history available for " + symbol);
            }

            updateProgress(curr++, t, stockFeedProgress);
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

            updateGUI();
        }
    }

    private void updateGUI() {
        Platform.runLater(() -> {
            try {
                updateStocksOwned();
                updateComponentChart();
                updateAllocationChart();
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

        Platform.runLater(() -> {
            stockBox.getChildren().clear();

            for (String stock : heldStocks) {
                String[] splitStock = stock.split(",");
                stockBox.getChildren().add(new Label(splitStock[0] + '\t' + splitStock[1]));
            }
        });
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

        Platform.runLater(() -> {
            profitLossLabel.setText(String.valueOf(Math.round(total * 100.0) / 100.0));
            if (total > 0)
                profitLossLabel.setTextFill(Color.GREEN);
            else if (total == 0)
                profitLossLabel.setTextFill(Color.BLACK);
            else
                profitLossLabel.setTextFill(Color.RED);
        });

        return total;
    }

    private void updateTotalWorth() {
        Platform.runLater(() -> {
            float bankBalance = Float.parseFloat(currentBalanceLabel.getText()),
                    stockWorth = Float.parseFloat(stockValueLabel.getText());
            totalBalanceLabel.setText(String.valueOf(Math.round((bankBalance + stockWorth) * 100.0) / 100.0));
        });

    }

    private int getHeldStocks(String stock) {
        try {
            return Integer.parseInt(dh.executeQuery("SELECT COALESCE(Held,0) FROM Portfolio WHERE Symbol='" + stock + "';").get(0));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    private void updateComponentChart() throws SQLException {
        Platform.runLater(() -> componentChart.getData().clear());

        ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol, Held FROM portfolio WHERE Held > 0;");

        if (heldStocks.isEmpty()) return;

        ArrayList<PieChart.Data> piechartData = new ArrayList<>();

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            piechartData.add(new PieChart.Data(splitStock[0], Integer.parseInt(splitStock[1])));
        }

        Platform.runLater(() -> componentChart.getData().addAll(piechartData));
    }

    private void updateAllocationChart() throws SQLException {
        Platform.runLater(() -> allocationChart.getData().clear());

        ArrayList<String> allowance = dh.executeQuery("SELECT Symbol, Allocation FROM portfolio ORDER BY Allocation DESC;");
        ArrayList<PieChart.Data> piechartData = new ArrayList<>();

        for (String stock : allowance) {
            String[] splitStock = stock.split(",");
            float allocation = Float.parseFloat(splitStock[1]);

            piechartData.add(new PieChart.Data(splitStock[0], allocation));
        }

        Platform.runLater(() -> allocationChart.getData().addAll(piechartData));
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

        ArrayList<String> result = dh.executeQuery("SELECT COALESCE(Held,0) FROM portfolio WHERE Symbol = '" + stock + "';");

        if (result.isEmpty()) return false;

        double availableStocks = Double.parseDouble(result.get(0));

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

            updateGUI();
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

    private void updateDailyStockData() throws SQLException {
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

    private void updateIntradayStockData() throws SQLException {
        if (priceUpdating) return;
        priceUpdating = true;
        for (LiveStockRecord curr : records) {
            curr.setUpdating(true);
            ArrayList<String> temp = null;
            try {
                temp = StockQuoteDownloader.downloadStockData(curr.getSymbol(), StockQuoteDownloader.Interval.INTRADAY, StockQuoteDownloader.OutputSize.COMPACT);
            } catch (Exception e) {
            }

            if (temp != null && temp.size() >= 2) {
                StockRecordParser.importIntradayMarketData(temp, curr.getSymbol());
                System.out.println("Downloaded " + curr.getSymbol() + " 1 minute update: " + temp.get(1));
            }

            curr.updateRecord(sqdh);
            curr.setUpdating(false);
        }
        priceUpdating = false;

        for (LiveStockRecord curr : records)
            Platform.runLater(() -> curr.updateChart(sqdh, false));

        updateGUI();
    }
}