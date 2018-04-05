package Default;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.1
 */

public class Main extends Application {
    private static FXMLLoader loader;
    private static String[] arguments;

    /**
     * Starts the application and initialises Hadoop utils if on a Windows machine
     *
     * @param args Application arguments, passed in via the Command Line or IDE parameters
     */
    public static void main(String[] args) {
        if (System.getProperty("os.name").toLowerCase().contains("win"))
            System.setProperty("hadoop.home.dir", "c:/hadoop");
        launch(arguments = args);
    }

    /**
     * Accesses the arguments passed into the application
     *
     * @return Application arguments
     */
    static String[] getArguments() {
        return arguments;
    }

    /**
     * Access the controller used by the JavaFX application
     *
     * @return Main controller of the application
     */
    static public Controller getController() {
        return loader.getController();
    }

    /**
     * Starts the JavaFX application
     *
     * @param primaryStage Stage that contains the JavaFX GUI
     * @throws IOException Throws IOException if the view.fxml file does not exist/cannot be found or is otherwise unreadable
     */
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
}