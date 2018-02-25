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
import java.util.Calendar;

class StockClock {
    private final VBox node = new VBox();
    private final Label clockTime = new Label();
    private final Label status = new Label();
    private final LocalTime tradeStart;
    private final LocalTime tradeEnd;
    private LocalTime currTime;
    private final ZoneId zone;

    public StockClock(String name, LocalTime marketStart, LocalTime marketEnd, ZoneId zone){
        node.setAlignment(Pos.CENTER);
        node.setMinWidth(150);
        node.setMinHeight(100);

        this.zone = zone;
        tradeStart = marketStart;
        tradeEnd = marketEnd;

        Label clockName = new Label();
        clockName.setText(name);
        clockName.setFont(Font.font(null, FontWeight.BOLD, 16));

        clockTime.setFont(Font.font(null, 20));

        status.setFont(Font.font(null, 16));

        clockName.setTextAlignment(TextAlignment.CENTER);
        clockTime.setTextAlignment(TextAlignment.CENTER);
        status.setTextAlignment(TextAlignment.CENTER);

        updateTime();

        node.getChildren().add(clockName);
        node.getChildren().add(clockTime);
        node.getChildren().add(status);
    }

    public void updateTime(){
        Platform.runLater(()-> {
            currTime = LocalTime.now(zone);
            clockTime.setText(currTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));

            if (currTime.isAfter(tradeEnd)
                    || currTime.isBefore(tradeStart)
                    || Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                    || Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
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
