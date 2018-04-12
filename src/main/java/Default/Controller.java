package Default;

import APIHandler.*;
import Portfolio.PortfolioManager;
import Prediction.StockPredictor;
import Prediction.TradingSimulator;
import Processing.NaturalLanguageProcessor;
import Processing.StockRecordParser;
import Processing.TechnicalAnalyser;
import Records.*;
import Utility.SmoothingUtils;
import Utility.TradingUtils;
import Utility.TrainingFileUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Pair;

import java.io.File;
import java.io.IOException;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static Utility.ChartUtils.*;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.1
 */

public class Controller {
    static final private double smoothRate = 0.1;
    static private boolean DISABLE_SYSTEM_UPDATE;
    static private DatabaseHandler dh = new DatabaseHandler();
    static private DatabaseHandler sqdh = new DatabaseHandler();
    static private DatabaseHandler nlpdh = new DatabaseHandler();
    static private DatabaseHandler tadh = new DatabaseHandler();
    static private DatabaseHandler nddh = new DatabaseHandler();
    static private DatabaseHandler pmdh = new DatabaseHandler();
    static private DatabaseHandler spdh = new DatabaseHandler();
    static private BarChartHandler bch = new BarChartHandler();
    static private AlphaVantageHandler avh = new AlphaVantageHandler();
    static private double lossCutoff, profitCutoff;
    static private Thread mainThread;
    static private boolean quit = false;
    static private int dayArray[] = new int[]{1, 30, 200};
    static private ArrayList<String> stocks = new ArrayList<>();
    static private ArrayList<LiveStockRecord> records = new ArrayList<>();
    static private TreeMap<String, PredictionBox> predictions = new TreeMap<>();
    static private TreeMap<String, SimulatorPredictionBox> simulatorPredictions = new TreeMap<>();
    static private ArrayList<StockClock> clocks = new ArrayList<>();
    static private HashMap<String, XYChart.Series<Number, Number>> simulatedHistory = new HashMap<>();
    static private XYChart.Series<Number, Number> simulatedBalance = new XYChart.Series<>();
    static private XYChart.Series<Number, Number> simulatedIndexPerformance = new XYChart.Series<>();
    static private XYChart.Series<Number, Number> simulatedRandomPerformance = new XYChart.Series<>();
    static private XYChart.Series<Number, Number> simulatedPortfolioInitialPerformance = new XYChart.Series<>();
    static private XYChart.Series<Number, Number> simulatedEqualAllocationPerformance = new XYChart.Series<>();
    static private HashMap<String, NumberAxis> simulatorAxes = new HashMap<>();

    @FXML
    FlowPane autonomousToolbox;
    @FXML
    FlowPane manualToolbox;
    @FXML
    FlowPane timePane;
    @FXML
    FlowPane stockList;
    @FXML
    FlowPane controlPanel;
    @FXML TextField simulationPortfolioLookbackPeriod;
    @FXML TextField simulationPortfolioHoldPeriod;
    @FXML NumberAxis simulatorPerformanceAxis;
    @FXML Button simulateTradingButton;
    @FXML
    Button displayHistoricDataButton;
    @FXML
    Button trainSimulationModelButton;
    @FXML
    Button sellButton;
    @FXML
    Button buyButton;
    @FXML
    Button trainMLModelButton;
    @FXML
    Button rebalanceButton;
    @FXML
    Button setLossCutoffButton;
    @FXML
    Button setProfitTargetButton;
    @FXML
    VBox simulatorPredictionBox;
    @FXML
    VBox historicOptions;
    @FXML VBox priceHistoryChartBox;
    @FXML
    VBox newsBox;
    @FXML
    VBox stockBox;
    @FXML
    VBox stockPredictionsBox;
    @FXML
    AreaChart<Number, Number> historicVolumeChart;
    @FXML
    LineChart<Number, Number> rsiChart;
    @FXML
    LineChart<Number, Number> adxChart;
    @FXML
    LineChart<Number, Number> cciChart;
    @FXML
    LineChart<Number, Number> adChart;
    @FXML
    LineChart<Number, Number> obvChart;
    @FXML
    LineChart<Number, Number> willRChart;
    @FXML
    LineChart<Number, Number> stoOscChart;
    @FXML
    LineChart<Number, Number> sentimentChart;
    @FXML
    LineChart<Number, Number> macdChart;
    @FXML
    LineChart<Number, Number> historicPriceChart;
    @FXML LineChart<Number, Number>  simulatorProfitLoss;
    @FXML
    LineChart<Number, Number> portfolioChart;
    @FXML
    PieChart allocationChart;
    @FXML
    PieChart componentChart;
    @FXML
    PieChart simulatorInitialPortfolioChart;
    @FXML
    PieChart simulatorRebalancedPortfolioChart;
    @FXML ComboBox<String> historicStockDropdown;
    @FXML ComboBox<String> optimisationMethodDropdown;
    @FXML ComboBox<String> evaluationMethodDropdown;
    @FXML
    ComboBox<String> stockDropdown;
    @FXML
    ComboBox<String> newsStockCombobox;
    @FXML
    ComboBox<String> newsDateCombobox;
    @FXML
    Label currentTaskLabel;
    @FXML Label historicDateRange;
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
    Label lossCutoffPercentageLabel;
    @FXML
    Label profitTargetPercentageLabel;
    @FXML CheckBox showSMA5;
    @FXML CheckBox showSMA10;
    @FXML CheckBox showSMA20;
    @FXML CheckBox showSMA200;
    @FXML CheckBox showEMA5;
    @FXML CheckBox showEMA10;
    @FXML CheckBox showEMA20;
    @FXML CheckBox showEMA200;
    @FXML CheckBox showMACD;
    @FXML CheckBox showMACDSig;
    @FXML CheckBox showMACDHist;
    @FXML
    CheckBox showRSI;
    @FXML CheckBox showCCI;
    @FXML CheckBox showADX10;
    @FXML CheckBox showAD;
    @FXML CheckBox showOBV;
    @FXML CheckBox showStoOscSlowK;
    @FXML CheckBox showStoOscSlowD;
    @FXML CheckBox showWillR;
    @FXML
    ProgressBar technicalAnalyserProgress;
    @FXML
    ProgressBar stockForecastProgress;
    @FXML ProgressBar stockFeedProgress;
    @FXML
    ProgressBar newsFeedProgress;
    @FXML
    ProgressBar nlpProgress;
    @FXML Circle nlpAvailability;
    @FXML Circle newsFeedAvailability;
    @FXML
    Circle stockFeedAvailability;
    @FXML
    Circle stockForecastAvailability;
    @FXML
    Circle technicalAnalyserAvailability;
    @FXML ComboBox autonomyLevelDropdown;
    @FXML TextField lossCutoffField;
    @FXML TextField profitTargetField;
    @FXML TextField stockAmountField;
    @FXML TextArea predictionModelInformationBox;
    @FXML
    TextArea infoBox;
    @FXML
    TextArea newsArticleArea;
    @FXML MenuItem exportAllTrainingFilesButton;
    @FXML MenuItem smoothPriceDataButton;
    @FXML MenuItem resetPriceDataButton;
    @FXML
    ListView<String> newsArticleList;

    private boolean automated = false;

    static void shutdown() throws SQLException {
        quit = true;

        if (mainThread.isAlive()) mainThread.interrupt();

        dh.close();
        nddh.close();
        nlpdh.close();
        tadh.close();
        sqdh.close();
        pmdh.close();
        spdh.close();
    }

    static public void updateProgress(double current, double total, ProgressBar pb) {
        updateProgress(current / total, pb);
    }

    static public void updateProgress(double value, ProgressBar pb) {
        Platform.runLater(() -> pb.setVisible(!(value == 0 || value == 1)));
        Platform.runLater(() -> pb.setProgress(value));
    }

