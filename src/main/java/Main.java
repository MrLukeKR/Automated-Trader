import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    public static void main(String[] args) {
        System.setProperty("hadoop.home.dir", "C:/hadoop");
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("view.fxml"));
        primaryStage.setScene(new Scene(root, 1920, 1030));
        primaryStage.setTitle("Automated Stock Trader");
        primaryStage.show();
        primaryStage.setOnCloseRequest(event -> {
            try {
                Controller.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(1);
        });
    }
}
