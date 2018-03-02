import javafx.scene.control.ProgressBar;
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
/*
import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader;
import org.datavec.api.split.FileSplit;
import org.deeplearning4j.datasets.datavec.RecordReaderDataSetIterator;
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.BackpropType;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.io.ClassPathResource;
import org.nd4j.linalg.lossfunctions.LossFunctions;
*/

import java.io.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

class StockPredictor {
    private static final SparkConf sparkConf = new SparkConf().setAppName("StockMarketPredictor").setMaster("local");
    private static final JavaSparkContext jsc = new JavaSparkContext(sparkConf);
    private static RandomForestModel model;
    private static HashMap<String, RandomForestModel> singleModels = new HashMap<>();
    private static DatabaseHandler dh;

    static public void initialise(DatabaseHandler spdh) {
        jsc.setLogLevel(Level.OFF.toString());
        dh = spdh;
    }

    static public String getModelInformation(){
        try {
            ArrayList<String> results = dh.executeQuery("SELECT Description, Accuracy FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope='MultiStock' ORDER BY ModelNumber DESC LIMIT 1;");
            if(!results.isEmpty()) {
             String[] splitString = results.get(0).split(",");
             StringBuilder finalString = new StringBuilder();
             for(int i = 0; i < splitString.length-1; i++)
                 finalString.append(splitString[i].trim()).append("\r\n");

             double accuracy = Math.floor(Double.valueOf(splitString[splitString.length-1]) * 10000) / 100;

             finalString.append("Accuracy: ").append(accuracy).append("%");

             return finalString.toString();
            }
        } catch (SQLException e) { e.printStackTrace(); }

        return "No model loaded";
    }

    static public boolean predictDirection(Vector data){
        double value = model.predict(data);

        return value == 1;
    }

    static public boolean predictDirection(Vector data, String stock){
        double value = singleModels.get(stock).predict(data);

        return value == 1;
    }

    static public void loadLatestRandomForest(String stock) throws SQLException {
        ArrayList<String> results = dh.executeQuery("SELECT Filepath FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope = '" + stock + "' ORDER BY ModelNumber DESC LIMIT 1;");
        if(results.isEmpty()) return;
        loadRandomForest(results.get(0),stock);
    }

    static public void loadLatestRandomForest() throws SQLException {
        ArrayList<String> results = dh.executeQuery("SELECT Filepath FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope = 'MultiStock' ORDER BY ModelNumber DESC LIMIT 1;");
        if(results.isEmpty()) return;
        loadRandomForest(results.get(0));
    }

    private static void loadRandomForest(String modelFile, String stock) {
        System.out.println("Loading Machine Learning Model '" + modelFile + "'...");
        singleModels.put(stock, RandomForestModel.load(jsc.sc(), modelFile));
        System.out.println("Loaded Machine Learning Model: " + singleModels.get(stock).toString()) ;
    }

    private static void loadRandomForest(String modelFile) {
        System.out.println("Loading Machine Learning Model '" + modelFile + "'...");
        model = RandomForestModel.load(jsc.sc(), modelFile);
        System.out.println("Loaded Machine Learning Model: " + model.toString()) ;
    }

    static public void trainLSTM(int inputColumns) {
        int layer1Size = 200;
        int layer2Size = 400;
        int layer3Size = 300;
/*
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).iterations(1)
                .learningRate(0.1)
                .seed(12345)
                .regularization(true)
                .l2(0.001)
                .weightInit(WeightInit.XAVIER)
                .updater(Updater.RMSPROP)
                .list()
                .layer(0, new GravesLSTM.Builder().nIn(inputColumns).nOut(layer1Size).activation(Activation.RELU).gateActivationFunction(Activation.HARDSIGMOID).dropOut(0.5).build())
                .layer(1, new GravesLSTM.Builder().nIn(layer1Size).nOut(layer2Size).activation(Activation.RELU).gateActivationFunction(Activation.HARDSIGMOID).dropOut(0.5).build())
                .layer(2, new DenseLayer.Builder().nIn(layer2Size).nOut(layer3Size).activation(Activation.RELU).build())
                .layer(3, new RnnOutputLayer.Builder().nIn(layer3Size).nOut(1).activation(Activation.IDENTITY).lossFunction(LossFunctions.LossFunction.MSE).build())
                .backpropType(BackpropType.TruncatedBPTT)
                .tBPTTForwardLength(10)
                .tBPTTBackwardLength(10)
                .pretrain(false)
                .backprop(true)
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.setListeners(new ScoreIterationListener(100));


        SequenceRecordReader featureReader = new CSVSequenceRecordReader(0,",");
        SequenceRecordReader labelReader = new CSVSequenceRecordReader(0,",");

        featureReader.initialize(new FileSplit(new File("res/TrainingFiles/SmoothedNASDAQTrainingFeatures.csv")));
        labelReader.initialize(new FileSplit(new File("res/TrainingFiles/SmoothedNASDAQTrainingLabels.csv")));

        DataSetIterator iterator = new SequenceRecordReaderDataSetIterator(featureReader,labelReader,150,2);
        DataSet data = iterator.next();

        SplitTestAndTrain tAndt = data.splitTestAndTrain(0.7);

        DataSet training = tAndt.getTrain();
        DataSet test = tAndt.getTest();

        net.fit(training);
        Evaluation eval = new Evaluation(2);
        INDArray output = net.output(test.getFeatures());

        eval.eval(test.getLabels(),output);
        System.out.println(eval.stats());
        */
            }

