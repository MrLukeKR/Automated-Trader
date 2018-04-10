package Prediction;

import Default.Controller;
import Default.DatabaseHandler;
import Default.Main;
import Processing.NaturalLanguageProcessor;
import Utility.SmoothingUtils;
import javafx.scene.control.ProgressBar;
import org.apache.log4j.Level;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkStatusTracker;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.mllib.linalg.DenseVector;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.regression.LabeledPoint;
import org.apache.spark.mllib.tree.RandomForest;
import org.apache.spark.mllib.tree.model.RandomForestModel;
import org.apache.spark.mllib.util.MLUtils;
import org.apache.spark.storage.StorageLevel;
import scala.Tuple2;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.3
 */

public class StockPredictor {
    private static final SparkConf sparkConf = new SparkConf().setMaster("local[*]").setAppName("StockMarketPredictor").set("spark.executor.cores", "6").set("spark.driver.memory", "2g").set("spark.executor.memory", "2g").setSparkHome(System.getProperty("user.dir") + "/res/sparkhome");
    private static final JavaSparkContext jsc = new JavaSparkContext(sparkConf);
    private static final SparkStatusTracker tracker = new SparkStatusTracker(jsc.sc());
    private static RandomForestModel model;
    private static ProgressBar pb;
    private static DatabaseHandler dh;
    private static HashMap<String, RandomForestModel> singleModels = new HashMap<>();

    /**
     * Initialises the Stock Predictor with a Database Handler and Progress Bar
     *
     * @param spdh Stock Predictor Database Handler
     * @param sppb Stock Predictor Progress Bar
     */
    static public void initialise(DatabaseHandler spdh, ProgressBar sppb) {
        System.out.println(jsc.getConf().toDebugString());
        jsc.setLogLevel(Level.FATAL.toString());
        dh = spdh;
        pb=sppb;
    }

    /**
     * Returns details of the currently loaded model(s)
     *
     * @param stocks Stocks to loads the model(s) of
     * @return Textual information about the currently loaded model(s)
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public String getModelInformation(ArrayList<String> stocks) throws SQLException {
        if (!isModelLoaded()) return "No model loaded";
        if (dh.executeQuery("SELECT value FROM settings WHERE ID='PREDICTION_MODE';").get(0).equals("MULTI"))
            try {
                ArrayList<String> results = dh.executeQuery("SELECT Description, Accuracy FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope='MultiStock' ORDER BY ModelNumber DESC LIMIT 1;");
                if (!results.isEmpty()) {
                    String[] splitString = results.get(0).split(",");
                    StringBuilder finalString = new StringBuilder();
                    for (int i = 0; i < splitString.length - 1; i++)
                        finalString.append(splitString[i].trim()).append("\r\n");

                    double accuracy = Math.floor(Double.valueOf(splitString[splitString.length - 1]) * 10000) / 100;

                    finalString.append("Accuracy: ").append(accuracy).append("%");

                    return finalString.toString();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        else {
            StringBuilder finalString = new StringBuilder();
            finalString.append("Single stock model collection, containing ").append(stocks.size()).append(" models.\r\n");
            double totalAccuracy = 0;
            try {
                for (String stock : stocks) {
                    ArrayList<String> results = dh.executeQuery("SELECT Description, Accuracy FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope='" + stock + "' ORDER BY ModelNumber DESC LIMIT 1;");
                    if (!results.isEmpty()) {
                        String[] splitString = results.get(0).split(",");
                        if (stocks.indexOf(stock) == 0)
                            finalString.append(splitString[0]).append("\r\n");
                        double accuracy = Math.floor(Double.valueOf(splitString[splitString.length - 1]));
                        totalAccuracy += accuracy;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            finalString.append("Average Accuracy: ").append((totalAccuracy / stocks.size())).append("%");

            return finalString.toString();
        }

        return "No model loaded";
    }

    /**
     * Predict RISE/MAINTAIN or FALL, given a feature vector
     *
     * @param data Feature vector containing information such as prediction period, stock price data and sentiment information
     * @return True if the stock is predicted to rise or stay the same, False if the stock is predicted to fall
     */
    static boolean predictDirection(Vector data) {
        return model.predict(data) == 1;
    }

    /**
     * Predict RISE/MAINTAIN or FALL, given a feature vector and a stock
     *
     * @param data  Feature vector containing information such as prediction period, stock price data and sentiment information
     * @param stock Stock to predict the price direction of
     * @return True if the stock is predicted to rise or stay the same, False if the stock is predicted to fall
     */
    static boolean predictDirection(Vector data, String stock) {
        double value;
        if (stock.contains("_"))
            value = singleModels.get(stock.split("_")[0]).predict(data);
        else
            value = singleModels.get(stock).predict(data);

        return value == 1;
    }