    @FXML
    private void trainSimulationModel() {
        new Thread(() -> {
            Platform.runLater(() -> trainSimulationModelButton.setDisable(true));
            try {
                boolean singleStock = dh.executeQuery("SELECT value FROM settings WHERE ID='PREDICTION_MODE';").get(0).equals("SINGLE");
                if (singleStock) {
                    TradingSimulator.generateSingleStockTrainingFiles(stocks, 200, false);
                    TradingSimulator.trainSingleStocks(stocks);
                } else {
                    TradingSimulator.generateMultistockTrainingFiles(stocks, 200, false);
                    TradingSimulator.trainMultiStock(stocks);
                }
            } catch (Exception e) {
                Main.getController().updateCurrentTask("Could not train simulation model: " + e.getMessage(), true, true);
            } finally {
                Platform.runLater(() -> trainSimulationModelButton.setDisable(false));
            }
        }).start();
    }

    public void updateCurrentTask(String task, boolean error, boolean printToInfoBox){
        if(error)
            System.err.println(task);
        else
            System.out.println(task);
        Platform.runLater(()->currentTaskLabel.setText(task));
        if(printToInfoBox)
            printToInfoBox(task);
    }

    public void clearSimulation() {
        Platform.runLater(() -> simulatedBalance.getData().clear());
        Platform.runLater(() -> simulatedPortfolioInitialPerformance.getData().clear());
        Platform.runLater(() -> simulatedEqualAllocationPerformance.getData().clear());
        Platform.runLater(() -> simulatedIndexPerformance.getData().clear());
        Platform.runLater(() -> simulatedRandomPerformance.getData().clear());
        for (String stock : simulatedHistory.keySet())
            Platform.runLater(() -> simulatedHistory.get(stock).getData().clear());
    }

    @FXML
    private void displayHistoricData() {
        Platform.runLater(()->displayHistoricDataButton.setDisable(true));
        Platform.runLater(()->historicOptions.setDisable(true));

        if (!historicPriceChart.getData().isEmpty()) Platform.runLater(()->historicPriceChart.getData().clear());
        if (!historicVolumeChart.getData().isEmpty()) Platform.runLater(()->historicVolumeChart.getData().clear());
        if (!macdChart.getData().isEmpty()) Platform.runLater(()->macdChart.getData().clear());
        if (!rsiChart.getData().isEmpty()) Platform.runLater(()->rsiChart.getData().clear());
        if (!adxChart.getData().isEmpty()) Platform.runLater(()->adxChart.getData().clear());
        if (!cciChart.getData().isEmpty()) Platform.runLater(()->cciChart.getData().clear());
        if (!adChart.getData().isEmpty()) Platform.runLater(()->adChart.getData().clear());
        if (!obvChart.getData().isEmpty()) Platform.runLater(()->obvChart.getData().clear());
        if (!stoOscChart.getData().isEmpty()) Platform.runLater(()->stoOscChart.getData().clear());
        if (!willRChart.getData().isEmpty()) Platform.runLater(()->willRChart.getData().clear());
        if (!sentimentChart.getData().isEmpty()) Platform.runLater(()->sentimentChart.getData().clear());

        new Thread(() -> {
            String stock = historicStockDropdown.getValue();
            if (stock == null) {
                Platform.runLater(() -> displayHistoricDataButton.setDisable(false));
                Platform.runLater(() -> historicOptions.setDisable(false));
                return;
            }

            ArrayList<String> dbSchema = null;
            ArrayList<String> dbData = null;
            double[] sentimentData = null;

            try {
                dbSchema = dh.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");
                dbData = dh.executeQuery("SELECT * FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate ASC;");
                sentimentData = NaturalLanguageProcessor.getAverageSentiments(stock,dbData.size());
                String startDate = dh.executeQuery("SELECT MIN(TradeDate) FROM dailystockprices WHERE Symbol='" + stock + "';").get(0);
                String endDate = dh.executeQuery("SELECT MAX(TradeDate) FROM dailystockprices WHERE Symbol='" + stock + "';").get(0);
                Platform.runLater(() -> historicDateRange.setText(startDate + " to " + endDate));
            } catch (SQLException e) {
                e.printStackTrace();
            }

            int count = 0;

            try {
                HashMap<String, ArrayDeque<XYChart.Data<Number, Number>>> values = new HashMap<>();

                for (String column : Objects.requireNonNull(dbSchema)) values.put(column, new ArrayDeque<>());
                values.put("Sentiment", new ArrayDeque<>());
                for(int i = 0; i < sentimentData.length; i++) values.get("Sentiment").add(new XYChart.Data<>(i, sentimentData[i]));

                Map<String, String> historicValues = new HashMap<>();
                Map<String, String> indicatorValues = new HashMap<>();

                historicValues.put("OpenPrice", "Open Prices");
                historicValues.put("HighPrice", "High Prices");
                historicValues.put("ClosePrice", "Close Prices");
                historicValues.put("SmoothedClosePrice", "Smoothed Close Prices");
                indicatorValues.put("SMA5", "Simple Moving Average (5-Day)");
                indicatorValues.put("SMA10", "Simple Moving Average (10-Day)");
                indicatorValues.put("SMA20", "Simple Moving Average (20-Day)");
                indicatorValues.put("SMA200", "Simple Moving Average (200-Day)");
                indicatorValues.put("EMA5", "Exponential Moving Average (5-Day)");
                indicatorValues.put("EMA10", "Exponential Moving Average (10-Day)");
                indicatorValues.put("EMA20", "Exponential Moving Average (20-Day)");
                indicatorValues.put("EMA200", "Exponential Moving Average (200-Day)");

                Map<String, CheckBox> visualisationCheckboxes = new HashMap<>();

                visualisationCheckboxes.put("SMA5", showSMA5);
                visualisationCheckboxes.put("SMA10", showSMA10);
                visualisationCheckboxes.put("SMA20", showSMA20);
                visualisationCheckboxes.put("SMA200", showSMA200);
                visualisationCheckboxes.put("EMA5", showEMA5);
                visualisationCheckboxes.put("EMA10", showEMA10);
                visualisationCheckboxes.put("EMA20", showEMA20);
                visualisationCheckboxes.put("EMA200", showEMA200);
                visualisationCheckboxes.put("MACD", showMACD);
                visualisationCheckboxes.put("MACDSig", showMACDSig);
                visualisationCheckboxes.put("MACDHist", showMACDHist);
                visualisationCheckboxes.put("RSI", showRSI);
                visualisationCheckboxes.put("ADX10", showADX10);
                visualisationCheckboxes.put("CCI", showCCI);
                visualisationCheckboxes.put("AD", showAD);
                visualisationCheckboxes.put("OBV", showOBV);
                visualisationCheckboxes.put("StoOscSlowD", showStoOscSlowD);
                visualisationCheckboxes.put("StoOscSlowK", showStoOscSlowK);
                visualisationCheckboxes.put("WillR", showWillR);

                for (String record : Objects.requireNonNull(dbData)) {
                    int idx;

                    String[] splitRecord = record.split(",");

                    for (String value : historicValues.keySet())
                        if (!splitRecord[dbSchema.indexOf(value)].equals("null"))
                            values.get(value).add(createChartPoint(count, Double.parseDouble(splitRecord[dbSchema.indexOf(value)])));

                    for (String indicator : visualisationCheckboxes.keySet())
                        if (visualisationCheckboxes.get(indicator).isSelected() && !splitRecord[idx = dbSchema.indexOf(indicator)].equals("null"))
                            values.get(indicator).add(createChartPoint(count, Double.parseDouble(splitRecord[idx])));

                    count++;
                }

                ArrayList<XYChart.Series<Number, Number>> historicData = new ArrayList<>();
                ArrayList<XYChart.Series<Number, Number>> macdData = new ArrayList<>();
                ArrayList<XYChart.Series<Number, Number>> stoOscData = new ArrayList<>();

                for (String value : historicValues.keySet())
                    historicData.add(new XYChart.Series<>(historicValues.get(value), FXCollections.observableArrayList(values.get(value))));
                for (String value : indicatorValues.keySet())
                    historicData.add(new XYChart.Series<>(indicatorValues.get(value), FXCollections.observableArrayList(values.get(value))));

                macdData.add(new XYChart.Series<>("Moving Average Convergence/Divergence", FXCollections.observableArrayList(values.get("MACD"))));
                macdData.add(new XYChart.Series<>("Moving Average Convergence/Divergence Signal Line", FXCollections.observableArrayList(values.get("MACDSig"))));
                macdData.add(new XYChart.Series<>("Moving Average Convergence/Divergence Histogram", FXCollections.observableArrayList(values.get("MACDHist"))));
                XYChart.Series<Number, Number> volumes = new XYChart.Series<>("Trade Volume", FXCollections.observableArrayList(values.get("TradeVolume")));
                XYChart.Series<Number, Number> rsi = new XYChart.Series<>("Relative Strength Indicator", FXCollections.observableArrayList(values.get("RSI")));
                XYChart.Series<Number, Number> adx10 = new XYChart.Series<>("Average Directional Index (10-Day)", FXCollections.observableArrayList(values.get("ADX10")));
                XYChart.Series<Number, Number> cci = new XYChart.Series<>("Commodity Channel Index", FXCollections.observableArrayList(values.get("CCI")));
                XYChart.Series<Number, Number> ad = new XYChart.Series<>("Accumulation Distribution", FXCollections.observableArrayList(values.get("AD")));
                XYChart.Series<Number, Number> obv = new XYChart.Series<>("On-Balance Volume", FXCollections.observableArrayList(values.get("OBV")));
                stoOscData.add(new XYChart.Series<>("Stochastic Oscillator Slow %K", FXCollections.observableArrayList(values.get("StoOscSlowK"))));
                stoOscData.add(new XYChart.Series<>("Stochastic Oscillator Slow %D", FXCollections.observableArrayList(values.get("StoOscSlowD"))));
                XYChart.Series<Number, Number> willR = new XYChart.Series<>("Williams %R", FXCollections.observableArrayList(values.get("WillR")));
                XYChart.Series<Number, Number> sentiment = new XYChart.Series<>("News Article Sentiment", FXCollections.observableArrayList(values.get("Sentiment")));

                Platform.runLater(() -> historicPriceChart.getData().addAll(historicData));
                Platform.runLater(() -> historicVolumeChart.getData().add(volumes));
                Platform.runLater(() -> macdChart.getData().addAll(macdData));
                Platform.runLater(() -> rsiChart.getData().add(rsi));
                Platform.runLater(() -> adxChart.getData().add(adx10));
                Platform.runLater(() -> cciChart.getData().add(cci));
                Platform.runLater(() -> adChart.getData().add(ad));
                Platform.runLater(() -> obvChart.getData().add(obv));
                Platform.runLater(() -> willRChart.getData().add(willR));
                Platform.runLater(() -> stoOscChart.getData().addAll(stoOscData));
                Platform.runLater(() -> sentimentChart.getData().add(sentiment));

                formatLineChart(historicPriceChart.getData());
                formatLineChart(macdChart.getData());
                formatLineChart(stoOscChart.getData());
                formatLineChart(volumes);
                formatLineChart(rsi);
                formatLineChart(adx10);
                formatLineChart(cci);
                formatLineChart(ad);
                formatLineChart(obv);
                formatLineChart(willR);
                formatLineChart(sentiment);
            }catch(Exception e){
                e.printStackTrace();
            }finally{
                Platform.runLater(()->displayHistoricDataButton.setDisable(false));
                Platform.runLater(()->historicOptions.setDisable(false));
            }
        }).start();
    }

