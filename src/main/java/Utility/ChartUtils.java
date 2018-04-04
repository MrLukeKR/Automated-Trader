package Utility;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.Collections;

public class ChartUtils {
    static public XYChart.Data<Number, Number> createChartPoint(int index, double value) {
        XYChart.Data<Number, Number> point = new XYChart.Data<>(index, value);
        Rectangle rect = new Rectangle(0, 0);
        rect.setVisible(false);
        point.setNode(rect);
        return point;
    }

    static public void formatLineChart(ObservableList<XYChart.Series<Number, Number>> seriesCollection) {
        Platform.runLater(() -> {
            for (XYChart.Series<Number, Number> series : seriesCollection)
                series.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
        });
    }

    static public void formatLineChart(XYChart.Series<Number, Number> series) {
        Platform.runLater(() -> series.nodeProperty().get().setStyle("-fx-stroke-width: 1px;"));
    }

    static public void setBounds(NumberAxis axis, ArrayList<Double> values) {
        axis.setUpperBound(Collections.max(values));
        axis.setLowerBound(Collections.min(values));
    }
}
