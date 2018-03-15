package APIHandler;

import Default.DatabaseHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

public class NewsDownloader {

    static private boolean newsUpdating = false;
    static private DatabaseHandler databaseHandler = null;

    static public void initialise(DatabaseHandler nddh) {
        databaseHandler = nddh;
    }

    static public ArrayList<String> updateNews(ArrayList<String> stocks) throws SQLException, InterruptedException, IOException {
        if (newsUpdating) return null;
        newsUpdating = true;

        INTRINIOHandler.getHistoricNews(stocks);

        newsUpdating = false;

        return databaseHandler.executeQuery("SELECT DISTINCT Symbol, Headline, Published FROM newsarticles WHERE DATE(Published) = CURDATE() ORDER BY Published DESC");


    }

    static public boolean getNewsUpdating() {
        return newsUpdating;
    }
}