    /**
     * Loads the latest Random Forest model for a given list of stocks
     *
     * @param stocks Stocks to load prediction models of
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void loadLatestRandomForest(ArrayList<String> stocks) throws SQLException {
        for (String stock : stocks) loadLatestRandomForest(stock);
    }

    /**
     * Loads the latest Random Forest model for a given stock
     *
     * @param stock Stock to load prediction model of
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void loadLatestRandomForest(String stock) throws SQLException {
        ArrayList<String> results = dh.executeQuery("SELECT Filepath FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope = '" + stock + "' ORDER BY ModelNumber DESC LIMIT 1;");
        if(results.isEmpty()) return;
        loadRandomForest(results.get(0), stock);
    }

    /**
     * Loads the latest MultiStock Random Forest Model
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void loadLatestRandomForest() throws SQLException {
        ArrayList<String> results = dh.executeQuery("SELECT Filepath FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope = 'MultiStock' ORDER BY ModelNumber DESC LIMIT 1;");
        if(results.isEmpty()) return;
        loadRandomForest(results.get(0));
    }

    /**
     * Loads a Random Forest from a given filepath, for a given stock
     * @param modelFile Filepath location of the model
     * @param stock Stock to associate with this model upon loading
     */
    private static void loadRandomForest(String modelFile, String stock) {
        System.out.println("Loading Machine Learning Model '" + modelFile + "'...");
        try {
            if (!singleModels.containsKey(stock.split("_")[0]))
                singleModels.put(stock.split("_")[0], RandomForestModel.load(jsc.sc(), modelFile));
        } catch (Exception e) {
            Main.getController().updateCurrentTask("Couldn't load ML Model: " + e.getMessage(), true, true);
        }
        System.out.println("Loaded Machine Learning Model: " + singleModels.get(stock.split("_")[0]).toString());
    }

    /**
     * Loads a MultiStock Random Forest from a given filepath
     * @param modelFile Filepath location of the model
     */
    private static void loadRandomForest(String modelFile) {
        System.out.println("Loading Machine Learning Model '" + modelFile + "'...");
        try {
            model = RandomForestModel.load(jsc.sc(), modelFile);
        } catch (Exception e) {
            Main.getController().updateCurrentTask("Couldn't load ML Model: " + e.getMessage(), true, true);
        }
        System.out.println("Loaded Machine Learning Model: " + model.toString());
    }

