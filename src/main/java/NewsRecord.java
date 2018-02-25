import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

class NewsRecord {
    private final HBox newsArticle = new HBox();

    public NewsRecord(String symbol, String headline){ //TODO: Add date to the info box
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

    public Node getNode(){ return newsArticle; }
}
