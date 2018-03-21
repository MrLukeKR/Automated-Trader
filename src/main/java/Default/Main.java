package Default;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;


public class Main extends Application {
    static FXMLLoader loader;
    static private String[] arguments;

    public static void main(String[] args) { launch(arguments = args); }

    public static String[] getArguments(){return arguments;}

    @Override
    public void start(Stage primaryStage) throws IOException {
        loader = new FXMLLoader(getClass().getResource("/view.fxml"));
        Parent root = loader.load();

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

    static public Controller getController(){
        return loader.getController();
    }
}