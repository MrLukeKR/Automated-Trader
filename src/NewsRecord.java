import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;

public class NewsRecord {
    String symbol;
    HBox newsArticle = new HBox();
    Label stockSymbol;
    Text newsHeadline = new Text();

    public NewsRecord(String symbol, String headline){ //TODO: Add date to the info box
        this.symbol = symbol;
        stockSymbol = new Label(symbol);
        newsHeadline.setText(headline);
        newsHeadline.maxWidth(325);
        newsHeadline.setWrappingWidth(325);

        newsArticle.setMinWidth(350);
        newsArticle.setPrefWidth(350);

            newsHeadline.setFont(Font.font(null, 14));
        newsArticle.getChildren().add(stockSymbol);
        Separator sep = new Separator(Orientation.VERTICAL);
        newsArticle.getChildren().add(sep);
        newsArticle.getChildren().add(newsHeadline);

            stockSymbol.setFont(Font.font(null, 12));
            stockSymbol.setTextFill(Color.GREY);

            stockSymbol.setMinWidth(40);
            stockSymbol.setMinHeight(20);

            sep.setVisible(false);
    }

    public Node getNode(){ return newsArticle; }
}