    static public void trainRandomForest(String libSVMFilePath, String stock) throws SQLException {
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

        tempModel = RandomForest.trainClassifier(data,classes,categoryInfo,trees,featureSubsetStrategy,impurity,maxDepth,32,seed);

        int modelNo = Integer.parseInt((dh.executeQuery("SELECT COALESCE(MAX(ModelNumber),0) FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' AND Scope='" + stock + "'")).get(0)) + 1;
        dh.executeCommand("INSERT INTO predictors(Model, Type, ModelNumber, Accuracy, Description, Filepath, Scope) VALUES ('Random Forest', 'CLASSIFICATION', " + modelNo + ", " + (1-testErr) + ", 'Exponentially Smoothed Prices (Alpha 0.25), Single Stock" + tempModel.toString() + ", Depth " + maxDepth + "', 'res/model/" + stock + "/RF" + modelNo + ".model', '" + stock + "')");
        File stockDir = new File("res/model/" + stock);
        if(! stockDir.exists())
            stockDir.mkdirs();

        tempModel.save(jsc.sc(), "res/model/" + stock + "/RF" + modelNo + ".model");
        singleModels.put(stock,tempModel);
        System.out.println("Saved Model Successfully!");
    }

    static public void trainRandomForest(String libSVMFilePath, int noOfStocks) throws SQLException {
        System.out.println("Training Multi-Stock Random Forest...");
        JavaRDD<LabeledPoint> data = MLUtils.loadLibSVMFile(jsc.sc(), libSVMFilePath).toJavaRDD().unpersist();

        JavaRDD<LabeledPoint>[] trainingTestSplits = data.randomSplit(new double[]{0.7, 0.3});
        JavaRDD<LabeledPoint> trainingData = trainingTestSplits[0];
        JavaRDD<LabeledPoint> testData = trainingTestSplits[1];

        Integer classes = 2;
        HashMap<Integer, Integer> categoryInfo = new HashMap<>();

        categoryInfo.put(0, noOfStocks);

        Integer trees = 150;
        String featureSubsetStrategy = "auto";
        String impurity = "gini";
        Integer maxDepth = 8;
        Integer maxBins = noOfStocks;
        Integer seed = 12345;

        model = RandomForest.trainClassifier(trainingData, classes, categoryInfo, trees, featureSubsetStrategy, impurity, maxDepth, maxBins, seed);
        trainingData = null;
        JavaPairRDD<Double, Double> predictionAndLabel = testData.mapToPair((PairFunction<LabeledPoint, Double, Double>) point -> new Tuple2<>(model.predict(point.features()), point.label()));

        Double testErr = 1.0 * predictionAndLabel.filter((Function<Tuple2<Double, Double>, Boolean>) predictionLabel -> !predictionLabel._1().equals(predictionLabel._2())).count() / testData.count();
        System.out.println(model.toDebugString() + " Accuracy: " + (1 - testErr) * 100 + "%");

        model = RandomForest.trainClassifier(data,classes,categoryInfo,trees,featureSubsetStrategy,impurity,maxDepth,maxBins,seed);

        int modelNo = Integer.parseInt((dh.executeQuery("SELECT COALESCE(MAX(ModelNumber),0) FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION'")).get(0)) + 1;
        dh.executeCommand("INSERT INTO predictors(Model, Type, ModelNumber, Accuracy, Description, Filepath, Scope) VALUES ('Random Forest', 'CLASSIFICATION', " + modelNo + ", " + (1-testErr) + ", 'Exponentially Smoothed Prices (Alpha "+ SmoothingUtils.getAlpha() + "), Categorical Features, " + model.toString() + ", Depth " + maxDepth + "', 'res/model/RF" + modelNo + ".model', 'MultiStock')");
        model.save(jsc.sc(), "res/model/RF" + modelNo + ".model");
        System.out.println("Saved Model Successfully!");
    }
}
