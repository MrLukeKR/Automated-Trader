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
import org.apache.spark.mllib.linalg.DenseVector;

import java.io.*;
import java.sql.SQLException;
import java.sql.Timestamp;
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
    static DatabaseHandler spdh = new DatabaseHandler();
    static BarChartHandler bch = new BarChartHandler();
    static private double lossCutoff, profitCutoff;

    static AlphaVantageHandler avh = new AlphaVantageHandler();
    private final STOCK_API useAPI = STOCK_API.AlphaVantage;
    static boolean quit = false;
    final int downloadInterval = 1;
    ArrayList<String> stocks = new ArrayList<>();
    ArrayList<LiveStockRecord> records = new ArrayList<>();
    ArrayList<StockClock> clocks = new ArrayList<>();

    static private Thread mainThread;
    @FXML
    CheckBox showRSI;

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
    CheckBox showCCI;
    @FXML
    CheckBox showADX10;
    @FXML
    LineChart<Number, Double> macdChart;
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
    VBox historicOptions;
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
    boolean automated = false;

    boolean priceUpdating = false;
    boolean newsUpdating = false;

    public static void shutdown() throws SQLException {
        quit = true;
        if (mainThread.isAlive())
            mainThread.interrupt();

        dh.close();
        nddh.close();
        nlpdh.close();
        tadh.close();
        sqdh.close();
        pmdh.close();
        spdh.close();
    }

    static public void updateProgress(double value, ProgressBar pb) {
        final double val = value;
        if (value == 0 || value == 1)
            Platform.runLater(() -> pb.setVisible(false));
        else if (!pb.isVisible())
            Platform.runLater(() -> pb.setVisible(true));

        Platform.runLater(() -> pb.setProgress(val));
    }

    @FXML
    private void displayHistoricData() {
        displayHistoricDataButton.setDisable(true);
        historicOptions.setDisable(true);

        if (!historicPriceChart.getData().isEmpty()) historicPriceChart.getData().clear();
        if (!historicVolumeChart.getData().isEmpty()) historicVolumeChart.getData().clear();
        if (!macdChart.getData().isEmpty()) macdChart.getData().clear();

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
            XYChart.Series<Number, Double> smoothedClosePrices = new XYChart.Series<>();
            XYChart.Series<Number, Double> sma10 = new XYChart.Series<>();
            XYChart.Series<Number, Double> ema10 = new XYChart.Series<>();
            XYChart.Series<Number, Double> macd = new XYChart.Series<>();
            XYChart.Series<Number, Double> macdSig = new XYChart.Series<>();
            XYChart.Series<Number, Double> macdHist = new XYChart.Series<>();
            XYChart.Series<Number, Double> rsi = new XYChart.Series<>();
            XYChart.Series<Number, Double> adx10 = new XYChart.Series<>();
            XYChart.Series<Number, Double> cci10 = new XYChart.Series<>();
            XYChart.Series<Number, Double> ad = new XYChart.Series<>();
            XYChart.Series<Number, Double> obv = new XYChart.Series<>();
            XYChart.Series<Number, Double> stoOscSlowK = new XYChart.Series<>();
            XYChart.Series<Number, Double> stoOscSlowD = new XYChart.Series<>();

            openPrices.setName("Open Prices");
            highPrices.setName("High Prices");
            lowPrices.setName("Low Prices");
            closePrices.setName("Close Prices");
            smoothedClosePrices.setName("Smoothed Close Prices");
            volumes.setName("Trade Volume");
            sma10.setName("Simple Moving Average (10-Day)");
            ema10.setName("Exponential Moving Average (10-Day)");
            macd.setName("Moving Average Convergence/Divergence");
            macdSig.setName("Moving Average Convergence/Divergence Signal Line");
            macdHist.setName("Moving Average Convergence/Divergence Histogram");
            rsi.setName("Relative Strength Indicator");
            adx10.setName("");
            cci10.setName("");
            ad.setName("");
            obv.setName("");
            stoOscSlowK.setName("");
            stoOscSlowD.setName("");

            int count = 0;

            for (String record : dbData) {
                String[] splitRecord = record.split(",");

                XYChart.Data<Number, Double> openData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[2]));
                XYChart.Data<Number, Double> highData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[3]));
                XYChart.Data<Number, Double> lowData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[4]));
                XYChart.Data<Number, Double> closeData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[5]));
                XYChart.Data<Number, Number> volumeData = new XYChart.Data<>(count, Integer.parseInt(splitRecord[6]));
                XYChart.Data<Number, Double> smoothedCloseData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[7]));

                Rectangle[] rects = new Rectangle[18];

                for (int i = 0; i < rects.length; i++) {
                    rects[i] = new Rectangle(0, 0);
                    rects[i].setVisible(false);
                }

                volumeData.setNode(rects[0]);
                openData.setNode(rects[1]);
                highData.setNode(rects[2]);
                lowData.setNode(rects[3]);
                closeData.setNode(rects[4]);
                smoothedCloseData.setNode(rects[5]);

                volumes.getData().add(volumeData);
                openPrices.getData().add(openData);
                highPrices.getData().add(highData);
                lowPrices.getData().add(lowData);
                closePrices.getData().add(closeData);
                smoothedClosePrices.getData().add(smoothedCloseData);

                if (showSMA10.isSelected() && !splitRecord[8].equals("null")) {
                    XYChart.Data<Number, Double> sma10Data = new XYChart.Data<>(count, Double.parseDouble(splitRecord[7]));
                    sma10.getData().add(sma10Data);
                    sma10Data.setNode(rects[6]);
                }

                if (showEMA10.isSelected() && !splitRecord[9].equals("null")) {
                    XYChart.Data<Number, Double> ema10Data = new XYChart.Data<>(count, Double.parseDouble(splitRecord[8]));
                    ema10.getData().add(ema10Data);
                    ema10Data.setNode(rects[7]);
                }

                if (showMACD.isSelected() && !splitRecord[10].equals("null")) {
                    XYChart.Data<Number, Double> macdData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[9]));
                    macd.getData().add(macdData);
                    macdData.setNode(rects[8]);
                }

                if (showMACDSig.isSelected() && !splitRecord[11].equals("null")) {
                    XYChart.Data<Number, Double> macdSigData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[10]));
                    macdSig.getData().add(macdSigData);
                    macdSigData.setNode(rects[9]);
                }

                if (showMACDHist.isSelected() && !splitRecord[12].equals("null")) {
                    XYChart.Data<Number, Double> macdHistData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[11]));
                    macdHist.getData().add(macdHistData);
                    macdHistData.setNode(rects[10]);
                }

                if (showRSI.isSelected() && !splitRecord[13].equals("null")) {
                    XYChart.Data<Number, Double> rsiData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[12]));
                    //macdHist.getData().add();
                    rsiData.setNode(rects[11]);
                }

                if (showADX10.isSelected() && !splitRecord[14].equals("null")) {
                    XYChart.Data<Number, Double> adx10Data = new XYChart.Data<>(count, Double.parseDouble(splitRecord[13]));
                    //macdHist.getData().add();
                    adx10Data.setNode(rects[12]);
                }

                if (showCCI.isSelected() && !splitRecord[15].equals("null")) {
                    XYChart.Data<Number, Double> cci10Data = new XYChart.Data<>(count, Double.parseDouble(splitRecord[14]));
                    //macdHist.getData().add();
                    cci10Data.setNode(rects[13]);
                }

                if (showAD.isSelected() && !splitRecord[16].equals("null")) {
                    XYChart.Data<Number, Double> adData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[15]));
                    //macdHist.getData().add();
                    adData.setNode(rects[14]);
                }

                if (showOBV.isSelected() && !splitRecord[17].equals("null")) {
                    XYChart.Data<Number, Double> obvData = new XYChart.Data<>(count, Double.parseDouble(splitRecord[16]));
                    //macdHist.getData().add();
                    obvData.setNode(rects[15]);
                }

                count++;
            }

            Platform.runLater(() -> {
                historicPriceChart.getData().addAll(openPrices, highPrices, lowPrices, closePrices, smoothedClosePrices, sma10, ema10);
                historicVolumeChart.getData().add(volumes);
                macdChart.getData().addAll(macd, macdSig, macdHist);

                volumes.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                openPrices.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                highPrices.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                lowPrices.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                closePrices.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                smoothedClosePrices.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                sma10.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                ema10.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                macd.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                macdSig.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
                macdHist.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");

                displayHistoricDataButton.setDisable(false);
                historicOptions.setDisable(false);
            });
        }).start();
    }

    private ArrayList<String> convertToClassificationTrainingArray(String stock, int index, int[] amountOfDaysArray) throws SQLException {
        ArrayList<String> dataPoints = new ArrayList<>();

        ArrayList<String> priceValues = null;
        try {
            priceValues = dh.executeQuery("SELECT * FROM dailystockprices WHERE Symbol = '" + stock + "' AND SmoothedClosePrice is not null AND SMA10 is not null AND EMA10 is not null AND MACD is not null AND MACDSig is not null AND MACDHist is not null AND RSI is not null AND ADX10 is not null AND CCI is not null AND AD is not null AND OBV is not null AND StoOscSlowK is not null AND StoOscSlowD is not null AND SMA3 is not null AND SMA5 is not null AND WillR is not null ORDER BY TradeDate ASC ");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        int columnToPredict = 7; //Use column 7 for smoothed data
        double[] currentPrices = new double[priceValues.size()];
        double[] sentiments = NaturalLanguageProcessor.getAverageSentiments(stock, priceValues.size());

        for (int amountOfDays : amountOfDaysArray) {
            double[] futurePrices = new double[priceValues.size() - amountOfDays];

            for (int i = 0; i < priceValues.size() - amountOfDays; i++) {
                String[] splitString = priceValues.get(i).split(",");
                currentPrices[i] = Double.parseDouble(splitString[columnToPredict]);
            }

            for (int i = amountOfDays; i < priceValues.size(); i++) {
                String[] splitString = priceValues.get(i).split(",");
                futurePrices[i - amountOfDays] = Double.parseDouble(splitString[columnToPredict]);
            }

            for (int i = 0; i < futurePrices.length; i++) {
                String dataPoint = String.valueOf(index) + "," + String.valueOf(amountOfDays);
                String[] splitString = priceValues.get(i).split(",");

                for (int j = 2; j < splitString.length; j++)
                    dataPoint += "," + splitString[j];

                dataPoint += "," + sentiments[i] + "," + String.valueOf((futurePrices[i] - currentPrices[i]) >= 0 ? 1 : 0);
                dataPoints.add(dataPoint);
            }
        }

        System.out.println("Converted data to Classification Training Array for '" + stock + "'");

        return dataPoints;
    }

    @FXML
    private void enableAmountField() {
        stockAmountField.setDisable(false);
    }

    private void exportToFile(ArrayList<String> values, String path) throws FileNotFoundException {
        File file = new File(path);
        PrintWriter pw = new PrintWriter(file);
        pw.close();
    }

    private void exportClassificationCSV(String path) {
        final int t = stocks.size() - 1;
        int c = 0;

        try {
            File file = new File(path);
            PrintWriter pw = new PrintWriter(file);

            for (String stock : stocks) {
                for (String value : convertToClassificationTrainingArray(stock, c, new int[]{1, 5, 20, 200}))
                    pw.println(value);

                updateProgress(++c, t, stockForecastProgress);
            }
            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void exportLibSVMFile(String csvPath, String libSVMPath) throws IOException {
        FileReader fr = new FileReader(csvPath);
        BufferedReader br = new BufferedReader(fr);

        File writeFile = new File(libSVMPath);
        PrintWriter pw = new PrintWriter(writeFile);

        String line;

        while ((line = br.readLine()) != null)
            pw.println(StockPredictor.convertToLibSVM(line));

        fr.close();
        pw.close();
    }

    @FXML
    private void exportToMLFile() {
        new Thread(() -> {
            Platform.runLater(() -> exportToMLFileButton.setDisable(true));

            System.out.println("Exporting to ML File...");

            updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockForecastProgress);

            exportClassificationCSV("res/TrainingFiles/SmoothedNASDAQTraining.csv");

            updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockForecastProgress);

            try {
                exportLibSVMFile("res/TrainingFiles/SmoothedNASDAQTraining.csv", "res/TrainingFiles/SmoothedNASDAQTrainingLibSVM.txt");
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                StockPredictor.trainRandomForest("res/TrainingFiles/SmoothedNASDAQTrainingLibSVM.txt", stocks.size());
            } catch (SQLException e) {
                e.printStackTrace();
            }

            updateProgress(0, stockForecastProgress);
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

    @FXML
    private void setLossCutoff() {
        setLossCutoff(Double.valueOf(lossCutoffField.getText()));
    }

    @FXML
    private void setProfitCutoff() {
        setProfitCutoff(Double.valueOf(profitTargetField.getText()));
    }

    private void setLossCutoff(double amount) {
        lossCutoff = amount;
        Platform.runLater(() -> targetLabel.setText(String.valueOf(profitCutoff)));
    }

    private void setProfitCutoff(double amount) {
        profitCutoff = amount;
        Platform.runLater(() -> cutoffLabel.setText(String.valueOf(lossCutoff)));
    }

    private void calculateLossCutoff(double percentage) throws SQLException {
        float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));
        lossCutoff = (balance + getStockWorth()) * (1 - percentage);

        Platform.runLater(() -> cutoffLabel.setText(String.valueOf(lossCutoff)));
    }

    private void calculateTargetCutoff(double percentage) throws SQLException {
        float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));
        profitCutoff = (balance + getStockWorth()) * (1 + percentage);

        Platform.runLater(() -> targetLabel.setText(String.valueOf(profitCutoff)));
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
            spdh.init("StockPredictor", "wfN1XLoW810diEhR");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        avh.init("PBATJ7L9N8SNK835");
        bch.init("07467da3de1195c974b66c46b8523e23", sqdh, stockFeedProgress);
        NewsAPIHandler.authenticate("be7afde61f5e10bb20393025c35e50c7", "1ff9ab03aa8e5bd073345d70d588abde");
    }

    @FXML
    public void setAutonomyLevel() {
        String level = autonomyLevelDropdown.getValue().toString();
        boolean fullyAutonomous = level == "Full-Autonomy",
                semiAutonomous = level == "Semi-Autonomy";

        autonomousToolbox.setDisable(fullyAutonomous);
        manualToolbox.setDisable(fullyAutonomous || semiAutonomous);

        automated = fullyAutonomous || semiAutonomous;
        //TODO: Set autonomy settings
    }

    private void updateProfitLossChart() throws SQLException, ParseException {
        Platform.runLater(() -> portfolioChart.getData().clear());
        //TODO: Refactor this to not regather all data each iteration
        ArrayList<String> portfolioRecords = dh.executeQuery("SELECT Symbol, Held, Investment, LastUpdated FROM portfolio WHERE Held > 0 ORDER BY LastUpdated ASC;");

        Map<Long, Double> timeAndPrice = new TreeMap<>();

        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (String record : portfolioRecords) {
            String[] splitRecord = record.split(",");

            double held = Double.parseDouble(splitRecord[1]),
                    cost = Double.parseDouble(splitRecord[2]);
            String command = "SELECT ClosePrice, TradeDateTime FROM intradaystockprices WHERE Symbol = '" + splitRecord[0] + "' AND TradeDateTime >= '" + Timestamp.valueOf(splitRecord[3]).toLocalDateTime() + "';";
            for (String iRecord : dh.executeQuery(command)) {
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
                    rebalancePortfolio(false);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                Platform.runLater(() -> rebalanceButton.setDisable(false));
            }).start();
    }

    public void sellAllStock(boolean automated) throws SQLException {
        ArrayList<String> stocks = dh.executeQuery("SELECT Symbol, Held FROM Portfolio WHERE Held > 0");

        for (String stock : stocks) {
            String[] splitStock = stock.split(",");
            sellStock(splitStock[0], Integer.parseInt(splitStock[1]), automated);
        }

        dh.executeCommand("DELETE FROM Portfolio;");

        updateGUI();
    }

    public void rebalancePortfolio(boolean automated) throws SQLException {
        sellAllStock(automated);

        Map<String, Double> portfolio = PortfolioManager.optimisePortfolio();

        String command = "INSERT INTO Portfolio (Symbol, Allocation, Held, LastUpdated) VALUES \r\n";

        int count = 1;
        double availableFunds = Double.parseDouble(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions;").get(0));

        for (String stock : portfolio.keySet()) {
            double allocation = availableFunds * portfolio.get(stock);

            String lastUpdated = dh.executeQuery("SELECT MAX(TradeDateTime) FROM intradaystockprices WHERE Symbol='" + stock + "';").get(0);

            command += "('" + stock + "', " + allocation + ", 0, '" + lastUpdated + "')";
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

    private boolean predictStock(String stock, int numberOfDays) throws SQLException {
        String results = dh.executeQuery("SELECT * FROM dailystockprices WHERE Symbol='" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0);
        double newsSentiment = NaturalLanguageProcessor.getTodaysAverageSentiment(stock, 2);

        String[] splitString = results.split(",");

        double features[] = new double[splitString.length + 1];

        //int stockBytes = Utils.stringToNumber(stock);
        features[0] = stocks.indexOf(stock);
        features[1] = numberOfDays;

        for (int i = 2; i < splitString.length - 1; i++)
            features[i] = Double.parseDouble(splitString[i + 1]);

        features[features.length - 1] = newsSentiment;

        return StockPredictor.predictDirection(new DenseVector(features));
    }

    private void predictStocks() throws SQLException {
        int dayArray[] = new int[]{1, 5, 20, 200};
        boolean predictions[] = new boolean[stocks.size() * dayArray.length];

        int i = 0;


        System.out.println("Predicting Stock Performance...");
        updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockForecastProgress);


        for (int numberOfDays : dayArray)
            for (String stock : stocks) {
                predictions[i] = predictStock(stock, numberOfDays);
                if (predictions[i])
                    System.out.println(numberOfDays + " Day Prediction for " + stock + ": RISE/MAINTAIN");
                else
                    System.out.println(numberOfDays + " Day Prediction for " + stock + ": FALL");
                i++;
                updateProgress(i, stocks.size() - 1, stockForecastProgress);
            }

        updateProgress(0, stockForecastProgress);

        System.out.println("Predicted Stock Performance!");
    }

    private void autoTrade() throws SQLException {
        System.out.println("Auto-Trading...");
        ArrayList<String> portfolio = dh.executeQuery("SELECT * FROM Portfolio ORDER BY Allocation DESC;");

        for (String record : portfolio) {
            double balance = Double.parseDouble(dh.executeQuery("SELECT COALESCE(SUM(Amount),0) FROM banktransactions").get(0));
            String[] splitString = record.split(",");
            String symbol = splitString[0];
            double allocation = Double.parseDouble(splitString[1]) - Double.parseDouble(splitString[3]);
            int held = Integer.parseInt(splitString[2]);
            double currentPrice = Double.parseDouble(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + symbol + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
            boolean priceWillRise = predictStock(symbol, 1);

            if (priceWillRise) {
                int buyAmount = (int) Math.floor(allocation / currentPrice);
                if ((buyAmount > 0) && (buyAmount * currentPrice) <= balance) {
                    printToInfoBox("> AUTOMATED TRADER: BUYING " + buyAmount + " " + symbol);
                    System.out.println("> AUTOMATED TRADER: BUYING " + buyAmount + " " + symbol);
                    buyStock(symbol, buyAmount, true);
                }
            } else {
                if (held > 0) {
                    printToInfoBox("> AUTOMATED TRADER: SELLING " + held + " " + symbol);
                    System.out.println("> AUTOMATED TRADER: SELLING " + held + " " + symbol);
                    sellStock(symbol, held, true);
                }
            }
            //TODO: Rebalance portfolio and cutoff reassignment
        }

        updateGUI();
    }

    @FXML
    public void initialize() throws SQLException {
        mainThread = new Thread(() -> {
            while (!quit) {
                int s = LocalTime.now().getSecond();
                int m = LocalTime.now().getMinute();
                int h = LocalTime.now().getHour();
                int cycle = m % downloadInterval;

                //DOWNLOAD INTRADAY DATA FOR VISUALISATION PURPOSES
                if (s == 0) {
                    try {
                        //updateBatchStockData();
                        if (!priceUpdating)
                            updateIntradayStockData();
                        if (!priceUpdating)
                            updateDailyStockData();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (m == 0) {
                        try {
                            updateNews();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    if (m % 5 == 0) {
                        try {
                            StockQuoteDownloader.downloadStockHistory(stocks, true, true, false);
                            SmoothingUtils.smoothStocks(stocks);
                            TechnicalAnalyser.calculateTechnicalIndicators(stocks);
                            predictStocks();
                            if (automated)
                                autoTrade();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    updateGUI();
                    try {
                        checkServices();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
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
        SmoothingUtils.initialise(tadh, technicalAnalyserProgress);
        TechnicalAnalyser.initialise(tadh, technicalAnalyserProgress);
        NewsAPIHandler.initialise(nddh, newsFeedProgress);
        PortfolioManager.initialise(pmdh); //TODO: Get a progessbar for this
        StockPredictor.initialise(spdh);
        StockPredictor.loadLatestRandomForest();

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

        macdChart.getYAxis().setMaxWidth(50.);
        macdChart.getYAxis().setPrefWidth(50.);
        macdChart.getYAxis().setMinWidth(50.);
    }

    private void updateStockValues() {
        float worth = 0;
        try {
            ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol FROM Portfolio WHERE Held > 0;");

            for (String stock : heldStocks) {
                int volume = getHeldStocks(stock);
                float currPrice = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
                worth += volume * currPrice;


            }
            float finalWorth = worth;
            Platform.runLater(() -> stockValueLabel.setText(String.valueOf(finalWorth)));
        } catch (SQLException e) {
            e.printStackTrace();
        }
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

    public void printToInfoBox(String string) {
        Platform.runLater(() -> infoBox.appendText(string + "\r\n"));
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

    private void updateSystem() throws SQLException, InterruptedException {
        Thread stockThread = new Thread(() -> {
            try {
                processYahooHistories();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            try {
                //        databaseHandler.executeCommand("DELETE FROM dailystockprices WHERE Temporary = 1;");
                dh.executeCommand("DELETE FROM intradaystockprices WHERE Temporary = 1;");

                StockQuoteDownloader.downloadStockHistory(stocks, true, false, false);

                switch (useAPI) {
                    case BarChart:
                        bch.downloadIntradayHistory(stocks);
                        break;
                    case AlphaVantage:
                        StockQuoteDownloader.downloadStockHistory(stocks, false, true, false);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        });

        Thread taThread = new Thread(() -> {
            try {
                SmoothingUtils.smoothStocks(stocks);
                TechnicalAnalyser.calculatePercentChanges(stocks);
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
                //NaturalLanguageProcessor.determineUselessNGrams();
                NaturalLanguageProcessor.processArticlesForSentiment(2);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        updateGUI();
        //////////////////////////////////////////////////////////

        calculateLossCutoff(0.1); //TODO: Make this calculation based, based on risk/variance of portfolio
        calculateTargetCutoff(0.1);
        checkServices();

        newsThread.start();
        stockThread.start();

        stockThread.join();
        taThread.start();
        newsThread.join();

        nlpThread.start();
        nlpThread.join();
        taThread.join();

        for (LiveStockRecord curr : records) {
            curr.updateRecord(sqdh);
            curr.updateChart(sqdh, false);
        }

        controlPanel.setDisable(false);

        mainThread.start();
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

    private void initialiseDatabase() {
        System.out.println("Initialising Database...");
        try {
            dh.executeCommand("CREATE DATABASE IF NOT EXISTS automated_trader;"); //TODO: Allow login of root to create the initial agent user
            //dh.executeCommand("GRANT ALL ON automated_trader.* TO 'agent'@'%';");
            dh.executeCommand("USE automated_trader");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS indices (Symbol varchar(7) UNIQUE NOT NULL, Name text NOT NULL, StartedTrading date NOT NULL, CeasedTrading date, TwitterUsername varchar(15), PRIMARY KEY (Symbol))");
            dh.executeCommand("CREATE TABLE IF NOT EXISTS dailystockprices (Symbol varchar(7) NOT NULL, TradeDate date NOT NULL, OpenPrice double NOT NULL, HighPrice double NOT NULL, LowPrice double NOT NULL, ClosePrice double NOT NULL, TradeVolume bigint(20) NOT NULL, SMA10 double, EMA10 double, MACD double, RSI double, ADX10 double, CCI double, PRIMARY KEY (Symbol,TradeDate), FOREIGN KEY (Symbol) REFERENCES indices(Symbol))");
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

            updateProgress(++curr, t, stockFeedProgress);
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

    private void buyStock(String stock, int amount, boolean automated) throws SQLException {
        float cost = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
        float totalCost = cost * amount;
        float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));
        int auto = 0;

        if (automated)
            auto = 1;

        if (totalCost <= balance) {
            //TODO: Allocation warning if exceeding allocation but still purchasable
            String lastUpdated = dh.executeQuery("SELECT MAX(TradeDateTime) FROM intradaystockprices WHERE Symbol = '" + stock + "';").get(0);
            dh.executeCommand("INSERT INTO portfolio (Symbol, Allocation, Held, Investment, LastUpdated) VALUES ('" + stock + "', " + totalCost + ", " + amount + ", " + totalCost + ", '" + lastUpdated + "') ON DUPLICATE KEY UPDATE Allocation = GREATEST(VALUES(Allocation), (SELECT Allocation FROM (SELECT Allocation FROM portfolio WHERE Symbol='" + stock + "') as t)), Held = Held+ VALUES(Held), Investment = Investment + VALUES(Investment), LastUpdated = VALUES(LastUpdated);");
            dh.executeCommand("INSERT INTO banktransactions(Amount, Type) VALUES (" + -totalCost + ",'TRADE')");
            dh.executeCommand("INSERT INTO tradetransactions(Type,Symbol,Volume,Price,Automated) VALUES ('BUY'," +
                    "'" + stock + "'," +
                    amount + "," +
                    cost + "," +
                    auto +
                    ");");
        }
    }

    private void updateGUI() {
        Platform.runLater(() -> {
            try {
                updateStocksOwned();
                updateComponentChart();
                updateAllocationChart();
                updateProfitLoss();
                updateProfitLossChart();
                updateBankBalance();
                updateStockValues();
                updateTotalWorth();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateStocksOwned() throws SQLException {
        ArrayList<String> heldStocks = dh.executeQuery("SELECT Held, Symbol FROM Portfolio");

        Platform.runLater(() -> stockBox.getChildren().clear());

            for (String stock : heldStocks) {
                String[] splitStock = stock.split(",");
                Platform.runLater(() -> stockBox.getChildren().add(new Label(splitStock[0] + '\t' + splitStock[1])));
            }
    }

    private void updateClocks() {
        for (StockClock clock : clocks) clock.updateTime();
    }

    private float getStockWorth() throws SQLException {
        float potentialTotal = 0;
        ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol, Held FROM Portfolio WHERE Held > 0");

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            potentialTotal += Float.parseFloat(splitStock[1]) * Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + splitStock[0] + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
        }

        return potentialTotal;
    }

    private float updateProfitLoss() throws SQLException {
        float investmentCost = Float.parseFloat(dh.executeQuery("SELECT COALESCE(SUM(Investment),0) FROM Portfolio").get(0));
        float potentialTotal = getStockWorth();

        float total = potentialTotal - investmentCost;


        Platform.runLater(() -> profitLossLabel.setText(String.valueOf(Math.round(total * 100.0) / 100.0)));
        if (total > 0)
            Platform.runLater(() -> profitLossLabel.setTextFill(Color.GREEN));
        else if (total == 0)
            Platform.runLater(() -> profitLossLabel.setTextFill(Color.BLACK));
        else
            Platform.runLater(() -> profitLossLabel.setTextFill(Color.RED));

        return total;
    }

    private void updateTotalWorth() {

            float bankBalance = Float.parseFloat(currentBalanceLabel.getText()),
                    stockWorth = Float.parseFloat(stockValueLabel.getText());

        //TODO: Make this NOT rely on text values ^^

        Platform.runLater(() -> totalBalanceLabel.setText(String.valueOf(Math.round((bankBalance + stockWorth) * 100.0) / 100.0)));
    }

    public boolean canBuyStock(String stock, int amount) throws SQLException {
        if (amount == 0 || stock.isEmpty())
            return false;

        double availableFunds = Double.parseDouble(dh.executeQuery("SELECT COALESCE(SUM(Amount),0) FROM banktransactions;").get(0)),
                stockCost = Double.parseDouble(dh.executeQuery("SELECT COALESCE(ClosePrice,0) FROM dailystockprices WHERE Symbol='" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));

        return (stockCost * amount) <= availableFunds;
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

    @FXML
    private void buyStock() throws SQLException {
        String stock = stockDropdown.getValue();
        int amount = Integer.parseInt(stockAmountField.getText());

        stockAmountField.clear();

        buyStock(stock, amount, false);

        updateGUI();
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
    private void sellStock() throws SQLException {
        String stock = stockDropdown.getValue();
        int amount = Integer.parseInt(stockAmountField.getText());

        stockAmountField.clear();

        sellStock(stock, amount, false);

        updateGUI();
    }

    private void sellStock(String stock, int amount, boolean automated) throws SQLException {
        float cost = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));

        float totalCost = cost * amount;

        int available = getHeldStocks(stock);

        int auto = 0;

        if (automated)
            auto = 1;

        if (amount <= available) {
            dh.executeCommand("INSERT INTO banktransactions(Amount, Type) VALUES (" + totalCost + ",'TRADE')");
            dh.executeCommand("INSERT INTO tradetransactions(Type,Symbol,Volume,Price, Automated) VALUES ('SELL'," +
                    "'" + stock + "'," +
                    amount + "," +
                    cost + "," +
                    auto +
                    ");");

            String lastUpdated = dh.executeQuery("SELECT MAX(TradeDateTime) FROM intradaystockprices WHERE Symbol = '" + stock + "';").get(0);

            dh.executeCommand("UPDATE Portfolio SET Held = Held - " + amount + ", Investment = Investment - " + totalCost + ", LastUpdated = '" + lastUpdated + "' WHERE Symbol='" + stock + "';");
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

        Platform.runLater(() -> newsBox.getChildren().clear());

            ArrayList<String> results = null;
            try {
                results = nddh.executeQuery("SELECT DISTINCT Symbol, Headline FROM newsarticles WHERE DATE(Published) = CURDATE() ORDER BY Published DESC");
            } catch (SQLException e) {
                e.printStackTrace();
            }

            for (String result : results) {
                String[] splitString = result.split(",");
                NewsRecord temp = new NewsRecord(splitString[0], splitString[1]);
                Platform.runLater(() -> newsBox.getChildren().add(temp.getNode()));
            }

        newsUpdating = false;
    }

    private void updateDailyStockData() throws InterruptedException {
        if (priceUpdating) return;
        priceUpdating = true;
        ArrayList<Thread> threads = new ArrayList<>();
        for (LiveStockRecord curr : records) {
            Thread downloadThread = new Thread(() -> {
                curr.setUpdating(true);
                ArrayList<String> temp = null;

                try {
                    temp = StockQuoteDownloader.downloadStockData(curr.getSymbol(), StockQuoteDownloader.Interval.DAILY, StockQuoteDownloader.OutputSize.COMPACT);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (temp != null && temp.size() > 1) {
                    try {
                        StockRecordParser.importDailyMarketData(temp, curr.getSymbol());
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    System.out.println("Downloaded " + curr.getSymbol() + " current daily close price: " + temp.get(1));
                }
                curr.setUpdating(false);
            });
            downloadThread.start();
        }

        for (Thread thread : threads)
            thread.join();

        priceUpdating = false;
    }

    private void updateIntradayStockData() throws SQLException, InterruptedException {
        dh.executeCommand("DELETE FROM intradaystockprices WHERE Temporary = 1");

        if (priceUpdating) return;
        priceUpdating = true;
        sqdh.setAutoCommit(false);
        ArrayList<Thread> threads = new ArrayList<>();
        for (LiveStockRecord curr : records) {
            Thread downloadThread = new Thread(() -> {
                curr.setUpdating(true);
                ArrayList<String> temp = null;

                try {
                    switch (useAPI) {
                        case BarChart:
                            temp = bch.downloadIntradayHistory(curr.getSymbol());
                            break;
                        case AlphaVantage:
                            temp = StockQuoteDownloader.downloadStockData(curr.getSymbol(), StockQuoteDownloader.Interval.INTRADAY, StockQuoteDownloader.OutputSize.COMPACT);
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (temp != null && temp.size() > 1) {
                    try {
                        StockRecordParser.importIntradayMarketData(temp, curr.getSymbol());
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    System.out.println("Downloaded " + curr.getSymbol() + " 1 minute update: " + temp.get(1));
                }
                curr.setUpdating(false);
            });
            downloadThread.start();
        }

        for (Thread thread : threads)
            thread.join();

        sqdh.executeBatch();
        sqdh.setAutoCommit(true);

        for (LiveStockRecord curr : records) {
            curr.updateRecord(sqdh);
            curr.updateChart(sqdh, false);
        }

        priceUpdating = false;
    }

    private void updateBatchStockData() throws SQLException {
        ArrayList<String> temp = null;
        try {
            switch (useAPI) {
                case AlphaVantage:
                    temp = StockQuoteDownloader.downloadBatchStockData(stocks);
                    break;
                case BarChart:
                    temp = bch.downloadQuotes(stocks);
                    break;
            }
        } catch (Exception e) {
        }


        int i = 0;

        for (LiveStockRecord curr : records) {
            //if(Integer.parseInt(dh.executeQuery("SELECT Max(TradeDate) = CURRENT_DATE FROM dailystockprices WHERE Symbol='" + curr.symbol + "';").get(0)) == 1) {
            String record = temp.get(i++);
            String[] splitString = record.split(",");
            record = record.replace(splitString[0] + ",", "");

            ArrayList<String> tempRec = new ArrayList<>();
            tempRec.add(record);

            if (tempRec != null) {
                if (splitString[0].equals(curr.symbol)) {
                    StockRecordParser.importCurrentQuote(tempRec.get(0), curr.getSymbol());
                    System.out.println("Downloaded " + curr.getSymbol() + " current price:" + record);
                    curr.updateRecord(dh);
                    curr.updateChart(dh, false);
                } else
                    System.err.println("Batch download mismatch");
            }
//           }
        }
        updateGUI();
    }

    private enum STOCK_API {AlphaVantage, BarChart}
}