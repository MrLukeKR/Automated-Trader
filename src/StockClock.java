import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class StockClock {
    VBox node = new VBox();
    Label clockName = new Label();
    Label clockTime = new Label();
    Label status = new Label();
    LocalTime tradeStart;
    LocalTime tradeEnd;
    LocalTime currTime;
    ZoneId zone;

    public StockClock(String name, LocalTime marketStart, LocalTime marketEnd, ZoneId zone){
        node.setAlignment(Pos.CENTER);
        node.setMinWidth(150);
        node.setMinHeight(100);

        this.zone = zone;
        tradeStart = marketStart;
        tradeEnd = marketEnd;

        clockName.setText(name);
        clockName.setFont(Font.font(null, FontWeight.BOLD, 16));

        clockTime.setFont(Font.font(null, 20));

        status.setFont(Font.font(null, 16));

        clockName.setTextAlignment(TextAlignment.CENTER);
        clockTime.setTextAlignment(TextAlignment.CENTER);
        status.setTextAlignment(TextAlignment.CENTER);

        updateTime();

        node.getChildren().add(this.clockName);
        node.getChildren().add(clockTime);
        node.getChildren().add(status);
    }

    public void updateTime(){
        Platform.runLater(()-> {
            currTime = LocalTime.now(zone);
            clockTime.setText(currTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));

            if (currTime.isAfter(tradeEnd) || currTime.isBefore(tradeStart)) {
                status.setText("CLOSED");
                status.setTextFill(Color.RED);
            } else {
                status.setText("OPEN");
                status.setTextFill(Color.GREEN);
            }
        });
    }

    public Node getNode(){return node;}
}
