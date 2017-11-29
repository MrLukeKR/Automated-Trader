import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;
import java.sql.SQLException;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("view.fxml"));
        primaryStage.setScene(new Scene(root, 1920, 1080));
        primaryStage.setTitle("Automated Stock Trader");
        primaryStage.show();
        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                System.exit(1);
            }
        });
    }


    public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException {
       launch(args);
    }
}
