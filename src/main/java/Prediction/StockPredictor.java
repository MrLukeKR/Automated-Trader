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

public class StockPredictor {
    private static final SparkConf sparkConf = new SparkConf().setMaster("local[*]").setAppName("StockMarketPredictor").set("spark.executor.cores", "6").set("spark.driver.memory", "2g").set("spark.executor.memory", "2g").setSparkHome(System.getProperty("user.dir") + "/res/sparkhome");
    private static final JavaSparkContext jsc = new JavaSparkContext(sparkConf);
    private static final SparkStatusTracker tracker = new SparkStatusTracker(jsc.sc());
    private static RandomForestModel model;
    private static ProgressBar pb;
    private static DatabaseHandler dh;
    private static HashMap<String, RandomForestModel> singleModels = new HashMap<>();

    static public void initialise(DatabaseHandler spdh, ProgressBar sppb) {
        System.out.println(jsc.getConf().toDebugString());
        jsc.setLogLevel(Level.FATAL.toString());
        dh = spdh;
        pb=sppb;
    }

    static public String getModelInformation(){
        try {
            ArrayList<String> results = dh.executeQuery("SELECT Description, Accuracy FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope='MultiStock' ORDER BY ModelNumber DESC LIMIT 1;");
            if(!results.isEmpty()) {
                String[] splitString = results.get(0).split(",");
                StringBuilder finalString = new StringBuilder();
                for (int i = 0; i < splitString.length - 1; i++)
                    finalString.append(splitString[i].trim()).append("\r\n");

                double accuracy = Math.floor(Double.valueOf(splitString[splitString.length - 1]) * 10000) / 100;

                finalString.append("Accuracy: ").append(accuracy).append("%");

                return finalString.toString();
            }
        } catch (SQLException e) { e.printStackTrace(); }

        return "No model loaded";
    }

    static public boolean predictDirection(Vector data) {
        double value = model.predict(data);

        return value == 1;
    }

    static public boolean predictDirection(Vector data, String stock) {
        double value = singleModels.get(stock.split("_")[0]).predict(data);

        return value == 1;
    }

    static public void loadLatestRandomForest(String stock) throws SQLException {
        ArrayList<String> results = dh.executeQuery("SELECT Filepath FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope = '" + stock + "' ORDER BY ModelNumber DESC LIMIT 1;");
        if(results.isEmpty()) return;
        loadRandomForest(results.get(0), stock);
    }

    static public void loadLatestRandomForest() throws SQLException {
        ArrayList<String> results = dh.executeQuery("SELECT Filepath FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope = 'MultiStock' ORDER BY ModelNumber DESC LIMIT 1;");
        if(results.isEmpty()) return;
        loadRandomForest(results.get(0));
    }

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

    private static void loadRandomForest(String modelFile) {
        System.out.println("Loading Machine Learning Model '" + modelFile + "'...");
        try {
            model = RandomForestModel.load(jsc.sc(), modelFile);
        } catch (Exception e) {
            Main.getController().updateCurrentTask("Couldn't load ML Model: " + e.getMessage(), true, true);
        }
        System.out.println("Loaded Machine Learning Model: " + model.toString());
    }

    static public boolean isModelLoaded() {
        return model != null;
    }

    static public void trainRandomForest(String libSVMFilePath, String stock, boolean isSimulation) throws SQLException {
        System.out.println("Training Single-Stock Random Forest for " + stock + "...");
        JavaRDD<LabeledPoint> data = MLUtils.loadLibSVMFile(jsc.sc(), libSVMFilePath).toJavaRDD().persist(StorageLevel.MEMORY_AND_DISK());

        JavaRDD<LabeledPoint>[] trainingTestSplits = data.randomSplit(new double[]{0.7, 0.3});
        JavaRDD<LabeledPoint> trainingData = trainingTestSplits[0];
        JavaRDD<LabeledPoint> testData = trainingTestSplits[1];

        Integer classes = 2;
        HashMap<Integer, Integer> categoryInfo = new HashMap<>();

        Integer trees = 100;
        String featureSubsetStrategy = "auto";
        String impurity = "gini";
        Integer maxDepth = 15;
        Integer seed = 12345;

        RandomForestModel tempModel;

        tempModel = RandomForest.trainClassifier(trainingData, classes, categoryInfo, trees, featureSubsetStrategy, impurity, maxDepth, 32, seed);
        trainingData = null;
        RandomForestModel finalTempModel = tempModel;
        JavaPairRDD<Double, Double> predictionAndLabel = testData.mapToPair((PairFunction<LabeledPoint, Double, Double>) point -> new Tuple2<>(finalTempModel.predict(point.features()), point.label()));

        Double testErr = 1.0 * predictionAndLabel.filter((Function<Tuple2<Double, Double>, Boolean>) predictionLabel -> !predictionLabel._1().equals(predictionLabel._2())).count() / testData.count();
        System.out.println(tempModel.toDebugString() + " Accuracy: " + (1 - testErr) * 100 + "%");
        testData = null;
        tracker.getExecutorInfos();
        tempModel = RandomForest.trainClassifier(data,classes,categoryInfo,trees,featureSubsetStrategy,impurity,maxDepth,32,seed);

        if (isSimulation) stock += "_SIMULATION";

        int modelNo = Integer.parseInt((dh.executeQuery("SELECT COALESCE(MAX(ModelNumber),0) FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope='" + stock + "'")).get(0)) + 1;
        dh.executeCommand("INSERT INTO predictors(Model, Type, ModelNumber, Accuracy, Description, Filepath, Scope) VALUES ('Random Forest', 'CLASSIFICATION', " + modelNo + ", " + (1 - testErr) * 100 + ", 'Exponentially Smoothed Prices (Alpha 0.1), Single Stock" + tempModel.toString() + ", Depth " + maxDepth + "', 'res/model/" + stock + "/RF" + modelNo + ".model', '" + stock + "')");
        File stockDir = new File(System.getProperty("user.dir") + "/res/model/" + stock);
        if(! stockDir.exists())
            stockDir.mkdirs();

        tempModel.save(jsc.sc(), System.getProperty("user.dir") + "/res/model/" + stock + "/RF" + modelNo + ".model");
        singleModels.put(stock,tempModel);
        System.out.println("Saved Model Successfully!");
    }

