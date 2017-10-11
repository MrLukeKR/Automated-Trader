import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class Main extends Application {
    static DatabaseHandler dh = new DatabaseHandler();
    static AlphaVantageHandler avh = new AlphaVantageHandler();
    static boolean initialUpdate = true;
    static boolean quit = false;

    @Override
    public void start(Stage primaryStage) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("sample.fxml"));
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root, 300, 275));
        primaryStage.show();
     }


    public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException {
//        launch(args);


        try {
            dh.init("agent","0Y5q0m28pSB9jj2O");
        } catch (Exception e) {}

        avh.init("PBATJ7L9N8SNK835");


        while(!quit) {
            try {
                updateStockData();
                TimeUnit.SECONDS.sleep(60);
            } catch (InterruptedException e) {
            }
        }

    }

    private static void updateStockData(){

        ArrayList<String> stocks = new ArrayList<>();

        try {
            stocks = dh.executeQuery("SELECT Symbol FROM indices");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for(String symbol : stocks) {
            if(initialUpdate)
            new Thread(()-> {
                File file = new File("res/historicstocks/" + symbol + ".csv");
                try {
                    StockRecordParser.importDailyMarketData(file, symbol, dh);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                System.out.println("Successfully committed complete Yahoo records of " + symbol + " to the database!");

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
            else
                new Thread(()-> {
                    ArrayList<String> temp = null;
                try {
                    temp = avh.submitRequest("https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY&symbol=" + symbol + "&interval=1min&datatype=csv&outputsize=compact&apikey=" + avh.getApiKey());
                } catch (IOException e) {
                }
                    System.out.println("Downloaded " + symbol + " 1 minute update");
                    StockRecordParser.importIntradayMarketData(temp, symbol, dh);

                }).start();
        }

        if(initialUpdate)
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

            if(initialUpdate)
                initialUpdate = false;
        }
    }
}