    /**
     * Asserts whether or not a model is currently loaded for each stock
     * @return True if a prediction model is available for each stock (or MultiStock), False is a model is not loaded
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public boolean isModelLoaded() throws SQLException {
        if (dh.executeQuery("SELECT value FROM settings WHERE ID = 'PREDICTION_MODE'").get(0).equals("MULTI"))
            return model != null;
        else {
            for (String model : singleModels.keySet())
                if (singleModels.get(model) == null)
                    return false;
            return true;
        }
    }

    /**
     * Trains a new Single-Stock Random Forest model, given a training file and stock
     * @param libSVMFilePath Filepath location of training/testing data
     * @param stock Stock to associate with the new model
     * @param isSimulation True if the model should only be used for simulations, False if the model can be used for real-time stock information
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void trainRandomForest(String libSVMFilePath, String stock, boolean isSimulation) throws SQLException {
        System.out.println("Training Single-Stock Random Forest for " + stock + "...");
        JavaRDD<LabeledPoint> data = MLUtils.loadLibSVMFile(jsc.sc(), libSVMFilePath).toJavaRDD().persist(StorageLevel.MEMORY_AND_DISK());

        final JavaRDD<LabeledPoint>[] trainingTestSplits = data.randomSplit(new double[]{0.7, 0.3});

        Integer classes = 2;
        HashMap<Integer, Integer> categoryInfo = new HashMap<>();

        Integer trees = 100;
        String featureSubsetStrategy = "auto";
        String impurity = "gini";
        Integer maxDepth = 15;
        Integer seed = 12345;

        RandomForestModel tempModel;

        tempModel = RandomForest.trainClassifier(trainingTestSplits[0], classes, categoryInfo, trees, featureSubsetStrategy, impurity, maxDepth, 32, seed);
        RandomForestModel finalTempModel = tempModel;
        JavaPairRDD<Double, Double> predictionAndLabel = trainingTestSplits[1].mapToPair((PairFunction<LabeledPoint, Double, Double>) point -> new Tuple2<>(finalTempModel.predict(point.features()), point.label()));

        Double testErr = 1.0 * predictionAndLabel.filter((Function<Tuple2<Double, Double>, Boolean>) predictionLabel -> !predictionLabel._1().equals(predictionLabel._2())).count() / trainingTestSplits[1].count();
        System.out.println(tempModel.toDebugString() + " Accuracy: " + (1 - testErr) * 100 + "%");
        tracker.getExecutorInfos();
        tempModel = RandomForest.trainClassifier(data,classes,categoryInfo,trees,featureSubsetStrategy,impurity,maxDepth,32,seed);

        if (isSimulation) stock += "_SIMULATION";

        int modelNo = Integer.parseInt((dh.executeQuery("SELECT COALESCE(MAX(ModelNumber),0) FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope='" + stock + "'")).get(0)) + 1;
        dh.executeCommand("INSERT INTO predictors(Model, Type, ModelNumber, Accuracy, Description, Filepath, Scope) VALUES ('Random Forest', 'CLASSIFICATION', " + modelNo + ", " + (1 - testErr) * 100 + ", 'Exponentially Smoothed Prices (Alpha 0.1), Single Stock" + tempModel.toString() + ", Depth " + maxDepth + "', 'res/model/" + stock + "/RF" + modelNo + ".model', '" + stock + "')");
        File stockDir = new File(System.getProperty("user.dir") + "/res/model/" + stock);
        if (!stockDir.exists())
            if (!stockDir.mkdirs())
                Main.getController().updateCurrentTask("Could not create director/file: " + stockDir, true,true);

        tempModel.save(jsc.sc(), System.getProperty("user.dir") + "/res/model/" + stock + "/RF" + modelNo + ".model");
        singleModels.put(stock,tempModel);
        System.out.println("Saved Model Successfully!");
    }

    /**
     * Trains a new MultiStock Random Forest model, given a training file
     * @param libSVMFilePath Filepath location of training/testing data
     * @param noOfStocks Number of stocks that will be covered by this MultiStock model
     * @param isSimulation True if the model should only be used for simulations, False if the model can be used for real-time stock information
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     * @throws InterruptedException Throws InterruptedException if the sleep function is interrupted by another process
     */
    static public void trainRandomForest(String libSVMFilePath, int noOfStocks, boolean isSimulation) throws SQLException, InterruptedException {
        System.out.println("Loading training data for Multi-Stock Random Forest...");
        model = null;
        JavaRDD<LabeledPoint> data = MLUtils.loadLibSVMFile(jsc.sc(), libSVMFilePath).toJavaRDD();

        final JavaRDD<LabeledPoint>[] trainingTestSplits = data.randomSplit(new double[]{0.7, 0.3});

        Integer classes = 2;
        HashMap<Integer, Integer> categoryInfo = new HashMap<>();

        categoryInfo.put(0, noOfStocks);

        Integer trees = 500;
        String featureSubsetStrategy = "auto";
        String impurity = "gini";
        Integer maxDepth = 10;
        Integer maxBins = noOfStocks;
        Integer seed = 12345;

        System.out.println("Training Multi-Stock Random Forest...");
        Thread trainThread = new Thread(() ->
                model = RandomForest.trainClassifier(trainingTestSplits[0], classes, categoryInfo, trees, featureSubsetStrategy, impurity, maxDepth, maxBins, seed)
        );

        trainThread.start();

        while (trainThread.isAlive()) {
            int total = 0;
            int completed = 0;

            TimeUnit.SECONDS.sleep(1);
            int[] jIDs = tracker.getActiveJobIds();

            for (Integer job : jIDs)
                for (Integer stage : tracker.getJobInfo(job).get().stageIds()) {
                    total += tracker.getStageInfo(stage).get().numTasks();
                    completed += tracker.getStageInfo(stage).get().numCompletedTasks();
                }

            double progress = ((double) completed / (double) total * 100.0);

            Controller.updateProgress(completed, total, pb);
            System.out.println("Progress: " + progress + "%");
        }

        JavaPairRDD<Double, Double> predictionAndLabel = trainingTestSplits[1].mapToPair((PairFunction<LabeledPoint, Double, Double>) point -> new Tuple2<>(model.predict(point.features()), point.label()));

        Double testErr = 1.0 * predictionAndLabel.filter((Function<Tuple2<Double, Double>, Boolean>) predictionLabel -> !predictionLabel._1().equals(predictionLabel._2())).count() / trainingTestSplits[1].count();
        System.out.println(model.toDebugString() + " Accuracy: " + (1 - testErr) * 100 + "%");

        model = RandomForest.trainClassifier(data, classes, categoryInfo, trees, featureSubsetStrategy, impurity, maxDepth, maxBins, seed);

        String scope = "MultiStock";
        if (isSimulation) scope += "_SIMULATION";

        int modelNo = Integer.parseInt((dh.executeQuery("SELECT COALESCE(MAX(ModelNumber),0) FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION'")).get(0)) + 1;
        dh.executeCommand("INSERT INTO predictors(Model, Type, ModelNumber, Accuracy, Description, Filepath, Scope) VALUES ('Random Forest', 'CLASSIFICATION', " + modelNo + ", " + (1 - testErr) * 100 + ", 'Exponentially Smoothed Prices (Alpha " + SmoothingUtils.getAlpha() + "), Categorical Features, " + model.toString() + ", Depth " + maxDepth + "', 'res/model/RF" + modelNo + ".model', '" + scope + "')");
        model.save(jsc.sc(), System.getProperty("user.dir") + "/res/model/RF" + modelNo + ".model");
        System.out.println("Saved Model Successfully!");
    }

