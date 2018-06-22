package Utility;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.Collections;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.9
 */

public class ChartUtils {
    /**
     * Creates and formats a new point on a Line Chart
     *
     * @param index Index of data point on the X-Axis
     * @param value Values of this data point
     * @return A Line Chart data point that can be added to a graph
     */
    static public XYChart.Data<Number, Number> createChartPoint(int index, double value) {
        XYChart.Data<Number, Number> point = new XYChart.Data<>(index, value);
        Rectangle rect = new Rectangle(0, 0);
        rect.setVisible(false);
        point.setNode(rect);
        return point;
    }

    /**
     * Reformats a line chart set of series to have thin lines
     * @param seriesCollection Line Chart Set of Series to reformat
     */
    static public void formatLineChart(ObservableList<XYChart.Series<Number, Number>> seriesCollection) {
        Platform.runLater(() -> {
            for (XYChart.Series<Number, Number> series : seriesCollection)
                series.nodeProperty().get().setStyle("-fx-stroke-width: 1px;");
        });
    }

    /**
     * Reformats a line chart series to have thin lines
     * @param series Line Chart Series to reformat
     */
    static public void formatLineChart(XYChart.Series<Number, Number> series) {
        Platform.runLater(() -> series.nodeProperty().get().setStyle("-fx-stroke-width: 1px;"));
    }

    /**
     * Sets the upper and lower bounds of the Y-Axis of a Line Chart
     * @param axis Y-Axis to perform the alignment on
     * @param values List of values contained in the Line Chart
     */
    static public void setBounds(NumberAxis axis, ArrayList<Double> values) {
        axis.setUpperBound(Collections.max(values));
        axis.setLowerBound(Collections.min(values));
    }
}