    @FXML
    private void enableAmountField() {
        stockAmountField.setDisable(false);
    }

    @FXML private void exportAllTrainingFiles(){
        new Thread(()-> {
            Platform.runLater(()->exportAllTrainingFilesButton.setDisable(true));
            try {
                TrainingFileUtils.exportAllFiles(stocks, stockForecastProgress, dayArray);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Platform.runLater(()->exportAllTrainingFilesButton.setDisable(false));
            }
        }).start();
    }

    @FXML
    private void trainMLModel() {
        new Thread(() -> {
            Platform.runLater(() -> trainMLModelButton.setDisable(true));

            try {
                updateCurrentTask("Generating ML Training File...", false, false);
                updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockForecastProgress);

                String predictionMode = dh.executeQuery("SELECT Value FROM Settings WHERE ID='PREDICTION_MODE';").get(0);
                if (predictionMode.equals("SINGLE")) {
                    for (String stock : stocks) {
                        File newDir = new File(System.getProperty("user.dir") + "/res/TrainingFiles/" + stock + "/");
                        if (!newDir.exists())
                            if (!newDir.mkdirs())
                                updateCurrentTask("Error: Could not create '" + newDir.getAbsolutePath() + "'", true, true);

                        TrainingFileUtils.exportClassificationCSV(stock, System.getProperty("user.dir") + "/res/TrainingFiles/" + stock + "/SmoothedNASDAQTraining.csv", dayArray, smoothRate, true, true, false, false);
                        TrainingFileUtils.exportLibSVMFile(System.getProperty("user.dir") + "/res/TrainingFiles/" + stock + "/SmoothedNASDAQTraining.csv", System.getProperty("user.dir") + "/res/TrainingFiles/" + stock + "/SmoothedNASDAQTraining.libsvm");
                    }

                    for (String stock : stocks)
                        StockPredictor.trainRandomForest(System.getProperty("user.dir") + "/res/TrainingFiles/" + stock + "/SmoothedNASDAQTraining.libsvm", stock, false);
                    for (String stock : stocks) StockPredictor.loadLatestRandomForest(stock);
                } else if (predictionMode.equals("MULTI")) {
                    File newDir = new File(System.getProperty("user.dir") + "/res/TrainingFiles/MultiStock/");
                    if (!newDir.exists())
                        if (!newDir.mkdirs())
                            updateCurrentTask("Error: Could not create '" + newDir.getAbsolutePath() + "'", true, true);

                    TrainingFileUtils.exportClassificationCSV(stocks, System.getProperty("user.dir") + "/res/TrainingFiles/MultiStock/SmoothedNASDAQTraining.csv", dayArray, stockForecastProgress, smoothRate, true, true, false, false);
                    TrainingFileUtils.exportLibSVMFile(System.getProperty("user.dir") + "/res/TrainingFiles/MultiStock/SmoothedNASDAQTraining.csv", System.getProperty("user.dir") + "/res/TrainingFiles/MultiStock/SmoothedNASDAQTraining.libsvm");
                    StockPredictor.trainRandomForest(System.getProperty("user.dir") + "/res/TrainingFiles/MultiStock/SmoothedNASDAQTraining.libsvm", stocks.size(), false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            updateProgress(0, stockForecastProgress);
            Platform.runLater(() -> {
                try {
                    predictionModelInformationBox.setText(StockPredictor.getModelInformation(stocks));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
            Platform.runLater(() -> trainMLModelButton.setDisable(false));
        }).start();
    }

    @FXML
    private void setLossCutoff() throws SQLException {
        setLossCutoff(Double.valueOf(lossCutoffField.getText()));
        lossCutoffField.clear();
    }

    @FXML
    private void setProfitCutoff() throws SQLException {
        setProfitCutoff(Double.valueOf(profitTargetField.getText()));
        profitTargetField.clear();
    }

    private void setLossCutoff(double amount) throws SQLException {
        lossCutoff = (int) (amount * 100) / 100.0;
        Platform.runLater(() -> cutoffLabel.setText(String.valueOf(lossCutoff)));
        lossCutoffPercentageLabel.setText("0.0%");
        dh.executeCommand("INSERT INTO settings VALUES ('LOSS_CUTOFF', '" + lossCutoff + "') ON DUPLICATE KEY UPDATE Value = VALUES(Value);");
    }

    private void setProfitCutoff(double amount) throws SQLException {
        profitCutoff = (int) (amount * 100) / 100.0;
        Platform.runLater(() -> targetLabel.setText(String.valueOf(profitCutoff)));
        profitTargetPercentageLabel.setText("0.0%");
        dh.executeCommand("INSERT INTO settings VALUES ('PROFIT_CUTOFF', '" + profitCutoff + "') ON DUPLICATE KEY UPDATE Value = VALUES(Value);");
    }

    private void calculateLossCutoff(double percentage) throws SQLException {
        float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));
        lossCutoff = (balance + TradingUtils.getStockWorth()) * (1 - percentage);

        setLossCutoff(lossCutoff);
    }

    private void calculateTargetCutoff(double percentage) throws SQLException {
        float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));
        profitCutoff = (balance + TradingUtils.getStockWorth()) * (1 + percentage);

        setProfitCutoff(profitCutoff);
    }

    private void initialiseConnections() throws IOException, SQLException {
        updateCurrentTask("Initialising Connections...", false, false);

        //Based on code from http://code.makery.ch/blog/javafx-dialogs-official/
        Dialog<Pair<String, String>> dialogue = new Dialog<>();
        dialogue.setTitle("SQL Server Login");

        ButtonType loginButton = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialogue.getDialogPane().getButtonTypes().addAll(loginButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(10);

        TextField user = new TextField();
        user.setPromptText("SQL Server Admin Username");
        PasswordField pass = new PasswordField();
        pass.setPromptText("SQL Server Admin Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(user, 1, 0);
        grid.add(pass, 1, 1);

        Node login = dialogue.getDialogPane().lookupButton(loginButton);
        login.setDisable(true);

        user.textProperty().addListener((observable, oldValue, newValue) -> login.setDisable(newValue.trim().isEmpty()));
        dialogue.getDialogPane().setContent(grid);

        Platform.runLater(user::requestFocus);

        dialogue.setResultConverter(dialogButton -> {
            if (dialogButton == loginButton)
                return new Pair<>(user.getText(), pass.getText());
            return null;
        });

        Optional<Pair<String, String>> result = dialogue.showAndWait();

        result.ifPresent(userPass -> {
            //End of 3rd Party Code
            try {
                DatabaseHandler.initialiseDatabase(userPass.getKey(), userPass.getValue());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        dh.init("Agent", "0Y5q0m28pSB9jj2O");
        nlpdh.init("NaturalLanguageProcessor", "p1pONM8zhI6GgCfy");
        tadh.init("TechnicalAnalyser", "n6qvdUkFOoFCxPq5");
        nddh.init("NewsDownloader", "wu0Ni6YF3yLTVp2A");
        sqdh.init("StockQuoteDownloader", "j2wbvx19Gg1Be22J");
        pmdh.init("PortfolioManager", "mAjwa22NdsrRihi4");
        spdh.init("StockPredictor", "wfN1XLoW810diEhR");

        ArrayList<String> alphavantageKey = dh.executeQuery("SELECT value FROM settings WHERE ID='ALPHAVANTAGE_API_KEY';");
        ArrayList<String> intrinioUser = dh.executeQuery("SELECT value FROM settings WHERE ID='INTRINIO_API_USER';");
        ArrayList<String> intrinioKey = dh.executeQuery("SELECT value FROM settings WHERE ID='INTRINIO_API_KEY';");
        ArrayList<String> barchartKey = dh.executeQuery("SELECT value FROM settings WHERE ID='BARCHART_API_KEY';");

        boolean keyMissing = false;
        if (alphavantageKey.isEmpty() || alphavantageKey.get(0).toUpperCase().equals("NULL")) {
            updateCurrentTask("No AlphaVantage API Key found! Please go to https://www.alphavantage.co/ to retrieve one and save key in database with ID 'ALPHAVANTAGE_API_KEY'.", true, true);
            keyMissing = true;
        }

        if (barchartKey.isEmpty() || barchartKey.get(0).toUpperCase().equals("NULL")) {
            updateCurrentTask("No Barchart API Key found! Please go to https://www.barchart.com/ondemand/free-market-data-api to retrieve one and save key in database with ID 'BARCHART_API_KEY'.", true, true);
            keyMissing = true;
        }

        if (intrinioUser.isEmpty() || intrinioUser.get(0).toUpperCase().equals("NULL")) {
            updateCurrentTask("No INTRINIO User ID found! Please go to https://intrinio.com/signup to retrieve one and save User ID in database with ID 'INTRINIO_API_USER' and API Key with ID 'INTRINIO_API_KEY'.", true, true);
            keyMissing = true;
        }

        if (intrinioKey.isEmpty() || intrinioKey.get(0).toUpperCase().equals("NULL")) {
            updateCurrentTask("No INTRINIO API Key found! Please go to https://intrinio.com/signup to retrieve one and save User ID in database with ID 'INTRINIO_API_USER' and API Key with ID 'INTRINIO_API_KEY'.", true, true);
            keyMissing = true;
        }

        if (keyMissing) System.exit(-1);

        avh.init(alphavantageKey.get(0));
        bch.init(barchartKey.get(0), sqdh, stockFeedProgress);
        INTRINIOHandler.authenticate(intrinioUser.get(0), intrinioKey.get(0));
    }

    @FXML private void smoothPriceData(){
        new Thread(()-> {
            Platform.runLater(() -> smoothPriceDataButton.setDisable(true));
            try {
                TrainingFileUtils.resetPriceValues();
                TechnicalAnalyser.calculatePercentChanges(stocks);
                SmoothingUtils.smoothStocks(stocks, smoothRate);
                TechnicalAnalyser.calculateTechnicalIndicators(stocks, true, false);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Platform.runLater(() -> smoothPriceDataButton.setDisable(false));
            }
        }).start();
    }

    @FXML private void resetPriceData(){
        new Thread(()-> {
            Platform.runLater(()->resetPriceDataButton.setDisable(true));
            try {
                TrainingFileUtils.resetPriceValues();
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                Platform.runLater(() -> resetPriceDataButton.setDisable(false));
            }
        }).start();
    }

    @FXML
    public void setAutonomyLevel() {
        String level = autonomyLevelDropdown.getValue().toString();
        boolean fullyAutonomous = level.equals("Full-Autonomy"),
                semiAutonomous = level.equals("Semi-Autonomy");

        autonomousToolbox.setDisable(fullyAutonomous);
        manualToolbox.setDisable(fullyAutonomous || semiAutonomous);

        automated = fullyAutonomous || semiAutonomous;
    }

    private void updateProfitLossChart() throws SQLException, ParseException {
        Platform.runLater(() -> portfolioChart.getData().clear());
        ArrayList<String> portfolioRecords = dh.executeQuery("SELECT Symbol, Held, Investment, LastUpdated FROM portfolio WHERE Held > 0 ORDER BY LastUpdated ASC;");

        Map<Long, Double> timeAndPrice = new TreeMap<>();

        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (String record : portfolioRecords) {
            String[] splitRecord = record.split(",");

            double held = Double.parseDouble(splitRecord[1]),
                    cost = Double.parseDouble(splitRecord[2]);
            String command = "SELECT ClosePrice, TradeDateTime FROM intradaystockprices WHERE Symbol = '" + splitRecord[0] + "' AND TradeDateTime >= '" + Timestamp.valueOf(splitRecord[3]).toLocalDateTime() + "' ORDER BY TradeDateTime ASC;";
            for (String iRecord : dh.executeQuery(command)) {
                String[] splitIRecord = iRecord.split(",");

                double profitLoss = (Double.parseDouble(splitIRecord[0]) * held) - cost;
                long epoch = format.parse(splitIRecord[1]).getTime();

                timeAndPrice.put(epoch, timeAndPrice.getOrDefault(epoch, 0.0) + profitLoss);
            }
        }

        XYChart.Series<Number, Number> profitLossData = new XYChart.Series<>();

        int i = 0;
        double currProfitLoss = 0;

        for (Long time : timeAndPrice.keySet()) {
            XYChart.Data<Number, Number> point = createChartPoint(i, timeAndPrice.get(time));
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

        if (result.isPresent() && result.get() == ButtonType.YES)
            new Thread(() -> {
                Platform.runLater(() -> rebalanceButton.setDisable(true));
                try {
                    rebalancePortfolio();
                } catch (Exception e) { e.printStackTrace(); }
                Platform.runLater(() -> rebalanceButton.setDisable(false));
            }).start();
    }


    private void rebalancePortfolio() throws SQLException, ParseException {
        //TradingUtils.sellAllStock(automated);

        PortfolioManager.OptimisationMethod om = null;
        PortfolioManager.EvaluationMethod em = null;

        switch(optimisationMethodDropdown.getSelectionModel().getSelectedItem()){
            case "Simulated Annealing":
                om = PortfolioManager.OptimisationMethod.SIMULATED_ANNEALING;
                break;
            case "Genetic Algorithm":
                om = PortfolioManager.OptimisationMethod.GENETIC_ALGORITHM;
                break;
            case "Deterministic Search":
                om = PortfolioManager.OptimisationMethod.DETERMINISTIC;
                break;
        }

        switch(evaluationMethodDropdown.getSelectionModel().getSelectedItem()){
            case "Maximise Return":
                em = PortfolioManager.EvaluationMethod.MAXIMISE_RETURN;
                break;
            case "Balance Return vs. Risk":
                em = PortfolioManager.EvaluationMethod.MAXIMISE_RETURN_MINIMISE_RISK;
                break;
        }

        TreeMap<String, TreeMap<Date, Double>> prices = new TreeMap<>();

        for(String stock : stocks)
            prices.put(stock, PortfolioManager.getPrices(stock, 200));

        double totalAllocation = Double.valueOf(dh.executeQuery("SELECT COALESCE(SUM(Allocation), 0) FROM portfolio;").get(0));
        Map<String, Double> currentPortfolio = new TreeMap<>();
        for (String stock : stocks) currentPortfolio.put(stock, 0.0);
        for (String record : dh.executeQuery("SELECT Symbol, COALESCE(Allocation, 0) FROM portfolio;")) {
            String[] splitString = record.split(",");
            currentPortfolio.put(splitString[0], Double.valueOf(splitString[1]) / totalAllocation);
        }

        Map<String, Double> portfolio = PortfolioManager.optimisePortfolio(om, em, 1, prices, currentPortfolio, true);
        double cutoff = portfolio.get("RETURN");

        calculateLossCutoff(cutoff);
        calculateTargetCutoff(cutoff);

        portfolio.remove("RETURN");

        StringBuilder command = new StringBuilder("INSERT INTO portfolio (Symbol, Allocation, Held, LastUpdated) VALUES \r\n");

        int count = 1;
        double availableFunds = Double.parseDouble(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions;").get(0));

        for (String stock : portfolio.keySet()) {
            double allocation = availableFunds * portfolio.get(stock);

            String lastUpdated = dh.executeQuery("SELECT MAX(TradeDateTime) FROM intradaystockprices WHERE Symbol='" + stock + "';").get(0);

            command.append("('").append(stock).append("', ").append(allocation).append(", 0, '").append(lastUpdated).append("')");
            if (count++ < portfolio.size())
                command.append(", \r\n");
            else
                command.append(" ON DUPLICATE KEY UPDATE Allocation = VALUES(Allocation);");
        }

        dh.executeCommand(command.toString());
        updateGUI();
    }

    private void initialiseListeners() {
        newsArticleList.getSelectionModel().selectedItemProperty().addListener((observableValue, s, t1) -> {
            if (newsArticleList.getSelectionModel().getSelectedItems().size() > 1)
                newsArticleList.getSelectionModel().clearSelection();

            if (newsArticleList.getSelectionModel().getSelectedItem() != null) {
                try {
                    newsArticleArea.setText(dh.executeQuery("SELECT DISTINCT(Content) FROM newsarticles WHERE Symbol='" + newsStockCombobox.getValue() + "' AND PublishedDate = '" + newsDateCombobox.getValue() + "' AND Headline = '" + newsArticleList.getSelectionModel().getSelectedItem() + "' AND Content IS NOT NULL LIMIT 1;").get(0));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        });

        newsDateCombobox.valueProperty().addListener((observableValue, s, t1) -> {
            newsArticleList.getItems().clear();
            try {
                newsArticleList.getItems().addAll(dh.executeQuery("SELECT DISTINCT(Headline) FROM newsarticles WHERE Symbol='" + newsStockCombobox.getValue() + "' AND PublishedDate = '" + newsDateCombobox.getValue() + "' AND Content IS NOT NULL ORDER BY PublishedDate DESC;"));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        newsStockCombobox.valueProperty().addListener((observableValue, s, t1) -> {
            newsDateCombobox.getItems().clear();

            if (newsDateCombobox.isDisabled())
                newsDateCombobox.setDisable(false);

            try {
                newsDateCombobox.getItems().addAll(dh.executeQuery("SELECT DISTINCT(PublishedDate) FROM newsarticles WHERE Symbol='" + newsStockCombobox.getValue() + "' AND Content IS NOT NULL ORDER BY PublishedDate DESC;"));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        lossCutoffField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*\\.?\\d*"))
                lossCutoffField.setText(newValue.replaceAll("[^\\d.]", ""));
            setLossCutoffButton.setDisable(newValue.isEmpty());
            if(!newValue.isEmpty()){
                try {
                    double worth = TradingUtils.getTotalWorth();
                    setLossCutoffButton.setDisable(Double.parseDouble(lossCutoffField.getText()) >= worth);
                    lossCutoffPercentageLabel.setText(String.valueOf(Double.parseDouble(lossCutoffField.getText()) / worth * 100) + "%");
                } catch (SQLException e) { e.printStackTrace();}
            }
        });

        profitTargetField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*\\.?\\d*"))
                profitTargetField.setText(newValue.replaceAll("[^\\d.]", ""));

            setProfitTargetButton.setDisable(newValue.isEmpty());
            if(!newValue.isEmpty()){
                try {
                    double worth = TradingUtils.getTotalWorth();
                    setProfitTargetButton.setDisable(Double.parseDouble(profitTargetField.getText()) <= worth);
                    profitTargetPercentageLabel.setText(String.valueOf(Double.parseDouble(profitTargetField.getText()) / worth * 100) + "%");
                } catch (SQLException e) { e.printStackTrace();}
            }
        });

        stockAmountField.textProperty().addListener(((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*"))
                stockAmountField.setText(newValue.replaceAll("[^\\d]", ""));

            boolean disable = newValue.isEmpty();

            String stock = stockDropdown.getValue();

            Platform.runLater(() -> {
                try {
                    buyButton.setDisable(disable || !TradingUtils.canBuyStock(stock, Integer.parseInt(newValue)));
                    sellButton.setDisable(disable || !TradingUtils.canSellStock(stock, Integer.parseInt(newValue)));
                } catch (SQLException e) {e.printStackTrace();}
            });
        }));

        simulationPortfolioHoldPeriod.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*\\.?\\d*"))
                simulationPortfolioHoldPeriod.setText(newValue.replaceAll("[^\\d.]", ""));

            simulateTradingButton.setDisable(simulationPortfolioHoldPeriod.getText().isEmpty() || simulationPortfolioLookbackPeriod.getText().isEmpty());
        });

        simulationPortfolioLookbackPeriod.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*\\.?\\d*"))
                simulationPortfolioLookbackPeriod.setText(newValue.replaceAll("[^\\d.]", ""));

            simulateTradingButton.setDisable(simulationPortfolioHoldPeriod.getText().isEmpty() || simulationPortfolioLookbackPeriod.getText().isEmpty());
        });
    }

    private void updatePredictions(HashMap<String, HashMap<Integer, Boolean>> predictionValues) {
        for (String stock : stocks) {
            boolean[] currentPredictions = new boolean[dayArray.length];
            int i = 0;
            for (int day : dayArray)
                currentPredictions[i++] = predictionValues.get(stock).get(day);
            PredictionBox pb = predictions.get(stock);
            Platform.runLater(()->pb.updateValues(currentPredictions));
        }
    }

    public void initialiseSimulatorPredictions(HashMap<String, HashMap<Integer, Boolean>> predictions) {
        if (!simulatorPredictions.isEmpty())
            simulatorPredictions.clear();

        Label spacer = new Label();
        spacer.setMinSize(60, 20);

        Label[] predictionLabels = new Label[dayArray.length];

        for (int i = 0; i < predictionLabels.length; i++) {
            predictionLabels[i] = new Label();
            predictionLabels[i].setFont(Font.font(null, 14));
            predictionLabels[i].setMinSize(30, 20);
            predictionLabels[i].setTextFill(Color.BLACK);
            predictionLabels[i].setText(String.valueOf(dayArray[i]));
        }

        HBox header = new HBox(spacer);
        header.getChildren().addAll(predictionLabels);

        for (String stock : stocks) {
            boolean[] currentPredictions = new boolean[dayArray.length];
            int i = 0;
            for (int day : dayArray)
                currentPredictions[i++] = predictions.get(stock).get(day);

            simulatorPredictions.put(stock, new SimulatorPredictionBox(stock, dayArray, currentPredictions));
        }

        Platform.runLater(() -> simulatorPredictionBox.getChildren().add(header));
        for (String box : simulatorPredictions.keySet())
            Platform.runLater(() -> simulatorPredictionBox.getChildren().add(simulatorPredictions.get(box).getNode()));
    }

    private void initialiseSimulatorCharts(){
        Platform.runLater(() -> simulatorProfitLoss.getData().add(simulatedPortfolioInitialPerformance));
        Platform.runLater(() -> simulatorProfitLoss.getData().add(simulatedBalance));
        Platform.runLater(() -> simulatorProfitLoss.getData().add(simulatedEqualAllocationPerformance));
        Platform.runLater(() -> simulatorProfitLoss.getData().add(simulatedRandomPerformance));
        Platform.runLater(() -> simulatorProfitLoss.getData().add(simulatedIndexPerformance));

        simulatedPortfolioInitialPerformance.setName("Automated Trader");
        simulatedEqualAllocationPerformance.setName("Automated Trader (Equal Allocation)");
        simulatedBalance.setName("Automated Trader (With Portfolio Rebalancing)");
        simulatedIndexPerformance.setName("NASDAQ 100 Index (^NDX)");
        simulatedRandomPerformance.setName("Random Trader");

        for(String stock :stocks){
            simulatedHistory.put(stock, new XYChart.Series<>());
            final NumberAxis xAxis = new NumberAxis();
            final NumberAxis yAxis = new NumberAxis();
            yAxis.setTickUnit(1);
            xAxis.setTickUnit(1);
            LineChart<Number, Number> priceChart = new LineChart<>(xAxis, yAxis);
            priceChart.setMaxHeight(300);
            priceChart.setMinHeight(300);
            priceChart.setMaxWidth(1700);
            priceChart.setMinWidth(1700);
            priceChart.setLegendVisible(false);
            yAxis.setUpperBound(0);
            yAxis.setLowerBound(0);
            simulatorAxes.put(stock, yAxis);
            priceChart.setTitle(stock);
            yAxis.setAutoRanging(false);
            priceChart.getData().add(simulatedHistory.get(stock));
            Platform.runLater(()-> priceHistoryChartBox.getChildren().add(priceChart));
        }
    }

    public void addSimulatorRandomPerformance(int index, double balance){
        Platform.runLater(() -> simulatedRandomPerformance.getData().add(createChartPoint(index, balance)));
    }

    public void addSimulatorIndexPerformance(int index, double balance){
        Platform.runLater(() -> simulatedIndexPerformance.getData().add(createChartPoint(index, balance)));
    }

    public void addSimulatorPortfolioInitialBalance(int index, double balance) {
        Platform.runLater(() -> simulatedPortfolioInitialPerformance.getData().add(createChartPoint(index, balance)));
    }

    public void addSimulatorEqualAllocationBalance(int index, double balance) {
        Platform.runLater(() -> simulatedEqualAllocationPerformance.getData().add(createChartPoint(index, balance)));
    }

    public void addSimulatorBalance(int index, double balance){
        Platform.runLater(() -> simulatedBalance.getData().add(createChartPoint(index, balance)));
    }

    public void addHistoricPrice(String stock, int index, double price){
        Platform.runLater(() -> simulatedHistory.get(stock).getData().add(createChartPoint(index, price)));
    }

    @FXML
    private void simulateTrading(){
        new Thread(()-> {
            Platform.runLater(() -> simulateTradingButton.setDisable(true));
            try {
                int lookbackPeriod = Integer.parseInt(simulationPortfolioLookbackPeriod.getText());
                boolean singleStock = dh.executeQuery("SELECT value FROM settings WHERE ID='PREDICTION_MODE';").get(0).equals("SINGLE");
                int holdPeriod = Integer.parseInt(simulationPortfolioHoldPeriod.getText());

                TradingSimulator.simulate(stocks, singleStock, lookbackPeriod, holdPeriod, Arrays.stream(dayArray).boxed().toArray(Integer[]::new));
            } catch (Exception e) {
                e.printStackTrace();
            }finally{
                Platform.runLater(() -> simulateTradingButton.setDisable(false));
            }
        }).start();
    }

    public void updateSimulationPredictions(HashMap<String, HashMap<Integer, Boolean>> predictions) {
        for (String stock : stocks) {
            boolean[] currentPredictions = new boolean[dayArray.length];
            int i = 0;
            for (int day : dayArray)
                currentPredictions[i++] = predictions.get(stock).get(day);
            SimulatorPredictionBox pb = simulatorPredictions.get(stock);
            Platform.runLater(() -> pb.updateValues(currentPredictions));
        }
    }

    public void realignSimulatorCharts(){
        ArrayList<Double> performance = new ArrayList<>();
        Platform.runLater(()-> {
            for (XYChart.Data indexData : simulatedIndexPerformance.getData())
                if (indexData != null)
                    performance.add((double) indexData.getYValue());
            for (XYChart.Data balanceData : simulatedPortfolioInitialPerformance.getData())
                if (balanceData != null)
                    performance.add((double) balanceData.getYValue());
            for (XYChart.Data balanceData : simulatedEqualAllocationPerformance.getData())
                if (balanceData != null)
                    performance.add((double) balanceData.getYValue());
            for (XYChart.Data balanceData : simulatedBalance.getData())
                if (balanceData != null)
                    performance.add((double) balanceData.getYValue());
            for (XYChart.Data randomData : simulatedRandomPerformance.getData())
                if (randomData != null)
                    performance.add((double) randomData.getYValue());

            setBounds(simulatorPerformanceAxis, performance);
            formatLineChart(simulatorProfitLoss.getData());
        });

        for(String stock : stocks){
            ArrayList<Double> prices = new ArrayList<>();
            Platform.runLater(()-> {
                formatLineChart(simulatedHistory.get(stock));
                for (XYChart.Data data : simulatedHistory.get(stock).getData())
                    if (data != null)
                        prices.add((double) data.getYValue());

                if (prices.isEmpty())
                    System.err.println("Price Chart Error");

                setBounds(simulatorAxes.get(stock), prices);
            });
        }
    }

    @FXML
    public void initialize() throws Exception {
        DISABLE_SYSTEM_UPDATE = Arrays.asList(Main.getArguments()).contains("-DSU");

        File res = new File("res");
        if(!res.exists())
            if (!res.mkdirs())
                updateCurrentTask("Error: Could not create '" + res.getAbsolutePath() + "'", true, true);

        System.out.println(res.getPath() + " " + res.getAbsolutePath() + " " + System.getProperty("user.dir"));

        mainThread = new Thread(() -> {
            while (!quit) {
                int seconds = LocalTime.now().getSecond();
                int minutes = LocalTime.now().getMinute();
                int hours = LocalTime.now().getHour();

                //DOWNLOAD INTRADAY DATA FOR VISUALISATION PURPOSES
                if (seconds == 0) {
                    try {
                        if (hours < 21 && hours >= 14) {
                            StockQuoteDownloader.updateIntradayStockData(records);
                            StockQuoteDownloader.updateDailyStockData(records);
                            double totalWorth = TradingUtils.getTotalWorth();
                            if (totalWorth <= lossCutoff || totalWorth >= profitCutoff) rebalancePortfolio();
                            SmoothingUtils.smoothStocks(stocks, smoothRate);
                            TechnicalAnalyser.calculateTechnicalIndicators(stocks, true, false);
                            TechnicalAnalyser.calculatePercentChanges(stocks);
                            if (StockPredictor.isModelLoaded())
                                updatePredictions(StockPredictor.predictStocks(stocks, dayArray, stockForecastProgress));
                        }
                        if (minutes == 0)
                            try {
                                NewsDownloader.updateNews(stocks);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        if (automated && StockPredictor.isModelLoaded())
                            TradingUtils.autoTrade(stocks, dayArray);
                        if (automated || (hours < 21 && hours >= 14) || minutes == 0) {
                            checkServices();
                            updateGUI();
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });

        initialiseConnections();
        initialiseListeners();

        StockQuoteDownloader.initialise(sqdh, avh, bch, stockFeedProgress, StockQuoteDownloader.STOCK_API.AlphaVantage);
        StockRecordParser.initialise(sqdh);
        NaturalLanguageProcessor.initialise(nlpdh, nlpProgress);
        SmoothingUtils.initialise(tadh, technicalAnalyserProgress);
        TechnicalAnalyser.initialise(tadh, technicalAnalyserProgress);
        INTRINIOHandler.initialise(nddh, newsFeedProgress);
        PortfolioManager.initialise(pmdh); //TODO: Get a progessbar for this
        StockPredictor.initialise(spdh, stockForecastProgress);
        TrainingFileUtils.setDatabaseHandler(dh);
        TradingUtils.setDatabaseHandler(dh);
        TradingSimulator.initialise(dh);

        initialiseStocks();
        if (!Arrays.asList(Main.getArguments()).contains("-DLM")) {
            new Thread(() -> {
                try {
                    switch (dh.executeQuery("SELECT value FROM settings WHERE ID = 'PREDICTION_MODE';").get(0)) {
                        case "SINGLE":
                            StockPredictor.loadLatestRandomForest(stocks);
                            break;
                        case "MULTI":
                            StockPredictor.loadLatestRandomForest();
                            break;
                        default:
                            updateCurrentTask("Invalid setting for value 'PREDICTION_MODE'!", true, true);
                            break;
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        initialiseSimulatorCharts();

        initialiseClocks();
        initialiseDisplay();
        startClocks();

        Platform.runLater(()->stockDropdown.getItems().addAll(stocks));
        Platform.runLater(() -> newsStockCombobox.getItems().addAll(stocks));
        Platform.runLater(() -> historicStockDropdown.getItems().addAll(stocks));
        final String modelInfromation = StockPredictor.getModelInformation(stocks);
        Platform.runLater(() -> predictionModelInformationBox.setText(modelInfromation));
        Platform.runLater(()->autonomyLevelDropdown.getSelectionModel().selectFirst());
        Platform.runLater(()->optimisationMethodDropdown.getSelectionModel().selectFirst());
        Platform.runLater(()->evaluationMethodDropdown.getSelectionModel().selectFirst());

        new Thread(() -> {
            try {
                updateSystem();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateStockValues() throws SQLException {
        float finalWorth = TradingUtils.getStockWorth();
        Platform.runLater(() -> stockValueLabel.setText(String.valueOf(finalWorth)));
    }

    private void startClocks() {
        updateCurrentTask("Starting Clocks", false, false);
        new Thread(() -> {
            while (!quit) {
                try {TimeUnit.MILLISECONDS.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
                updateClocks();
            }
        }).start();
    }

    private void updateBankBalance() throws SQLException {
        final double balance = TradingUtils.getBalance();
        Platform.runLater(() -> currentBalanceLabel.setText(String.valueOf(balance)));
    }

    private void printToInfoBox(String string) {
        Platform.runLater(() -> infoBox.appendText(string + "\r\n"));
    }

    private void initialiseDisplay() throws SQLException {
        updateCurrentTask("Initialising Display", false, false);
        ArrayList<String> stockInfo = dh.executeQuery("SELECT Symbol, Name FROM indices;");

        Map<String, String> stockNames = new HashMap<>();

        for (String curr : stockInfo) {
            String[] values = curr.split(",");
            stockNames.put(values[0], values[1]);
        }

        for (String curr : stocks) records.add(new LiveStockRecord(curr, stockNames.get(curr), dh));
        for (LiveStockRecord rec : records) Platform.runLater(() -> stockList.getChildren().add(rec.getNode()));
    }

    private void updateSystem() throws SQLException, InterruptedException, ParseException {
        Thread stockThread = new Thread(() -> {
            try {
                StockRecordParser.processYahooHistories(stocks, stockFeedProgress);
                dh.executeCommand("DELETE FROM intradaystockprices WHERE Temporary = 1;");
                bch.downloadHistory(stocks, false);
                bch.downloadHistory(stocks, true);
                StockQuoteDownloader.downloadStockHistory(stocks, true, true, false);
            } catch (Exception e) { e.printStackTrace(); }
        });

        Thread taThread = new Thread(() -> {
            try {
                TechnicalAnalyser.calculatePercentChanges(stocks);
                SmoothingUtils.smoothStocks(stocks, smoothRate);
                TechnicalAnalyser.calculateTechnicalIndicators(stocks, true, false);
            } catch (Exception e) { e.printStackTrace(); }
        });

        Thread newsThread = new Thread(() -> {
            try {
                nddh.setWriteToFile(true);
                NewsDownloader.updateNews(stocks);
                nddh.setWriteToFile(false);
                nddh.sendSQLFileToDatabase(false);

                INTRINIOHandler.downloadArticles(); //Has to be done individually to check for duplicate values
            } catch (Exception e) { e.printStackTrace(); }

            try {
                NaturalLanguageProcessor.enumerateSentencesFromArticles();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread nlpThread = new Thread(() -> {
            try {
                NaturalLanguageProcessor.enumerateNGramsFromArticles(2);
                NaturalLanguageProcessor.processArticlesForSentiment(2);
            } catch (SQLException e) { e.printStackTrace(); }
        });

        updateGUI();

        setProfitCutoff(Double.parseDouble(dh.executeQuery("SELECT Value FROM settings WHERE ID = 'PROFIT_CUTOFF';").get(0)));
        setLossCutoff(Double.parseDouble(dh.executeQuery("SELECT Value FROM settings WHERE ID = 'LOSS_CUTOFF';").get(0)));
        checkServices();

        if(!DISABLE_SYSTEM_UPDATE) {
            newsThread.start();
            stockThread.start();

            stockThread.join();
            taThread.start();
            newsThread.join();

            nlpThread.start();
            nlpThread.join();
            taThread.join();
        }

        new Thread(()-> {
            for (LiveStockRecord curr : records) {
                curr.updateRecord(sqdh);
                curr.updateChart(sqdh, false);
            }
        }).start();

        if (StockPredictor.isModelLoaded())
            new Thread(() -> {
                HashMap<String, HashMap<Integer, Boolean>> predictionArray = null;
                try {
                    predictionArray = StockPredictor.predictStocks(stocks, dayArray, stockForecastProgress);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Label spacer = new Label();
                spacer.setMinSize(80, 20);

                Label[] predictionLabels = new Label[dayArray.length];

                for (int i = 0; i < predictionLabels.length; i++) {
                    predictionLabels[i] = new Label();
                    predictionLabels[i].setFont(Font.font(null, 14));
                    predictionLabels[i].setMinSize(120, 20);
                    predictionLabels[i].setTextFill(Color.BLACK);
                    predictionLabels[i].setText(dayArray[i] + " day");

                    if (dayArray[i] != 1) predictionLabels[i].setText(predictionLabels[i].getText() + "s");
                }

                    HBox header = new HBox(spacer);
                    header.getChildren().addAll(predictionLabels);
                    Platform.runLater(() -> stockPredictionsBox.getChildren().add(header));

                for (String stock : stocks) {
                    boolean[] currentPredictions = new boolean[dayArray.length];
                    int i = 0;
                    for (int days : dayArray)
                        currentPredictions[i++] = Objects.requireNonNull(predictionArray).get(stock).get(days);

                    predictions.put(stock, new PredictionBox(stock, dayArray, currentPredictions));
                }

                for (String pb : predictions.keySet())
                    Platform.runLater(() -> stockPredictionsBox.getChildren().add(predictions.get(pb).getNode()));
            }).start();

        Platform.runLater(() -> trainMLModelButton.setDisable(false));
        Platform.runLater(() -> controlPanel.setDisable(false));

        updateGUI();

        if (!Arrays.asList(Main.getArguments()).contains("-DMT")) mainThread.start();
    }

    private void initialiseClocks() {
        updateCurrentTask("Initialising Clocks", false, false);

        Map<LocalTime, LocalTime> jpBreak = new HashMap<>(), hkBreak = new HashMap<>();

        jpBreak.put(LocalTime.of(11, 30), LocalTime.of(12, 30));
        hkBreak.put(LocalTime.of(12, 0), LocalTime.of(13, 0));

        clocks.add(new StockClock("NASDAQ", LocalTime.of(9, 30), null, LocalTime.of(16, 0), ZoneId.of("America/New_York")));
        clocks.add(new StockClock("London SE", LocalTime.of(8, 0), null, LocalTime.of(16, 30), ZoneId.of("Europe/London")));
        clocks.add(new StockClock("Euronext", LocalTime.of(9, 0), null, LocalTime.of(17, 30), ZoneId.of("CET")));
        clocks.add(new StockClock("Japan Exchange Group", LocalTime.of(9, 0), jpBreak, LocalTime.of(15, 0), ZoneId.of("Asia/Tokyo")));
        clocks.add(new StockClock("Hong Kong SE", LocalTime.of(9, 30), hkBreak, LocalTime.of(16, 0), ZoneId.of("Asia/Hong_Kong")));
        clocks.add(new StockClock("Korea Exchange", LocalTime.of(9, 0), null, LocalTime.of(15, 30), ZoneId.of("Asia/Tokyo")));
        clocks.add(new StockClock("Australia SX", LocalTime.of(9, 50), null, LocalTime.of(16, 12), ZoneId.of("Australia/Canberra")));
        clocks.add(new StockClock("Deutsche Börse", LocalTime.of(8, 0), null, LocalTime.of(22, 0), ZoneId.of("CET")));
        clocks.add(new StockClock("SIX Swiss Exchange", LocalTime.of(9, 0), null, LocalTime.of(17, 30), ZoneId.of("CET")));
        clocks.add(new StockClock("Bombay SE", LocalTime.of(9, 15), null, LocalTime.of(15, 30), ZoneId.of("Asia/Calcutta")));
        clocks.add(new StockClock("JSE Limited", LocalTime.of(9, 0), null, LocalTime.of(17, 0), ZoneId.of("Africa/Johannesburg")));
        clocks.add(new StockClock("TMX Group", LocalTime.of(9, 30), null, LocalTime.of(16, 0), ZoneId.of("America/New_York")));
        clocks.add(new StockClock("Taiwan SE", LocalTime.of(9, 0), null, LocalTime.of(13, 30), ZoneId.of("Asia/Hong_Kong")));

        for (StockClock clock : clocks) timePane.getChildren().add(clock.getNode());
    }

    private void checkServices() throws SQLException {
        int newsCalls = 0;
        int callLimit = Integer.parseInt(dh.executeQuery("SELECT COALESCE(DailyLimit,0) FROM apimanagement WHERE Name='INTRINIO';").get(0));

        ArrayList<String> calls = dh.executeQuery("SELECT Calls FROM apicalls WHERE Name='INTRINIO' AND Date=CURDATE()");

        if (!calls.isEmpty())
            newsCalls = Integer.parseInt(calls.get(0));

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

        if (NewsDownloader.getNewsUpdating()) {
            nlpAvailability.setFill(Color.ORANGE);
            nlpAvailability.setStroke(Color.ORANGE);
        } else {
            nlpAvailability.setFill(Color.GREEN);
            nlpAvailability.setStroke(Color.GREEN);
        }
    }

    @FXML
    private void initialiseStocks() throws SQLException {
        updateCurrentTask("Initialising stocks", false, false);
        stocks = dh.executeQuery("SELECT Symbol FROM indices");
    }

    public void updateGUI() throws SQLException, ParseException {
        updateStocksOwned();
        updateComponentChart();
        updateAllocationChart();
        updateProfitLoss();
        updateProfitLossChart();
        updateBankBalance();
        updateStockValues();
        updateTotalWorth();
        updateNewsGUI(dh.executeQuery("SELECT DISTINCT Symbol, Headline, Published FROM newsarticles WHERE DATE(Published) = CURDATE() ORDER BY Published DESC"));
    }

    private void updateStocksOwned() throws SQLException {
        ArrayList<String> heldStocks = dh.executeQuery("SELECT Held, Symbol FROM portfolio");

        Platform.runLater(() -> stockBox.getChildren().clear());

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            Platform.runLater(() -> stockBox.getChildren().add(new Label(splitStock[0] + '\t' + splitStock[1])));
        }
    }

    private void updateClocks() {
        for (StockClock clock : clocks) clock.updateTime();
    }

    private void updateProfitLoss() throws SQLException {
        final float investmentCost = Float.parseFloat(dh.executeQuery("SELECT COALESCE(SUM(Investment),0) FROM portfolio").get(0));
        final float potentialTotal = TradingUtils.getStockWorth();
        final float total = potentialTotal - investmentCost;

        Platform.runLater(() -> profitLossLabel.setText(String.valueOf(Math.round(total * 100.0) / 100.0)));

        if (total > 0)
            Platform.runLater(() -> profitLossLabel.setTextFill(Color.GREEN));
        else if (total == 0)
            Platform.runLater(() -> profitLossLabel.setTextFill(Color.BLACK));
        else
            Platform.runLater(() -> profitLossLabel.setTextFill(Color.RED));

    }

    private void updateTotalWorth() throws SQLException {
        final double value = Math.round(TradingUtils.getTotalWorth() * 100.0) / 100.0;
        Platform.runLater(() -> totalBalanceLabel.setText(String.valueOf(value)));
    }

    public void updateSimulatedComponentChart(String name, Map<String, Double> portfolio) {
        PieChart chart = null;
        switch (name) {
            case "REBALANCED_ALLOC":
                chart = simulatorRebalancedPortfolioChart;
                break;
            case "INITIAL_ALLOC":
                chart = simulatorInitialPortfolioChart;
                break;
        }

        PieChart finalChart = chart;
        Platform.runLater(() -> Objects.requireNonNull(finalChart).getData().clear());

        if (portfolio.isEmpty()) return;

        ArrayList<PieChart.Data> piechartData = new ArrayList<>();

        for (String stock : portfolio.keySet())
            piechartData.add(new PieChart.Data(stock, portfolio.get(stock)));

        Platform.runLater(() -> Objects.requireNonNull(finalChart).getData().addAll(piechartData));
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
    private void buyStock() throws SQLException, ParseException {
        String stock = stockDropdown.getValue();
        int amount = Integer.parseInt(stockAmountField.getText());

        stockAmountField.clear();

        TradingUtils.buyStock(stock, amount, 0, false);

        updateGUI();
    }

    @FXML
    private void sellStock() throws SQLException, ParseException {
        String stock = stockDropdown.getValue();
        int amount = Integer.parseInt(stockAmountField.getText());

        stockAmountField.clear();

        TradingUtils.sellStock(stock, amount, false);

        updateGUI();
    }

    private void updateNewsGUI(ArrayList<String> results) {
        Platform.runLater(() -> newsBox.getChildren().clear());
        for (String result : results) {
            String[] splitString = result.split(",");
            NewsRecord temp = new NewsRecord(splitString[0], splitString[1]);
            Platform.runLater(() -> newsBox.getChildren().add(temp.getNode()));
        }
    }
}