import org.apache.log4j.Level;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.regression.LabeledPoint;
import org.apache.spark.mllib.tree.RandomForest;
import org.apache.spark.mllib.tree.model.RandomForestModel;
import org.apache.spark.mllib.util.MLUtils;
import scala.Tuple2;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

public class StockPredictor {
    static SparkConf sparkConf = new SparkConf().setAppName("StockMarketPredictor").setMaster("local");
    static JavaSparkContext jsc = new JavaSparkContext(sparkConf);
    static RandomForestModel model;
    static DatabaseHandler dh;

    static public void initialise(DatabaseHandler spdh) {
        jsc.setLogLevel(Level.OFF.toString());
        dh = spdh;
    }

    static public String getModelInformation(){
        try {
            ArrayList<String> results = dh.executeQuery("SELECT Description, Accuracy FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' ORDER BY ModelNumber DESC LIMIT 1;");
            if(!results.isEmpty()) {
             String[] splitString = results.get(0).split(",");
             String finalString = "";
             for(int i = 0; i < splitString.length-1; i++)
                 finalString += splitString[i].trim() + "\r\n";

             double accuracy = Math.floor(Double.valueOf(splitString[splitString.length-1]) * 10000) / 100;

             finalString += "Accuracy: " + accuracy + "%";

             return finalString;
            }
        } catch (SQLException e) { e.printStackTrace(); }

        return "No model loaded";
    }

    static public String convertToLibSVM(String dataPoint){
        String[] splitString = dataPoint.split(",");
        int index = 1;

        String dp = splitString[splitString.length - 1];

        for (int j = 0; j < splitString.length - 1; j++)
            dp += " " + index++ + ":" + splitString[j];

        return dp;
    }

    static public ArrayList<String> convertCSVToLibSVM(ArrayList<String> dataPoints) {
        ArrayList<String> libSVMFormat = new ArrayList<>();

        for (String dataPoint : dataPoints)
            libSVMFormat.add(dataPoint);

        return libSVMFormat;
    }

    static public boolean predictDirection(Vector data){
        double value = model.predict(data);

        return value == 1;
    }

    static public void loadLatestRandomForest() throws SQLException {
        ArrayList<String> results = dh.executeQuery("SELECT Filepath FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' ORDER BY ModelNumber DESC LIMIT 1;");
        if(results.isEmpty()) return;
        loadRandomForest(results.get(0));
    }

    static public void loadRandomForest(String modelFile) {
        System.out.println("Loading Machine Learning Model '" + modelFile + "'...");
        model = RandomForestModel.load(jsc.sc(), modelFile);
        System.out.println("Loaded Machine Learning Model: " + model.toString()) ;
    }

    static public void trainLSTM(){
            }

    static public void trainRandomForest(String libSVMFilePath, int noOfStocks) throws SQLException {
        System.out.println("Training Random Forest...");
        JavaRDD<LabeledPoint> data = MLUtils.loadLibSVMFile(jsc.sc(), libSVMFilePath).toJavaRDD();

        JavaRDD<LabeledPoint>[] trainingTestSplits = data.randomSplit(new double[]{0.7, 0.3});
        JavaRDD<LabeledPoint> trainingData = trainingTestSplits[0];
        JavaRDD<LabeledPoint> testData = trainingTestSplits[1];

        Integer classes = 2;
        HashMap<Integer, Integer> categoryInfo = new HashMap<>();

        categoryInfo.put(0, noOfStocks);

        Integer trees = 300;
        String featureSubsetStrategy = "auto";
        String impurity = "gini";
        Integer maxDepth = 6;
        Integer maxBins = noOfStocks;
        Integer seed = 12345;

        model = RandomForest.trainClassifier(trainingData, classes, categoryInfo, trees, featureSubsetStrategy, impurity, maxDepth, maxBins, seed);

        JavaPairRDD<Double, Double> predictionAndLabel = testData.mapToPair((PairFunction<LabeledPoint, Double, Double>) point -> new Tuple2<>(model.predict(point.features()), point.label()));

        Double testErr = 1.0 * predictionAndLabel.filter((Function<Tuple2<Double, Double>, Boolean>) predictionLabel -> !predictionLabel._1().equals(predictionLabel._2())).count() / testData.count();
        System.out.println(model.toDebugString() + " Accuracy: " + (1 - testErr) * 100 + "%");

        model = RandomForest.trainClassifier(data,classes,categoryInfo,trees,featureSubsetStrategy,impurity,maxDepth,maxBins,seed);

        int modelNo = Integer.parseInt((dh.executeQuery("SELECT COALESCE(MAX(ModelNumber),0) FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION'")).get(0)) + 1;
        dh.executeCommand("INSERT INTO predictors(Model, Type, ModelNumber, Accuracy, Description, Filepath) VALUES ('Random Forest', 'CLASSIFICATION', " + modelNo + ", " + (1-testErr) + ", 'Exponentially Smoothed Prices (Alpha "+ SmoothingUtils.getAlpha() + "), Categorical Features, " + model.toString() + ", Depth " + maxDepth + "', 'res/model/RF" + modelNo + ".model')");
        model.save(jsc.sc(), "res/model/RF" + modelNo + ".model");
        System.out.println("Saved Model Successfully!");
    }
}
