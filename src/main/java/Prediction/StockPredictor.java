package Prediction;

import Default.Controller;
import Default.DatabaseHandler;
import Default.Main;
import Processing.NaturalLanguageProcessor;
import Utility.TrainingFileUtils;
import javafx.scene.control.ProgressBar;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.*;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

public class StockPredictor {
    //private static final SparkConf sparkConf = new SparkConf().setMaster("local[1]").setAppName("StockMarketPredictor").set("spark.executor.cores", "1").set("spark.driver.memory","2g").set("spark.executor.memory","6g").setSparkHome("/Users/lukerose/IdeaProjects/Automated-Trading/res/sparkhome");
    //private static final JavaSparkContext jsc = new JavaSparkContext(sparkConf);
    private static RandomForest model;
    private static HashMap<String, RandomForest> singleModels = new HashMap<>();
    private static ProgressBar pb;
    private static DatabaseHandler dh;

    //private static final SparkStatusTracker tracker = new SparkStatusTracker(jsc.sc());

    static public void initialise(DatabaseHandler spdh, ProgressBar sppb) {
    //    System.out.println(jsc.getConf().toDebugString());
    //    jsc.setLogLevel(Level.FATAL.toString());
        dh = spdh;
        pb=sppb;
    }

    static public String getModelInformation(){
        try {
            ArrayList<String> results = dh.executeQuery("SELECT Description, Accuracy FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope='MultiStock' ORDER BY ModelNumber DESC LIMIT 1;");
            if(!results.isEmpty()) {
             String[] splitString = results.get(0).split(",");
             StringBuilder finalString = new StringBuilder();
             for(int i = 0; i < splitString.length-1; i++)
                 finalString.append(splitString[i].trim()).append("\r\n");

             double accuracy = Double.valueOf(splitString[splitString.length-1]);

             finalString.append("Accuracy: ").append(accuracy).append("%");

             return finalString.toString();
            }
        } catch (SQLException e) { e.printStackTrace(); }

        return "No model loaded";
    }

    static public boolean predictDirection(Instance data) throws Exception {
        double value = model.classifyInstance(data);

        return value == 1;
    }

    static public boolean predictDirection(Instance data, String stock){
        /*
        double value = singleModels.get(stock).predict(data);

        return value == 1;
        */
        return false;
    }

    static public void loadLatestRandomForest(String stock) throws Exception {
        ArrayList<String> results = dh.executeQuery("SELECT Filepath FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope = '" + stock + "' ORDER BY ModelNumber DESC LIMIT 1;");
        if(results.isEmpty()) return;
        loadRandomForest(System.getProperty("user.dir") + results.get(0),stock);
    }

    static public void loadLatestSimulationRandomForest() throws Exception {

        ArrayList<String> results = dh.executeQuery("SELECT Filepath FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope = 'MultiStock_SIMULATION' ORDER BY ModelNumber DESC LIMIT 1;");
        if(results.isEmpty()) return;
        loadRandomForest(System.getProperty("user.dir") + results.get(0));
    }

    static public void loadLatestRandomForest() throws Exception {
        ArrayList<String> results = dh.executeQuery("SELECT Filepath FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope = 'MultiStock' ORDER BY ModelNumber DESC LIMIT 1;");
        if(results.isEmpty()) return;
        loadRandomForest(System.getProperty("user.dir") + results.get(0));
    }

    private static void loadRandomForest(String modelFile, String stock) throws Exception {
        System.out.println("Loading Machine Learning Model '" + modelFile + "'...");
        singleModels.put(stock, (RandomForest) SerializationHelper.read(modelFile));
        System.out.println("Loaded Machine Learning Model: " + singleModels.get(stock).toString()) ;
    }

    static public boolean isModelLoaded() {
        return model != null;
    }

    private static void loadRandomForest(String modelFile) {
        Main.getController().updateCurrentTask("Loading Machine Learning Model '" + modelFile + "'...", false, false);
        try {
            model = (RandomForest) SerializationHelper.read(modelFile);
            Main.getController().updateCurrentTask("Loaded Machine Learning Model: " + model.toString(), false, false);
        } catch (Exception e) {
            Main.getController().updateCurrentTask("Couldn't load ML Model: " + e.getMessage(), true, true);
        }

    }

