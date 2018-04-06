package Records;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.8
 */

public class SimulatorPredictionBox {
    private final HBox predictions = new HBox();
    private Label[] predictionLabels;

    /**
     * Initialises a new Prediction Box, given a stock, collection of days to predict and their respective prediction values
     *
     * @param stock            Stock ticker that the predictions are associated with
     * @param days             Prediction periods
     * @param predictionValues Predictions of Rise/Maintain or Fall for each of the prediction periods
     */
    public SimulatorPredictionBox(String stock, int[] days, boolean[] predictionValues) {
        predictions.setMinSize(180, 20);
        predictions.setPrefSize(180, 20);
        predictions.setMaxSize(180, 20);

        Label predictionSymbol = new Label();
        predictionSymbol.setFont(Font.font(null, FontWeight.BOLD, 14));
        predictionSymbol.setMinSize(60, 20);

        predictionSymbol.setText(stock);

        predictionLabels = new Label[days.length];

        for (int i = 0; i < predictionValues.length; i++) {
            predictionLabels[i] = new Label();
            predictionLabels[i].setFont(Font.font(null, 14));
            predictionLabels[i].setMinSize(30, 20);
        }

        updateValues(predictionValues);

        predictions.getChildren().add(predictionSymbol);
        predictions.getChildren().addAll(predictionLabels);
    }

    /**
     * Updates the prediction values of the record without having to recreate a new PredictionBox
     *
     * @param predictionValues Boolean array of new prediction values (True = Rise/Maintain, False = Fall)
     */
    public void updateValues(boolean[] predictionValues) {
        for (int i = 0; i < predictionValues.length; i++) {
            if (predictionValues[i]) {
                predictionLabels[i].setTextFill(Color.GREEN);
                predictionLabels[i].setText("\u25B2");
            } else {
                predictionLabels[i].setTextFill(Color.RED);
                predictionLabels[i].setText("\u25BC");
            }
        }
    }

    /**
     * Gets the JavaFX GUI node associated with this record for inclusion with the main application GUI
     * @return Record's JavaFX GUI node
     */
    public Node getNode() {
        return predictions;
    }
}
