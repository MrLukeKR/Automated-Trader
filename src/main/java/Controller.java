import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Pair;
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
    static private final STOCK_API useAPI = STOCK_API.AlphaVantage;
    static private Thread mainThread;
    static private boolean quit = false;
    static private int dayArray[] = new int[]{1};

    static private ArrayList<String> stocks = new ArrayList<>();
    static private ArrayList<LiveStockRecord> records = new ArrayList<>();
    static private ArrayList<PredictionBox> predictions = new ArrayList<>();
    static private ArrayList<StockClock> clocks = new ArrayList<>();

    @FXML CheckBox showRSI;

    //Historic Stock FXML Items
    @FXML ComboBox<String> historicStockDropdown;
    @FXML ComboBox<String> optimisationMethodDropdown;
    @FXML Label historicDateRange;
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
    @FXML CheckBox showCCI;
    @FXML CheckBox showADX10;
    @FXML LineChart<Number, Number> macdChart;
    @FXML CheckBox showAD;
    @FXML CheckBox showOBV;
    @FXML CheckBox showStoOscSlowK;
    @FXML CheckBox showStoOscSlowD;
    @FXML CheckBox showWillR;
    @FXML LineChart<Number, Number> historicPriceChart;
    @FXML VBox historicOptions;
    @FXML AreaChart<Number, Number> historicVolumeChart;
    @FXML LineChart<Number, Number> rsiChart;
    @FXML LineChart<Number, Number> adxChart;
    @FXML LineChart<Number, Number> cciChart;
    @FXML LineChart<Number, Number> adChart;
    @FXML LineChart<Number, Number> obvChart;
    @FXML LineChart<Number, Number> willRChart;
    @FXML LineChart<Number, Number> stoOscChart;
    @FXML Button displayHistoricDataButton;
    //-------------------------

    @FXML Label currentTaskLabel;
    @FXML ComboBox<String> stockDropdown;
    @FXML FlowPane stockList;
    @FXML ProgressBar stockFeedProgress;
    @FXML FlowPane timePane;
    @FXML TextArea infoBox;
    @FXML VBox newsBox;
    @FXML Label stockValueLabel;
    @FXML Label currentBalanceLabel;
    @FXML Label totalBalanceLabel;
    @FXML Label cutoffLabel;
    @FXML Label targetLabel;
    @FXML Label profitLossLabel;
    @FXML PieChart allocationChart;
    @FXML PieChart componentChart;
    @FXML Circle nlpAvailability;
    @FXML Circle newsFeedAvailability;
    @FXML ProgressBar newsFeedProgress;
    @FXML ProgressBar nlpProgress;
    @FXML ComboBox autonomyLevelDropdown;
    @FXML FlowPane autonomousToolbox;
    @FXML FlowPane manualToolbox;
    @FXML Label lossCutoffPercentageLabel;
    @FXML Label profitTargetPercentageLabel;
    @FXML TextField lossCutoffField;
    @FXML TextField profitTargetField;
    @FXML LineChart<Integer, Double> portfolioChart;
    @FXML ProgressBar technicalAnalyserProgress;
    @FXML ProgressBar stockForecastProgress;
    @FXML Circle technicalAnalyserAvailability;
    @FXML TextField stockAmountField;
    @FXML Button sellButton;
    @FXML Button buyButton;
    @FXML Button exportToMLFileButton;
    @FXML Button rebalanceButton;
    @FXML Button setLossCutoffButton;
    @FXML Button setProfitTargetButton;
    @FXML FlowPane controlPanel;
    @FXML VBox stockBox;
    @FXML VBox stockPredictionsBox;
    @FXML TextArea predictionModelInformationBox;

    private boolean automated = false;
    private boolean priceUpdating = false;
    private boolean newsUpdating = false;

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

    public void updateCurrentTask(String task, boolean error, boolean printToInfoBox){
        if(error)
            System.err.println(task);
        else
            System.out.println(task);
        Platform.runLater(()->currentTaskLabel.setText(task));
        if(printToInfoBox)
            printToInfoBox(task);
    }

    @FXML
    private void displayHistoricData() {
        Platform.runLater(()->displayHistoricDataButton.setDisable(true));
        Platform.runLater(()->historicOptions.setDisable(true));

        if (!historicPriceChart.getData().isEmpty()) Platform.runLater(()->historicPriceChart.getData().clear());
        if (!historicVolumeChart.getData().isEmpty()) Platform.runLater(()->historicVolumeChart.getData().clear());
        if (!macdChart.getData().isEmpty()) Platform.runLater(()->macdChart.getData().clear());

        new Thread(() -> {
            String stock = historicStockDropdown.getValue();
            if(stock == null){
                Platform.runLater(()->displayHistoricDataButton.setDisable(false));
                Platform.runLater(()->historicOptions.setDisable(false));
                return;
            }

            ArrayList<String> dbSchema = null;
            ArrayList<String> dbData = null;

            try {
                dbSchema = dh.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");
                dbData = dh.executeQuery("SELECT * FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate ASC;");
                String startDate = dh.executeQuery("SELECT MIN(TradeDate) FROM dailystockprices WHERE Symbol='" + stock + "';").get(0);
                String endDate = dh.executeQuery("SELECT MAX(TradeDate) FROM dailystockprices WHERE Symbol='" + stock + "';").get(0);
                Platform.runLater(()->historicDateRange.setText(startDate + " to " + endDate));
            } catch (SQLException e) {e.printStackTrace();}

            int count = 0;

            HashMap<String, ArrayDeque<XYChart.Data<Number, Number>>> values = new HashMap<>();

            for(String column : Objects.requireNonNull(dbSchema)) values.put(column, new ArrayDeque<>());

            for (String record : Objects.requireNonNull(dbData)) {
                int idx, rIdx = 0;

                String[] splitRecord = record.split(",");

                values.get("OpenPrice").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[dbSchema.indexOf("OpenPrice")])));
                values.get("HighPrice").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[dbSchema.indexOf("HighPrice")])));
                values.get("LowPrice").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[dbSchema.indexOf("LowPrice")])));
                values.get("ClosePrice").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[dbSchema.indexOf("ClosePrice")])));
                values.get("TradeVolume").add(new XYChart.Data<>(count, Integer.parseInt(splitRecord[dbSchema.indexOf("TradeVolume")])));
                values.get("SmoothedClosePrice").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[dbSchema.indexOf("SmoothedClosePrice")])));

                Rectangle[] rects = new Rectangle[dbSchema.size()];

                for (int i = 0; i < rects.length; i++) {
                    rects[i] = new Rectangle(0, 0);
                    rects[i].setVisible(false);
                }

                if (showSMA5.isSelected() && !splitRecord[idx = dbSchema.indexOf("SMA5")].equals("null"))
                    values.get("SMA5").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showSMA10.isSelected() && !splitRecord[idx = dbSchema.indexOf("SMA10")].equals("null"))
                    values.get("SMA10").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showSMA20.isSelected() && !splitRecord[idx = dbSchema.indexOf("SMA20")].equals("null"))
                    values.get("SMA20").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showSMA200.isSelected() && !splitRecord[idx = dbSchema.indexOf("SMA200")].equals("null"))
                    values.get("SMA200").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showEMA5.isSelected() && !splitRecord[idx = dbSchema.indexOf("EMA5")].equals("null"))
                    values.get("EMA5").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showEMA10.isSelected() && !splitRecord[idx = dbSchema.indexOf("EMA10")].equals("null"))
                    values.get("EMA10").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showEMA20.isSelected() && !splitRecord[idx = dbSchema.indexOf("EMA20")].equals("null"))
                    values.get("EMA20").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showEMA20.isSelected() && !splitRecord[idx = dbSchema.indexOf("EMA200")].equals("null"))
                    values.get("EMA200").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showMACD.isSelected() && !splitRecord[idx = dbSchema.indexOf("MACD")].equals("null"))
                    values.get("MACD").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showMACDSig.isSelected() && !splitRecord[idx = dbSchema.indexOf("MACDSig")].equals("null"))
                    values.get("MACDSig").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showMACDHist.isSelected() && !splitRecord[idx = dbSchema.indexOf("MACDHist")].equals("null"))
                    values.get("MACDHist").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showRSI.isSelected() && !splitRecord[idx = dbSchema.indexOf("RSI")].equals("null"))
                    values.get("RSI").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showADX10.isSelected() && !splitRecord[idx = dbSchema.indexOf("ADX10")].equals("null"))
                    values.get("ADX10").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showCCI.isSelected() && !splitRecord[idx = dbSchema.indexOf("CCI")].equals("null"))
                    values.get("CCI").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showAD.isSelected() && !splitRecord[idx = dbSchema.indexOf("AD")].equals("null"))
                    values.get("AD").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showOBV.isSelected() && !splitRecord[idx = dbSchema.indexOf("OBV")].equals("null"))
                    values.get("OBV").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showStoOscSlowD.isSelected() && !splitRecord[idx = dbSchema.indexOf("StoOscSlowD")].equals("null"))
                    values.get("StoOscSlowD").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showStoOscSlowK.isSelected() && !splitRecord[idx = dbSchema.indexOf("StoOscSlowK")].equals("null"))
                    values.get("StoOscSlowK").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                if (showWillR.isSelected() && !splitRecord[idx = dbSchema.indexOf("WillR")].equals("null"))
                    values.get("WillR").add(new XYChart.Data<>(count, Double.parseDouble(splitRecord[idx])));

                for(String column:dbSchema)
                    if(!values.get(column).isEmpty())
                        values.get(column).getLast().setNode(rects[rIdx++]);

                count++;
            }

            XYChart.Series<Number, Number> openPrices = new XYChart.Series<>("Open Prices", FXCollections.observableArrayList(values.get("OpenPrice")));
            XYChart.Series<Number, Number> highPrices = new XYChart.Series<>("High Prices", FXCollections.observableArrayList(values.get("HighPrice")));
            XYChart.Series<Number, Number> lowPrices = new XYChart.Series<>("Low Prices", FXCollections.observableArrayList(values.get("LowPrice")));
            XYChart.Series<Number, Number> closePrices = new XYChart.Series<>("Close Prices", FXCollections.observableArrayList(values.get("ClosePrice")));
            AreaChart.Series<Number, Number> volumes = new AreaChart.Series<>("Trade Volume", FXCollections.observableArrayList(values.get("TradeVolume")));
            XYChart.Series<Number, Number> smoothedClosePrices = new XYChart.Series<>("Smoothed Close Prices", FXCollections.observableArrayList(values.get("SmoothedClosePrice")));
            XYChart.Series<Number, Number> sma5 = new XYChart.Series<>("Simple Moving Average (5-Day)", FXCollections.observableArrayList(values.get("SMA5")));
            XYChart.Series<Number, Number> sma10 = new XYChart.Series<>("Simple Moving Average (10-Day)", FXCollections.observableArrayList(values.get("SMA10")));
            XYChart.Series<Number, Number> sma20 = new XYChart.Series<>("Simple Moving Average (20-Day)", FXCollections.observableArrayList(values.get("SMA20")));
            XYChart.Series<Number, Number> sma200 = new XYChart.Series<>("Simple Moving Average (200-Day)", FXCollections.observableArrayList(values.get("SMA200")));
            XYChart.Series<Number, Number> ema5 = new XYChart.Series<>("Exponential Moving Average (5-Day)", FXCollections.observableArrayList(values.get("EMA5")));
            XYChart.Series<Number, Number> ema10 = new XYChart.Series<>("Exponential Moving Average (10-Day)", FXCollections.observableArrayList(values.get("EMA10")));
            XYChart.Series<Number, Number> ema20 = new XYChart.Series<>("Exponential Moving Average (20-Day)", FXCollections.observableArrayList(values.get("EMA20")));
            XYChart.Series<Number, Number> ema200 = new XYChart.Series<>("Exponential Moving Average (200-Day)", FXCollections.observableArrayList(values.get("EMA200")));
            XYChart.Series<Number, Number> macd = new XYChart.Series<>("Moving Average Convergence/Divergence", FXCollections.observableArrayList(values.get("MACD")));
            XYChart.Series<Number, Number> macdSig = new XYChart.Series<>("Moving Average Convergence/Divergence Signal Line", FXCollections.observableArrayList(values.get("MACDSig")));
            XYChart.Series<Number, Number> macdHist = new XYChart.Series<>("Moving Average Convergence/Divergence Histogram", FXCollections.observableArrayList(values.get("MACDHist")));
            XYChart.Series<Number, Number> rsi = new XYChart.Series<>("Relative Strength Indicator", FXCollections.observableArrayList(values.get("RSI")));
            XYChart.Series<Number, Number> adx10 = new XYChart.Series<>("Average Directional Index (10-Day)", FXCollections.observableArrayList(values.get("ADX10")));
            XYChart.Series<Number, Number> cci = new XYChart.Series<>("Commodity Channel Index", FXCollections.observableArrayList(values.get("CCI")));
            XYChart.Series<Number, Number> ad = new XYChart.Series<>("Accumulation Distribution", FXCollections.observableArrayList(values.get("AD")));
            XYChart.Series<Number, Number> obv = new XYChart.Series<>("On-Balance Volume", FXCollections.observableArrayList(values.get("OBV")));
            XYChart.Series<Number, Number> stoOscSlowK = new XYChart.Series<>("Stochastic Oscillator Slow %K", FXCollections.observableArrayList(values.get("StoOscSlowK")));
            XYChart.Series<Number, Number> stoOscSlowD = new XYChart.Series<>("Stochastic Oscillator Slow %D", FXCollections.observableArrayList(values.get("StoOscSlowD")));
            XYChart.Series<Number, Number> willR = new XYChart.Series<>("Williams %R", FXCollections.observableArrayList(values.get("WillR")));

            Platform.runLater(()-> {
                historicPriceChart.getData().addAll(openPrices, highPrices, lowPrices, closePrices, smoothedClosePrices,
                        sma5, sma10, sma20, sma200,
                        ema5, ema10, ema20, ema200);
                for(XYChart.Series<Number, Number> series : historicPriceChart.getData())
                    series.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
            });

            Platform.runLater(()->{
                    historicVolumeChart.getData().add(volumes);
                    volumes.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
            });

            Platform.runLater(()->{
                macdChart.getData().addAll(macd, macdSig, macdHist);
                for(XYChart.Series<Number, Number> series : macdChart.getData())
                    series.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
            });

            Platform.runLater(()->{
                rsiChart.getData().add(rsi);
                rsi.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
            });

            Platform.runLater(()->{
                adxChart.getData().add(adx10);
                adx10.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
            });

            Platform.runLater(()->{
                cciChart.getData().add(cci);
                cci.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
            });

            Platform.runLater(()->{
                adChart.getData().add(ad);
                ad.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
            });

            Platform.runLater(()->{
                obvChart.getData().add(obv);
                obv.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
            });

            Platform.runLater(()->{
                willRChart.getData().add(willR);
                willR.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
            });

            Platform.runLater(()->{
                stoOscChart.getData().addAll(stoOscSlowD,stoOscSlowK);
                for(XYChart.Series<Number, Number> series : stoOscChart.getData())
                    series.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
            });

                Platform.runLater(()->displayHistoricDataButton.setDisable(false));
            Platform.runLater(()->historicOptions.setDisable(false));
        }).start();
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

    @FXML
    private void exportToMLFile() {
        new Thread(() -> {
            Platform.runLater(() -> exportToMLFileButton.setDisable(true));

            try {
                updateCurrentTask("Exporting to ML File...", false, false);
                mainThread.interrupt();
                TrainingFileUtils.exportAllFiles(stocks,stockForecastProgress);
                mainThread.start();
                /*
                TrainingFileUtils.exportSeparateClassificationCSV(stocks,"res/TrainingFiles/SmoothedNASDAQTraining", dayArray, stockForecastProgress);
                StockPredictor.trainLSTM(29);
                updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockForecastProgress);

                TrainingFileUtils.exportClassificationCSV(stocks,"res/TrainingFiles/SmoothedNASDAQTraining.csv", dayArray, stockForecastProgress);

                updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockForecastProgress);

                TrainingFileUtils.exportLibSVMFile("res/TrainingFiles/SmoothedNASDAQTraining.csv", "res/TrainingFiles/SmoothedNASDAQTrainingLibSVM.txt");

                StockPredictor.trainRandomForest("res/TrainingFiles/SmoothedNASDAQTrainingLibSVM.txt", stocks.size());
                */
            }catch (Exception e){e.printStackTrace();}

            updateProgress(0, stockForecastProgress);
            Platform.runLater(()->predictionModelInformationBox.setText(StockPredictor.getModelInformation()));
            Platform.runLater(() -> exportToMLFileButton.setDisable(false));
        }).start();
    }

    static public void updateProgress(double current, double total, ProgressBar pb) {
        Platform.runLater(() -> pb.setVisible(current != 0));
        Platform.runLater(() -> pb.setProgress(current / total));
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
        dh.executeCommand("INSERT INTO settings VALUES ('LCUTOFF', '" + lossCutoff + "') ON DUPLICATE KEY UPDATE Value = VALUES(Value);");
    }

    private void setProfitCutoff(double amount) throws SQLException {
        profitCutoff = (int) (amount * 100) / 100.0;
        Platform.runLater(() -> targetLabel.setText(String.valueOf(profitCutoff)));
        profitTargetPercentageLabel.setText("0.0%");
        dh.executeCommand("INSERT INTO settings VALUES ('PCUTOFF', '" + profitCutoff + "') ON DUPLICATE KEY UPDATE Value = VALUES(Value);");
    }

    private void calculateLossCutoff(double percentage) throws SQLException {
        float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));
        lossCutoff = (balance + getStockWorth()) * (1 - percentage);

        setLossCutoff(lossCutoff);
    }

    private void calculateTargetCutoff(double percentage) throws SQLException {
        float balance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));
        profitCutoff = (balance + getStockWorth()) * (1 + percentage);

        setProfitCutoff(profitCutoff);
    }

    private void initialiseConnections() throws IOException, SQLException {
        updateCurrentTask("Initialising Connections...", false, false);

        //http://code.makery.ch/blog/javafx-dialogs-official/
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
            if (dialogButton == loginButton) {
                return new Pair<>(user.getText(), pass.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialogue.showAndWait();

        result.ifPresent(userPass -> {
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


        avh.init("UFKUIPVK2VFA83U0"); //PBATJ7L9N8SNK835
        bch.init("07467da3de1195c974b66c46b8523e23", sqdh, stockFeedProgress);
        NewsAPIHandler.authenticate("be7afde61f5e10bb20393025c35e50c7", "1ff9ab03aa8e5bd073345d70d588abde");
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
            XYChart.Data<Integer, Double> point = new XYChart.Data<>(i, timeAndPrice.get(time));
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
                } catch (Exception e) { e.printStackTrace(); }
                Platform.runLater(() -> rebalanceButton.setDisable(false));
            }).start();
    }

    public void sellAllStock(boolean automated) throws SQLException, ParseException {
        ArrayList<String> stocks = dh.executeQuery("SELECT Symbol, Held FROM portfolio WHERE Held > 0");

        for (String stock : stocks) {
            String[] splitStock = stock.split(",");
            sellStock(splitStock[0], Integer.parseInt(splitStock[1]), automated);
        }

        dh.executeCommand("DELETE FROM portfolio;");
        updateGUI();
    }

    public void rebalancePortfolio(boolean automated) throws SQLException, ParseException {
        sellAllStock(automated);

        PortfolioManager.OptimisationMethod om = null;

        switch(optimisationMethodDropdown.getSelectionModel().getSelectedItem()){
            case "Simulated Annealing":
                om = PortfolioManager.OptimisationMethod.SIMULATED_ANNEALING;
                break;
            case "Genetic Algorithm":
                om = PortfolioManager.OptimisationMethod.GENETIC_ALGORITHM;
                break;
        }

        Map<String, Double> portfolio = PortfolioManager.optimisePortfolio(om);
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
                command.append(";");
        }

        dh.executeCommand(command.toString());
        updateGUI();
    }

    private void initialiseListeners() {
        lossCutoffField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*\\.?\\d*"))
                lossCutoffField.setText(newValue.replaceAll("[^\\d\\.]", ""));
            setLossCutoffButton.setDisable(newValue.isEmpty());
            if(!newValue.isEmpty()){
                try {
                    double worth = getTotalWorth();
                    setLossCutoffButton.setDisable(Double.parseDouble(lossCutoffField.getText()) >= worth);
                    lossCutoffPercentageLabel.setText(String.valueOf(Double.parseDouble(lossCutoffField.getText()) / worth * 100) + "%");
                } catch (SQLException e) { e.printStackTrace();}
            }
        });

        profitTargetField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*\\.?\\d*"))
                profitTargetField.setText(newValue.replaceAll("[^\\d\\.]", ""));

            setProfitTargetButton.setDisable(newValue.isEmpty());
            if(!newValue.isEmpty()){
                try {
                    double worth = getTotalWorth();
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
                    buyButton.setDisable(disable || !canBuyStock(stock, Integer.parseInt(newValue)));
                    sellButton.setDisable(disable || !canSellStock(stock, Integer.parseInt(newValue)));
                } catch (SQLException e) {e.printStackTrace();}
            });
        }));
    }

    private boolean predictStock(String stock, int numberOfDays) throws SQLException {
        String results = dh.executeQuery("SELECT * FROM dailystockprices WHERE Symbol='" + stock + "' AND SmoothedClosePrice is not null AND SMA10 is not null AND EMA10 is not null AND MACD is not null AND MACDSig is not null AND MACDHist is not null AND RSI is not null AND ADX10 is not null AND CCI is not null AND AD is not null AND OBV is not null AND StoOscSlowK is not null AND StoOscSlowD is not null AND SMA20 is not null AND SMA200 is not null AND EMA5 IS NOT NULL AND EMA20 IS NOT NULL AND EMA200 IS NOT NULL AND SMA5 is not null AND WillR is not null ORDER BY TradeDate DESC LIMIT 1").get(0);
        String[] splitString = results.split(",");
        double newsSentiment = NaturalLanguageProcessor.getTodaysAverageSentiment(stock, 2);
        double features[] = new double[splitString.length + 1];

        features[0] = stocks.indexOf(stock);
        features[1] = numberOfDays;

        for (int i = 2; i < splitString.length - 1; i++)
            features[i] = Double.parseDouble(splitString[i + 1]);

        features[features.length - 1] = newsSentiment;

        return StockPredictor.predictDirection(new DenseVector(features));
    }

    private boolean[] predictStocks() throws SQLException {
        boolean predictions[] = new boolean[stocks.size() * dayArray.length];

        int i = 0;

        updateCurrentTask("Predicting Stock Performance...", false, false);
        updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockForecastProgress);

        for (int numberOfDays : dayArray)
            for (String stock : stocks) {
                predictions[i] = predictStock(stock, numberOfDays);
                if (predictions[i])
                    updateCurrentTask(numberOfDays + " Day Prediction for " + stock + ": RISE/MAINTAIN", false, false);
                else
                    updateCurrentTask(numberOfDays + " Day Prediction for " + stock + ": FALL", false, false);
                i++;
                updateProgress(i, predictions.length  - 1, stockForecastProgress);
            }

        updateProgress(0, stockForecastProgress);
        updateCurrentTask("Predicted Stock Performance!", false, false);

        return predictions;
    }

    private void autoTrade() throws SQLException, ParseException {
        updateCurrentTask("Auto-Trading...", false, true);
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
                    updateCurrentTask("> AUTOMATED TRADER: BUYING " + buyAmount + " " + symbol, false, true);
                    buyStock(symbol, buyAmount, true);
                }
            } else {
                if (held > 0) {
                    updateCurrentTask("> AUTOMATED TRADER: SELLING " + held + " " + symbol, false, true);
                    sellStock(symbol, held, true);
                }
            }
            //TODO: Rebalance portfolio and cutoff reassignment
        }

        updateGUI();
    }

    private void updatePredictions(boolean[] predictionValues){
        //TODO: Update prediction GUI
        for(int i = 0; i < stocks.size(); i++) {
            boolean[] currentPredictions = new boolean[dayArray.length];
            for(int j = 0; j < dayArray.length; j++)
                currentPredictions[j] = predictionValues[i + j * stocks.size()];
            PredictionBox pb = predictions.get(i);
            Platform.runLater(()->pb.updateValues(currentPredictions));
        }
    }

    @FXML
    public void initialize() throws SQLException, IOException {
        mainThread = new Thread(() -> {
            while (!quit) {
                int s = LocalTime.now().getSecond();
                int m = LocalTime.now().getMinute();
                int h = LocalTime.now().getHour();

                //DOWNLOAD INTRADAY DATA FOR VISUALISATION PURPOSES
                if (s == 0 && h < 21 && h >= 14) {
                    try {
                        //updateBatchStockData();
                        if (!priceUpdating)
                            updateIntradayStockData();
                        if (!priceUpdating)
                            updateDailyStockData();
                        if(getTotalWorth() <= lossCutoff || getTotalWorth() >= profitCutoff)
                            sellAllStock(false);

                        checkServices();
                        updateGUI();
                    } catch (Exception e) { e.printStackTrace(); }
                }

                if ( s==0 && h == 0 && m == 0) {
                    try {
                        updateNews();
                        checkServices();
                        updateGUI();
                    } catch (Exception e) {e.printStackTrace();}
                }

                if (m % 30 == 0 && s == 0) {
                    try {
                        StockQuoteDownloader.downloadStockHistory(stocks, true, true, false);
                        SmoothingUtils.smoothStocks(stocks, 0.2);
                        TechnicalAnalyser.calculateTechnicalIndicators(stocks, true);
                        updatePredictions(predictStocks());
                        if (automated)
                            autoTrade();
                        checkServices();
                        updateGUI();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        initialiseConnections();
        initialiseListeners();

        StockQuoteDownloader.initialise(sqdh, avh, stockFeedProgress);
        StockRecordParser.initialise(sqdh);
        NaturalLanguageProcessor.initialise(nlpdh, nlpProgress);
        SmoothingUtils.initialise(tadh, technicalAnalyserProgress);
        TechnicalAnalyser.initialise(tadh, technicalAnalyserProgress);
        NewsAPIHandler.initialise(nddh, newsFeedProgress);
        PortfolioManager.initialise(pmdh); //TODO: Get a progessbar for this
        StockPredictor.initialise(spdh);
        TrainingFileUtils.initialise(dh);
        StockPredictor.loadLatestRandomForest();

        initialiseStocks();
        initialiseClocks();
        initialiseDisplay();
        startClocks();

        Platform.runLater(()->stockDropdown.getItems().addAll(stocks));
        Platform.runLater(()->predictionModelInformationBox.setText(StockPredictor.getModelInformation()));
        Platform.runLater(()->autonomyLevelDropdown.getSelectionModel().selectFirst());
        Platform.runLater(()->optimisationMethodDropdown.getSelectionModel().selectFirst());
        Platform.runLater(()->historicStockDropdown.getItems().addAll(stocks));

        new Thread(() -> {
            try {
                updateSystem();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateStockValues() throws SQLException {
        float worth = 0;
        ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol FROM portfolio WHERE Held > 0;");

        for (String stock : heldStocks) {
            int volume = getHeldStocks(stock);
            float currPrice = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
            worth += volume * currPrice;
        }

        float finalWorth = worth;
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

    private double getBalance() throws SQLException {
        return Math.floor(100 * Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0))) / 100;
    }

    private void updateBankBalance(){
            Platform.runLater(() -> {
                try { currentBalanceLabel.setText(String.valueOf(getBalance())); }
                catch (SQLException e) { e.printStackTrace(); }
            });
    }

    public void printToInfoBox(String string) {
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

        for (String curr : stocks)
            records.add(new LiveStockRecord(curr, stockNames.get(curr), dh));

        for (LiveStockRecord rec : records)
            Platform.runLater(() -> stockList.getChildren().add(rec.getNode()));
    }

    private void updateSystem() throws SQLException, InterruptedException, ParseException {
        Thread stockThread = new Thread(() -> {
            try {
                processYahooHistories();
                dh.executeCommand("DELETE FROM intradaystockprices WHERE Temporary = 1;");

                StockQuoteDownloader.downloadStockHistory(stocks, true, false, false);

                bch.downloadIntradayHistory(stocks);
/*
                switch (useAPI) {
                    case BarChart:
                        bch.downloadIntradayHistory(stocks);
                        break;
                    case AlphaVantage:
                        StockQuoteDownloader.downloadStockHistory(stocks, false, true, false);
                        break;
                }
                */
            } catch (Exception e) { e.printStackTrace(); }

        });

        Thread taThread = new Thread(() -> {
            try {
                SmoothingUtils.smoothStocks(stocks, 0.2);
                TechnicalAnalyser.calculatePercentChanges(stocks);
                TechnicalAnalyser.calculateTechnicalIndicators(stocks, true);
            } catch (Exception e) { e.printStackTrace(); }
        });

        Thread newsThread = new Thread(() -> {
            try {
                nddh.setWriteToFile(true);
                updateNews();
                nddh.setWriteToFile(false);
                nddh.sendSQLFileToDatabase(false);

                NewsAPIHandler.downloadArticles(); //Has to be done individually to check for duplicate values

                NaturalLanguageProcessor.enumerateSentencesFromArticles();
                NaturalLanguageProcessor.determineUselessSentences();

            } catch (Exception e) { e.printStackTrace(); }
        });

        Thread nlpThread = new Thread(() -> {
            try {
                NaturalLanguageProcessor.enumerateNGramsFromArticles(2);
                NaturalLanguageProcessor.processArticlesForSentiment(2);
            } catch (SQLException e) { e.printStackTrace(); }
        });

        updateGUI();
        //////////////////////////////////////////////////////////

        setProfitCutoff(Double.parseDouble(dh.executeQuery("SELECT Value FROM settings WHERE ID = 'PCUTOFF';").get(0)));
        setLossCutoff(Double.parseDouble(dh.executeQuery("SELECT Value FROM settings WHERE ID = 'LCUTOFF';").get(0)));
        checkServices();

        newsThread.start();
        stockThread.start();

        stockThread.join();
        taThread.start();
        newsThread.join();

        nlpThread.start();
        nlpThread.join();
        taThread.join();

        new Thread(()-> {
            for (LiveStockRecord curr : records) {
                curr.updateRecord(sqdh);
                curr.updateChart(sqdh, false);
            }
        }).start();

        new Thread(()->{
            boolean[] predictionArray = new boolean[0];
            try { predictionArray = predictStocks(); } catch (SQLException e) { e.printStackTrace(); }

            for(int i = 0; i < stocks.size(); i++) {
                boolean[] currentPredictions = new boolean[dayArray.length];
                for(int j = 0; j < dayArray.length; j++)
                    currentPredictions[j] = predictionArray[i + j * stocks.size()];

                predictions.add(new PredictionBox(stocks.get(i), dayArray, currentPredictions));
            }

            for(PredictionBox pb : predictions)
                Platform.runLater(()->stockPredictionsBox.getChildren().add(pb.getNode()));
        }).start();

        Platform.runLater(() -> exportToMLFileButton.setDisable(false));
        Platform.runLater(() -> controlPanel.setDisable(false));

        mainThread.start();
    }

    private void initialiseClocks() {
        updateCurrentTask("Initialising Clocks", false, false);
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

    private void processYahooHistories() throws SQLException, IOException {

        double curr = 0;
        final double t = stocks.size();

        updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockFeedProgress);

        for (String symbol : stocks) {
            ArrayList<String> results = sqdh.executeQuery("SELECT COUNT(*) FROM dailystockprices WHERE Symbol='" + symbol + "';");
            if (results.isEmpty() || Integer.parseInt(results.get(0)) == 0) {
                updateCurrentTask("Importing Yahoo! records for: " + symbol, false, false);

                File file = new File("res/historicstocks/" + symbol + ".csv");

                if (file.exists()) {
                    StockRecordParser.importDailyYahooMarketData(file, symbol);
                    updateCurrentTask("Successfully committed complete Yahoo! records of " + symbol + " to the database!", false, false);
                }else
                    updateCurrentTask("No Yahoo history available for " + symbol, true, true);
            }

            updateProgress(++curr, t, stockFeedProgress);
        }
    }

    @FXML
    public void initialiseStocks() throws SQLException {
        updateCurrentTask("Initialising stocks", false, false);
        stocks = dh.executeQuery("SELECT Symbol FROM indices");
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

    private void updateGUI() throws SQLException, ParseException {
            updateStocksOwned();
            updateComponentChart();
            updateAllocationChart();
            updateProfitLoss();
            updateProfitLossChart();
            updateBankBalance();
            updateStockValues();
            updateTotalWorth();
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

    private float getStockWorth() throws SQLException {
        float potentialTotal = 0;
        ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol, Held FROM portfolio WHERE Held > 0");

        for (String stock : heldStocks) {
            String[] splitStock = stock.split(",");
            potentialTotal += Float.parseFloat(splitStock[1]) * Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + splitStock[0] + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
        }

        return potentialTotal;
    }

    private void updateProfitLoss() throws SQLException {
        float investmentCost = Float.parseFloat(dh.executeQuery("SELECT COALESCE(SUM(Investment),0) FROM portfolio").get(0));
        float potentialTotal = getStockWorth();
        float total = potentialTotal - investmentCost;

        Platform.runLater(() -> profitLossLabel.setText(String.valueOf(Math.round(total * 100.0) / 100.0)));

        if (total > 0)
            Platform.runLater(() -> profitLossLabel.setTextFill(Color.GREEN));
        else if (total == 0)
            Platform.runLater(() -> profitLossLabel.setTextFill(Color.BLACK));
        else
            Platform.runLater(() -> profitLossLabel.setTextFill(Color.RED));

    }

    private double getTotalWorth() throws SQLException {
        float bankBalance = Float.parseFloat(dh.executeQuery("SELECT SUM(Amount) FROM banktransactions").get(0));
        float stockWorth = 0;

        ArrayList<String> heldStocks = dh.executeQuery("SELECT Symbol FROM portfolio WHERE Held > 0;");

        for (String stock : heldStocks) {
            int volume = getHeldStocks(stock);
            float currPrice = Float.parseFloat(dh.executeQuery("SELECT ClosePrice FROM dailystockprices WHERE Symbol = '" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));
            stockWorth += volume * currPrice;
        }

        return stockWorth + bankBalance;
    }

    private void updateTotalWorth() throws SQLException {
        double value = getTotalWorth();
        Platform.runLater(() -> totalBalanceLabel.setText(String.valueOf(Math.round((value) * 100.0) / 100.0)));
    }

    public boolean canBuyStock(String stock, int amount) throws SQLException {
        if (amount == 0 || stock.isEmpty())
            return false;

        double availableFunds = Double.parseDouble(dh.executeQuery("SELECT COALESCE(SUM(Amount),0) FROM banktransactions;").get(0)),
                stockCost = Double.parseDouble(dh.executeQuery("SELECT COALESCE(ClosePrice,0) FROM dailystockprices WHERE Symbol='" + stock + "' ORDER BY TradeDate DESC LIMIT 1").get(0));

        return (stockCost * amount) <= availableFunds;
    }

    private int getHeldStocks(String stock) throws SQLException {
            return Integer.parseInt(dh.executeQuery("SELECT COALESCE(Held,0) FROM Portfolio WHERE Symbol='" + stock + "';").get(0));
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
    private void sellStock() throws SQLException, ParseException {
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

    private void updateNews() throws SQLException, InterruptedException, IOException {
        if (newsUpdating) return;
        newsUpdating = true;

        NewsAPIHandler.getHistoricNews(stocks);

        Platform.runLater(() -> newsBox.getChildren().clear());

        ArrayList<String> results = nddh.executeQuery("SELECT DISTINCT Symbol, Headline, Published FROM newsarticles WHERE DATE(Published) = CURDATE() ORDER BY Published DESC");

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
                    updateCurrentTask("Downloaded " + curr.getSymbol() + " current daily close price: " + temp.get(1), false, false);
                }
                curr.setUpdating(false);
            });

            threads.add(downloadThread);
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
                    updateCurrentTask("Downloaded " + curr.getSymbol() + " 1 minute update: " + temp.get(1), false, false);
                }
                curr.setUpdating(false);
            });

            threads.add(downloadThread);
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

    private void updateBatchStockData() throws SQLException, ParseException, IOException {
        ArrayList<String> temp = null;
            switch (useAPI) {
                case AlphaVantage:
                    temp = StockQuoteDownloader.downloadBatchStockData(stocks);
                    break;
                case BarChart:
                    temp = bch.downloadQuotes(stocks);
                    break;
            }

        int i = 0;

        for (LiveStockRecord curr : records) {
            String record = Objects.requireNonNull(temp).get(i++);
            String[] splitString = record.split(",");
            record = record.replace(splitString[0] + ",", "");

            ArrayList<String> tempRec = new ArrayList<>();
            tempRec.add(record);

            if (splitString[0].equals(curr.symbol)) {
                StockRecordParser.importCurrentQuote(tempRec.get(0), curr.getSymbol());
                updateCurrentTask("Downloaded " + curr.getSymbol() + " current price:" + record, false, false);
                curr.updateRecord(dh);
                curr.updateChart(dh, false);
            } else
                updateCurrentTask("Batch download mismatch", true, true);
        }

        updateGUI();
    }

    private enum STOCK_API {AlphaVantage, BarChart}
}