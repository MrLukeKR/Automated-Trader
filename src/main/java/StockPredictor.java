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
    private static DatabaseHandler dh;

    static public void initialise(DatabaseHandler spdh) {
        jsc.setLogLevel(Level.OFF.toString());
        dh = spdh;
    }

    static public String getModelInformation(){
        try {
            ArrayList<String> results = dh.executeQuery("SELECT Description, Accuracy FROM predictors WHERE Model = 'Random Forest' AND Type = 'CLASSIFICATION' ORDER BY ModelNumber DESC LIMIT 1;");
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

    static private ArrayList<String> convertToClassificationTrainingArray(String stock, int index, int[] amountOfDaysArray) throws SQLException {
        ArrayList<String> dataPoints = new ArrayList<>();

        ArrayList<String> dbSchema = dh.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'dailystockprices';");

        StringBuilder command = new StringBuilder("SELECT * FROM dailystockprices WHERE Symbol='" + stock + "'");

        for(String column:dbSchema)
            command.append(" AND ").append(column).append(" IS NOT NULL");

        ArrayList<String> priceValues = dh.executeQuery(command + " ORDER BY TradeDate ASC;");

        int columnToPredict = dbSchema.indexOf("SmoothedClosePrice");
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
                StringBuilder dataPoint = new StringBuilder(String.valueOf(index) + "," + String.valueOf(amountOfDays));
                String[] splitString = priceValues.get(i).split(",");

                for (int j = 2; j < splitString.length; j++)
                    dataPoint.append(",").append(splitString[j]);

                dataPoint.append(",").append(sentiments[i]).append(",").append(String.valueOf((futurePrices[i] - currentPrices[i]) >= 0 ? 1 : 0));
                dataPoints.add(dataPoint.toString());
            }
        }

        System.out.println("Converted data to Classification Training Array for '" + stock + "'");

        return dataPoints;
    }

    static public void exportClassificationCSV(ArrayList<String> stocks, String path, int[] days, ProgressBar pb) throws FileNotFoundException, SQLException {
        final int t = stocks.size() - 1;
        int c = 0;
        File file = new File(path);
        PrintWriter pw = new PrintWriter(file);

        for (String stock : stocks) {
            for (String value : convertToClassificationTrainingArray(stock, c, days))
                pw.println(value);

            Controller.updateProgress(++c, t, pb);
        }

        pw.close();
    }

    static public void exportLibSVMFile(String csvPath, String libSVMPath) throws IOException {
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

    static public String convertToLibSVM(String dataPoint){
        String[] splitString = dataPoint.split(",");
        int index = 1;

        StringBuilder dp = new StringBuilder(splitString[splitString.length - 1]);

        for (int j = 0; j < splitString.length - 1; j++)
            dp.append(" ").append(index++).append(":").append(splitString[j]);

        return dp.toString();
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

    private static void loadRandomForest(String modelFile) {
        System.out.println("Loading Machine Learning Model '" + modelFile + "'...");
        model = RandomForestModel.load(jsc.sc(), modelFile);
        System.out.println("Loaded Machine Learning Model: " + model.toString()) ;
    }

    static public void exportSeparateClassificationCSV(ArrayList<String> stocks, String filePath, int[] days, ProgressBar pb) throws FileNotFoundException, SQLException {
        final int t = stocks.size() - 1;
        int c = 0;
        File featureFile = new File(filePath + "Features.csv");
        File labelFile = new File(filePath + "Labels.csv");
        PrintWriter fpw = new PrintWriter(featureFile);
        PrintWriter lpw = new PrintWriter(labelFile);

        for (String stock : stocks) {
            for (String value : convertToClassificationTrainingArray(stock, c, days)) {
                String[] splitString = value.split(",");
                StringBuilder feature = new StringBuilder(splitString[0]);
                for(int i = 1; i < splitString.length-1; i++)
                    feature.append(",").append(splitString[i]);
                fpw.println(feature);
                lpw.println(splitString[splitString.length-1]);
            }

            Controller.updateProgress(++c, t, pb);
        }

        fpw.close();
        lpw.close();
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
        Integer maxDepth = 8;
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