    /*
    static public void trainRandomForest(String libSVMFilePath, String stock, boolean simulation) throws SQLException {
        System.out.println("Training Single-Stock Random Forest for " + stock + "...");
        JavaRDD<LabeledPoint> data = MLUtils.loadLibSVMFile(jsc.sc(), libSVMFilePath).toJavaRDD().unpersist();

        JavaRDD<LabeledPoint>[] trainingTestSplits = data.randomSplit(new double[]{0.7, 0.3});
        JavaRDD<LabeledPoint> trainingData = trainingTestSplits[0];
        JavaRDD<LabeledPoint> testData = trainingTestSplits[1];

        Integer classes = 2;
        HashMap<Integer, Integer> categoryInfo = new HashMap<>();

        Integer trees = 150;
        String featureSubsetStrategy = "auto";
        String impurity = "gini";
        Integer maxDepth = 10;
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

        int modelNo = Integer.parseInt((dh.executeQuery("SELECT COALESCE(MAX(ModelNumber),0) FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope='" + stock + "'")).get(0)) + 1;
        String scope = stock;
        if(simulation) scope += "_SIMULATION";
        dh.executeCommand("INSERT INTO predictors(Model, Type, ModelNumber, Accuracy, Description, Filepath, Scope) VALUES ('Random Forest', 'CLASSIFICATION', " + modelNo + ", " + (1-testErr) + ", 'Exponentially Smoothed Prices (Alpha 0.25), Single Stock" + tempModel.toString() + ", Depth " + maxDepth + "', 'res/model/" + stock + "/RF" + modelNo + ".model', '" + scope + "')");
        File stockDir = new File("res/model/" + stock);
        if(! stockDir.exists())
            stockDir.mkdirs();

        tempModel.save(jsc.sc(), "res/model/" + stock + "/RF" + modelNo + ".model");
        singleModels.put(stock,tempModel);
        System.out.println("Saved Model Successfully!");
    }
*/

    static public void trainRandomForest(String filePath, int noOfStocks, boolean simulation) throws Exception {
        System.out.println("Loading training data for Multi-Stock Random Forest...");

        Instances dataSet = new Instances(TrainingFileUtils.loadAttributeCSV(filePath));

        NumericToNominal ntn = new NumericToNominal();
        ntn.setOptions(new String[]{"-R","last"});
        ntn.setInputFormat(dataSet);
        Instances fixedDataSet = Filter.useFilter(dataSet, ntn);
        dataSet.delete();
        fixedDataSet.setClassIndex(fixedDataSet.numAttributes() - 1);

        double splitPercent = 0.95;
        int trainingAmount = (int)Math.round(fixedDataSet.numInstances() * splitPercent);

        Instances trainingSet = new Instances(fixedDataSet, 0,trainingAmount);
        Instances testingSet = new Instances(fixedDataSet, trainingAmount, fixedDataSet.numInstances() - trainingAmount);

        fixedDataSet.delete();

        System.out.println("Training Random Forest...");
        RandomForest randomForest = new RandomForest();

        randomForest.setNumIterations(100);
        randomForest.setMaxDepth(6);
        randomForest.setOptions(new String[]{"-num-slots","0"});

        randomForest.buildClassifier(trainingSet);

        System.out.println("Evaluating Random Forest...");
        Evaluation evaluation = new Evaluation(trainingSet);
        evaluation.evaluateModel(randomForest, testingSet);

        trainingSet.delete();
        testingSet.delete();

        System.out.println("----Random Forest Evaluation----");
        System.out.println(evaluation.toSummaryString());
        System.out.println(randomForest);
        System.out.println(evaluation.toMatrixString());
        System.out.println(evaluation.toClassDetailsString());
        System.out.println("Accuracy: " + evaluation.pctCorrect());

        String scope = "MultiStock";
        if(simulation) scope += "_SIMULATION";

        int modelNo = Integer.parseInt((dh.executeQuery("SELECT COALESCE(MAX(ModelNumber),0) FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope='" + scope + "'")).get(0)) + 1;

        File stockDir = new File(System.getProperty("user.dir") + "/res/model/" + scope);
        if(! stockDir.exists())
            stockDir.mkdirs();

        SerializationHelper.write(System.getProperty("user.dir") + "/res/model/" +  scope + "/RF" + modelNo + ".model",randomForest);
        dh.executeCommand("INSERT INTO predictors(Model, Type, ModelNumber, Accuracy, Description, Filepath, Scope) VALUES ('Random Forest', 'CLASSIFICATION', " + modelNo + ", " + evaluation.pctCorrect() + ", 'Exponentially Smoothed Prices (Alpha 0.1), Single Stock" + randomForest.toString() + ", Depth " + randomForest.getMaxDepth() + "', '/res/model/" + scope + "/RF" + modelNo + ".model', '" + scope + "')");

        System.out.println("Saved Model Successfully!");
    }

