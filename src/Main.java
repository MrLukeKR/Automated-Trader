import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

public class Main extends Application {
    static DatabaseHandler dh = new DatabaseHandler();
    static AlphaVantageHandler avh = new AlphaVantageHandler();

    @Override
    public void start(Stage primaryStage) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("sample.fxml"));
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root, 300, 275));
        primaryStage.show();

     }


    public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException {
//        launch(args);

        //File file = new File("res/historicstocks/" + symbol + ".csv");
        //StockRecordParser.importDailyMarketData(file, symbol, dh);

            updateStockData();
    }

    private static void updateStockData(){
        try {
            dh.init("agent","0Y5q0m28pSB9jj2O");
        } catch (Exception e) {}

        avh.init("PBATJ7L9N8SNK835");

        ArrayList<String> stocks = new ArrayList<>();

        try {
            stocks = dh.executeQuery("SELECT Symbol FROM indices");
        } catch (SQLException e) {
            e.printStackTrace();
        }


        for(String symbol : stocks) {
            new Thread(()-> {
                ArrayList<String> temp = null;
                try {
                    temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=" + symbol + "&interval=1min&datatype=csv&outputsize=full&apikey=" + avh.getApiKey());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                System.out.println("Downloaded intraday history of " + symbol);

                StockRecordParser.importIntradayMarketData(temp, symbol, dh);

                System.out.println("Successully committed " + symbol + " intraday history to the database!");
            }).start();
        }

        for(String symbol : stocks) {
            new Thread(()-> {
                ArrayList<String> temp = null;
                try {
                    temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol + "&datatype=csv&outputsize=full&apikey=" + avh.getApiKey());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            System.out.println("Downloaded full history of " + symbol);
            StockRecordParser.importDailyMarketData(temp, symbol, dh);
                System.out.println("Successully committed " + symbol + " full history to the database!");
            }).start();
        }
    }
}