    static public void trainRandomForest(String libSVMFilePath, int noOfStocks, boolean isSimulation) throws SQLException, InterruptedException {
        System.out.println("Loading training data for Multi-Stock Random Forest...");
        model = null;
        JavaRDD<LabeledPoint> data = MLUtils.loadLibSVMFile(jsc.sc(), libSVMFilePath).toJavaRDD();

        JavaRDD<LabeledPoint>[] trainingTestSplits = data.randomSplit(new double[]{0.7, 0.3});
        JavaRDD<LabeledPoint> trainingData = trainingTestSplits[0];
        JavaRDD<LabeledPoint> testData = trainingTestSplits[1];

        Integer classes = 2;
        HashMap<Integer, Integer> categoryInfo = new HashMap<>();

        categoryInfo.put(0, noOfStocks);

        Integer trees = 500;
        String featureSubsetStrategy = "auto";
        String impurity = "gini";
        Integer maxDepth = 10;
        Integer maxBins = noOfStocks;
        Integer seed = 12345;

        JavaRDD<LabeledPoint> finalTrainingData = trainingData;
        trainingData = null;
        System.out.println("Training Multi-Stock Random Forest...");
        Thread trainThread = new Thread(() ->
                model = RandomForest.trainClassifier(finalTrainingData, classes, categoryInfo, trees, featureSubsetStrategy, impurity, maxDepth, maxBins, seed)
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

        JavaPairRDD<Double, Double> predictionAndLabel = testData.mapToPair((PairFunction<LabeledPoint, Double, Double>) point -> new Tuple2<>(model.predict(point.features()), point.label()));

        Double testErr = 1.0 * predictionAndLabel.filter((Function<Tuple2<Double, Double>, Boolean>) predictionLabel -> !predictionLabel._1().equals(predictionLabel._2())).count() / testData.count();
        System.out.println(model.toDebugString() + " Accuracy: " + (1 - testErr) * 100 + "%");

        model = RandomForest.trainClassifier(data, classes, categoryInfo, trees, featureSubsetStrategy, impurity, maxDepth, maxBins, seed);

        String scope = "MultiStock";
        if (isSimulation) scope += "_SIMULATION";

        int modelNo = Integer.parseInt((dh.executeQuery("SELECT COALESCE(MAX(ModelNumber),0) FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION'")).get(0)) + 1;
        dh.executeCommand("INSERT INTO predictors(Model, Type, ModelNumber, Accuracy, Description, Filepath, Scope) VALUES ('Random Forest', 'CLASSIFICATION', " + modelNo + ", " + (1 - testErr) * 100 + ", 'Exponentially Smoothed Prices (Alpha " + SmoothingUtils.getAlpha() + "), Categorical Features, " + model.toString() + ", Depth " + maxDepth + "', 'res/model/RF" + modelNo + ".model', '" + scope + "')");
        model.save(jsc.sc(), System.getProperty("user.dir") + "/res/model/RF" + modelNo + ".model");
        System.out.println("Saved Model Successfully!");
    }

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

    static public boolean predictStock(ArrayList<String> stocks, String stock, int numberOfDays) throws SQLException {
        String results = dh.executeQuery("SELECT * FROM dailystockprices WHERE Symbol='" + stock + "' AND SmoothedClosePrice is not null AND SMA10 is not null AND EMA10 is not null AND MACD is not null AND MACDSig is not null AND MACDHist is not null AND RSI is not null AND ADX10 is not null AND CCI is not null AND AD is not null AND OBV is not null AND StoOscSlowK is not null AND StoOscSlowD is not null AND SMA20 is not null AND SMA200 is not null AND EMA5 IS NOT NULL AND EMA20 IS NOT NULL AND EMA200 IS NOT NULL AND SMA5 is not null AND WillR is not null ORDER BY TradeDate DESC LIMIT 1").get(0);
        String[] splitString = results.split(",");
        double newsSentiment = NaturalLanguageProcessor.getTodaysAverageSentiment(stock, 2);
        double features[] = new double[splitString.length + 1];

        features[0] = stocks.indexOf(stock);
        features[1] = numberOfDays;

        for (int i = 2; i < splitString.length; i++)
            features[i] = Double.parseDouble(splitString[i]);

        features[features.length - 1] = newsSentiment;

        return StockPredictor.predictDirection(new DenseVector(features));
    }
}