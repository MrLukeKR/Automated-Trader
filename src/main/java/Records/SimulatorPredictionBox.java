package Records;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class SimulatorPredictionBox {
    private final HBox predictions = new HBox();
    Label[] predictionLabels;

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

    public Node getNode() {
        return predictions;
    }
}