    /**
     * Produces a collection of predictions for multiple days and multple stocks
     * @param stocks Stocks to predict the direction of
     * @param dayArray List of days to predict
     * @param stockForecastProgress ProgressBar used to update the current prediction process
     * @return Collection of multi-stock, multi-day price direction predictions
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public HashMap<String, HashMap<Integer, Boolean>> predictStocks(ArrayList<String> stocks, int[] dayArray, ProgressBar stockForecastProgress) throws SQLException {
        HashMap<String, HashMap<Integer, Boolean>> predictions = new HashMap<>();

        for (String stock : stocks)
            predictions.put(stock, new HashMap<>());
        int i = 0;

        Main.getController().updateCurrentTask("Predicting Stock Performance...", false, false);
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockForecastProgress);

        for (int numberOfDays : dayArray)
            for (String stock : stocks) {
                predictions.get(stock).put(numberOfDays, predictStock(stocks, stock, numberOfDays));
                if (predictions.get(stock).get(numberOfDays))
                    Main.getController().updateCurrentTask(numberOfDays + " Day Prediction for " + stock + ": RISE/MAINTAIN", false, false);
                else
                    Main.getController().updateCurrentTask(numberOfDays + " Day Prediction for " + stock + ": FALL", false, false);
                i++;
                Controller.updateProgress(i, dayArray.length * stocks.size(), stockForecastProgress);
            }

        Controller.updateProgress(0, stockForecastProgress);
        Main.getController().updateCurrentTask("Predicted Stock Performance!", false, false);

        return predictions;
    }

    /**
     * Predicts the direction of a single stock, given a number of days
     * @param stocks List of stocks used in the application (to determine the index required for multi-stock models)
     * @param stock Stock to predict
     * @param numberOfDays Number of days to predict
     * @return True if the stock is expected to rise or stay the same after the number of days given, False if it is expected to fall
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public boolean predictStock(ArrayList<String> stocks, String stock, int numberOfDays) throws SQLException {
        String results = dh.executeQuery("SELECT * FROM dailystockprices WHERE Symbol='" + stock + "' AND PercentChange IS NOT NULL AND SmoothedClosePrice IS NOT NULL AND SMA10 is not null AND EMA10 is not null AND MACD is not null AND MACDSig is not null AND MACDHist is not null AND RSI is not null AND ADX10 is not null AND CCI is not null AND AD is not null AND OBV is not null AND StoOscSlowK is not null AND StoOscSlowD is not null AND SMA20 is not null AND SMA200 is not null AND EMA5 IS NOT NULL AND EMA20 IS NOT NULL AND EMA200 IS NOT NULL AND SMA5 is not null AND WillR is not null ORDER BY TradeDate DESC LIMIT 1").get(0);
        String[] splitString = results.split(",");
        double newsSentiment = NaturalLanguageProcessor.getTodaysAverageSentiment(stock, 2);

        if (dh.executeQuery("SELECT value FROM settings WHERE ID = 'PREDICTION_MODE'").get(0).equals("MULTI")) {
            double features[] = new double[splitString.length + 1];

            features[0] = stocks.indexOf(stock);
            features[1] = numberOfDays;

            for (int i = 2; i < splitString.length; i++)
                features[i] = Double.parseDouble(splitString[i]);

            features[features.length - 1] = newsSentiment;

            return StockPredictor.predictDirection(new DenseVector(features));
        } else {
            double features[] = new double[splitString.length];

            features[0] = numberOfDays;

            for (int i = 2; i < splitString.length; i++)
                features[i - 1] = Double.parseDouble(splitString[i]);

            features[features.length - 1] = newsSentiment;

            return StockPredictor.predictDirection(new DenseVector(features), stock);
        }
    }
}