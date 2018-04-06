package Records;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.1
 */

public class NewsRecord {
    private final HBox newsArticle = new HBox();

    /**
     * Initialises a new News Record, given a stock's ticker (e.g. AAPL) and a news headline
     *
     * @param symbol   Stock ticker (e.g. AAPL for Apple Inc.)
     * @param headline News article headline
     */
    public NewsRecord(String symbol, String headline) {
        Label stockSymbol = new Label(symbol);
        Text newsHeadline = new Text();
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

    /**
     * Returns the JavaFX GUI node associated with this record for inclusion with the main application GUI
     * @return Record's JavaFX GUI node
     */
    public Node getNode(){ return newsArticle; }
}
