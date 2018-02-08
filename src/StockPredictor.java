import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.mllib.regression.LabeledPoint;
import org.apache.spark.mllib.tree.RandomForest;
import org.apache.spark.mllib.tree.model.RandomForestModel;
import org.apache.spark.mllib.util.MLUtils;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.HashMap;

public class StockPredictor {
    static SparkConf sparkConf = new SparkConf().setAppName("StockMarketRandomForestClassification");
    static JavaSparkContext jsc = new JavaSparkContext(sparkConf);

    static public ArrayList<String> convertCSVToLibSVM(ArrayList<String> dataPoints) {
        ArrayList<String> libSVMFormat = new ArrayList<>();

        for (int i = 0; i < dataPoints.size(); i++) {
            String[] splitString = dataPoints.get(i).split(",");
            int index = 1;

            String dataPoint = splitString[splitString.length - 1];

            for (int j = 0; j < splitString.length - 1; j++)
                dataPoint += " " + index++ + ":" + splitString[j];

            libSVMFormat.add(dataPoint);
        }

        return libSVMFormat;
    }

    static public void trainRandomForest(String libSVMFilePath) {
        JavaRDD<LabeledPoint> data = MLUtils.loadLibSVMFile(jsc.sc(), libSVMFilePath).toJavaRDD();

        JavaRDD<LabeledPoint>[] trainingTestSplits = data.randomSplit(new double[]{0.7, 0.3});
        JavaRDD<LabeledPoint> trainingData = trainingTestSplits[0];
        JavaRDD<LabeledPoint> testData = trainingTestSplits[0];

        Integer classes = 2;
        HashMap<Integer, Integer> categoryInfo = new HashMap<>();
        Integer trees = 10;
        String featureSubsetStrategy = "auto";
        String impurity = "gini";
        Integer maxDepth = 5;
        Integer maxBins = 32;
        Integer seed = 12345;

        final RandomForestModel model = RandomForest.trainClassifier(trainingData, classes, categoryInfo, trees, featureSubsetStrategy, impurity, maxDepth, maxBins, seed);

        JavaPairRDD<Double, Double> predictionAndLabel = testData.mapToPair((PairFunction<LabeledPoint, Double, Double>) point -> new Tuple2<>(model.predict(point.features()), point.label()));

        Double testErr = 1.0 * predictionAndLabel.filter((Function<Tuple2<Double, Double>, Boolean>) predictionLabel -> !predictionLabel._1().equals(predictionLabel._2())).count() / testData.count();
        System.out.println("Random Forest Test Error: " + testErr);
    }
}