    static public boolean[] predictStocks(ArrayList<String> stocks, int[] dayArray, ProgressBar stockForecastProgress) throws Exception {
        boolean predictions[] = new boolean[stocks.size() * dayArray.length];

        int i = 0;

        Main.getController().updateCurrentTask("Predicting Stock Performance...", false, false);
        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, stockForecastProgress);

        for (int numberOfDays : dayArray)
            for (String stock : stocks) {
                predictions[i] = predictStock(stocks, stock, numberOfDays);
                if (predictions[i])
                    Main.getController().updateCurrentTask(numberOfDays + " Day Prediction for " + stock + ": RISE/MAINTAIN", false, false);
                else
                    Main.getController().updateCurrentTask(numberOfDays + " Day Prediction for " + stock + ": FALL", false, false);
                i++;
                Controller.updateProgress(i, predictions.length - 1, stockForecastProgress);
            }

        Controller.updateProgress(0, stockForecastProgress);
        Main.getController().updateCurrentTask("Predicted Stock Performance!", false, false);

        return predictions;
    }


    static public boolean predictStock(ArrayList<String> stocks, String stock, int numberOfDays) throws Exception {
        String results = dh.executeQuery("SELECT * FROM dailystockprices WHERE Symbol='" + stock + "' AND SmoothedClosePrice is not null AND SMA10 is not null AND EMA10 is not null AND MACD is not null AND MACDSig is not null AND MACDHist is not null AND RSI is not null AND ADX10 is not null AND CCI is not null AND AD is not null AND OBV is not null AND StoOscSlowK is not null AND StoOscSlowD is not null AND SMA20 is not null AND SMA200 is not null AND EMA5 IS NOT NULL AND EMA20 IS NOT NULL AND EMA200 IS NOT NULL AND SMA5 is not null AND WillR is not null ORDER BY TradeDate DESC LIMIT 1").get(0);
        String[] splitString = results.split(",");

        double newsSentiment = NaturalLanguageProcessor.getTodaysAverageSentiment(stock, 2);
        double features[] = new double[splitString.length + 1];

        features[0] = stocks.indexOf(stock);
        features[1] = numberOfDays;

        for (int i = 2; i < splitString.length; i++)
            features[i] = Double.parseDouble(splitString[i]);

        features[features.length - 1] = newsSentiment;

        String[] values = new String[]{"Index","Days","OpenPrice","HighPrice","LowPrice","ClosePrice","TradeVolume","PercentChange","SmoothedClosePrice","SMA5","SMA10","SMA20","SMA200","EMA5","EMA10","EMA20","EMA200","MACD","MACDSig","MACDHist","RSI","ADX10","CCI","AD","OBV","StoOscSlowK","StoOscSlowD","WillR","Sentiment"};

        ArrayList<Attribute> attributes = new ArrayList<>();

        for(String value : values)
            attributes.add(new Attribute(value));

        ArrayList<String> predictions = new ArrayList<>();
        predictions.add ("0");
        predictions.add("1");

        Attribute classes = new Attribute("Predictions", predictions);

        attributes.add(classes);

        double[] unlabelledData = new double[features.length+1];

        for(int i = 0; i < features.length; i++)
            unlabelledData[i] = features[i];

        unlabelledData[unlabelledData.length-1] = -1;

        Instance dataPoint = new DenseInstance(unlabelledData.length, unlabelledData);
        Instances dataSet = new Instances("ToPredict",attributes,0);
        dataSet.add(dataPoint);
        dataPoint.setMissing(dataSet.numAttributes() - 1);

        NumericToNominal ntn = new NumericToNominal();
        ntn.setOptions(new String[]{"-R", "last"});
        ntn.setInputFormat(dataSet);
        Instances fixedDataSet = Filter.useFilter(dataSet, ntn);
        dataSet.delete();
        fixedDataSet.setClassIndex(fixedDataSet.numAttributes() - 1);

        return StockPredictor.predictDirection(fixedDataSet.instance(0));
    }
}