package APIHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.1
 */

public class NewsDownloader {
    static private boolean newsUpdating = false;

    /**
     * Downloads the news metadata and content for a list of given stocks
     *
     * @param stocks List of stock tickers to download news for (e.g. AAL, AAPL, BIIB etc.)
     * @throws IOException          Throws IOException if the request fails due to server unavailability or connection refusal
     * @throws SQLException         Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     * @throws InterruptedException Throws InterruptedException if the sleep function is interrupted by another process
     */
    static public void updateNews(ArrayList<String> stocks) throws SQLException, InterruptedException, IOException {
        if (newsUpdating) return;
        newsUpdating = true;

        INTRINIOHandler.getHistoricNews(stocks);

        newsUpdating = false;
    }

    /**
     * Access whether or not news is currently updating (useful when downloading news from a separate thread)
     *
     * @return True if news articles are currently being downloaded, false otherwise
     */
    static public boolean getNewsUpdating() { return newsUpdating; }
}
