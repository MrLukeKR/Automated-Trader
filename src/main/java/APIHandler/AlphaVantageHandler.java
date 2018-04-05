package APIHandler;

import Default.Main;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.1
 */

public class AlphaVantageHandler {
    /**
     * TOR proxy connection, used to bypass IP-based API limits, for example if behind a network where multiple AlphaVantage calls are being made by different users/API keys
     */
    private final Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 9050));
    private final boolean USE_PROXY = false;

    private String apiKey;
    private boolean useProxy = false;
    private boolean allPaused = false;
    private int failedDownloads = 0;
    private int pausedDownloads = 0;
    private int currentlyDownloading = 0;
    private final int MAX_CONCURRENT_DOWNLOADS = 30;
    private int downloadsSinceToggle = 0;
    private final Semaphore availableThreads = new Semaphore(MAX_CONCURRENT_DOWNLOADS, false);

    /**
     * Initialises the AlphaVantage Handler class using the necessary API key
     *
     * @param apiKey The AlphaVantage API Key, required to access the stock feed API
     * @see <a href="https://www.alphavantage.co/support/#api-key">Request a free API key<a/>
     */
    public void init(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Access the number of successful downloads since the last proxy switch
     *
     * @return The number of successful downloads since the last proxy switch
     */
    private synchronized int getDownloadsSinceToggle() {return downloadsSinceToggle;
    }

    /**
     * Increases the number of successful downloads since the last proxy switch by 1
     */
    private synchronized void incrementDownloadsSinceToggle(){downloadsSinceToggle++;
    }

    /**
     * Resets the number of successful downloads since the last proxy switch to 0 (used whenever the proxy is switched)
     */
    private synchronized void resetDownloadsSinceToggle(){downloadsSinceToggle = 0;
    }

    /**
     * Access the status of whether or not proxies are used when downloading stock data
     * @return True if the class is using a proxy, false otherwise
     */
    private synchronized boolean getUseProxy() {return useProxy;
    }

    /**
     * Switches between the direct and proxy connection
     */
    private synchronized void toggleProxy() {useProxy = !useProxy;
    }

    /**
     * Decrease the number of paused threads by 1
     */
    private synchronized void decrementPause() {
        pausedDownloads--;
    }

    /**
     * Increase the number of paused threads by 1
     */
    private synchronized void incrementPause() {
        pausedDownloads++;
    }

    /**
     * Access the number of currently paused threads
     * @return The number of currently paused download threads
     */
    private synchronized int getPausedDownloads() {return pausedDownloads;
    }

    /**
     * Decrease the number of downloading threads by 1
     */
    private synchronized void decrementCurrentlyDownloading(){ currentlyDownloading--;
    }

    /**
     * Increase the number of downloading threads by 1
     */
    private synchronized void incrementCurrentlyDownloading(){ currentlyDownloading++;
    }

    /**
     * Access the number of currently downloading threads
     * @return The number of currently downloading threads
     */
    private synchronized int getCurrentlyDownloading() {return currentlyDownloading;
    }

    /**
     * Sets all threads' 'paused' value to false and reset ths paused thread counter
     */
    private synchronized void unpauseAll(){ allPaused = false; pausedDownloads = 0;
    }

    /**
     * Sets all threads' 'paused' value to true
     */
    private synchronized void pauseAll() {
        allPaused = true;
        pausedDownloads = currentlyDownloading;
    }

    /**
     * Access whether or not all threads are currently paused
     * @return True if all threads are in a paused state, false otherwise
     */
    private synchronized boolean getAllPaused() {return allPaused;
    }

    /**
     * Access the number of downloads that have failed due to e.g. connection timeouts or API limits
     * @return The number of currently failed downloads
     */
    private synchronized int getFailedDownloads() { return failedDownloads;
    }

    /**
     * Increase the number of failed downloads by 1
     */
    private synchronized void incrementFailedDownloads(){ failedDownloads++;
    }

    /**
     * Reset the number of failed downloads when all downloads are complete
     */
    private synchronized void resetFailedDownloads(){failedDownloads = 0;
    }

    /**
     * Submits a request to AlphaVantage using the API
     * @param request The API URL containing the requested values
     * @return A list of results retrieved from AlphaVantage
     * @throws IOException Thrown if the connection to or reading of the API request/response fails due to server unavailability or connection refusal
     * @see <a href=https://www.alphavantage.co/documentation/>AlphaVantage API Documentation</a>
     */
    ArrayList<String> submitRequest(String request) throws IOException {
        int exceeded = 1;
        ArrayList<String> temp = new ArrayList<>();
        boolean paused = false, failed = false;
        int fails = 0;
        String errMessage = "";


        do {
            availableThreads.acquireUninterruptibly();
            incrementCurrentlyDownloading();

            if(USE_PROXY && getDownloadsSinceToggle() >= 30) {
                Main.getController().updateCurrentTask("SWITCHING PROXY", false, false);
                toggleProxy();
                resetDownloadsSinceToggle();
            }

            int CALL_LIMIT = 2;
            while (paused) {
                if(getAllPaused()){
                    decrementPause();
                    paused = false;

                    if (getAllPaused() && getPausedDownloads() == 0) unpauseAll();
                }

                if (getPausedDownloads() >= Math.min(MAX_CONCURRENT_DOWNLOADS, getCurrentlyDownloading()) && !allPaused)
                    pauseAll();
                try { TimeUnit.SECONDS.sleep(exceeded ); } catch (Exception e) { e.printStackTrace(); }
            }

            URL url = new URL(request);
            HttpURLConnection connection = (HttpURLConnection) (getUseProxy() ? url.openConnection(proxy) : url.openConnection());

            connection.setRequestMethod("GET");

            try {
                TimeUnit.SECONDS.sleep(CALL_LIMIT);
            } catch (Exception e) {
                e.printStackTrace();
            }

            try (InputStream is = connection.getInputStream(); Reader reader = new InputStreamReader(is)) {
                final char[] buf = new char[10240];
                int read;
                final StringBuilder sb = new StringBuilder();
                while ((read = reader.read(buf, 0, buf.length)) > 0) sb.append(buf, 0, read);

                String sTemp = sb.toString();

                if (sTemp.contains("Please consider optimizing your API call frequency.") || sTemp.contains("}") || sTemp.contains("{")) {
                    errMessage = "Too many calls";
                    failed = true;
                } else if (sTemp.contains("Invalid API call")) {
                    errMessage = "Possible invalid API call: " + request;
                    failed = true;
                } else {
                    temp.addAll(Arrays.asList(sTemp.split("\r\n")));
                    if (exceeded != 1) exceeded = 1;
                }
            } catch (Exception e) {
                errMessage = e.toString();
                failed = true;
            } finally {
                connection.disconnect();
            }

            if(failed){
                if(fails == 0)
                    incrementFailedDownloads();
                fails++;
                paused = true;
                incrementPause();

                exceeded++;
                failed = false;
                Main.getController().updateCurrentTask("FAILED " + getFailedDownloads() + " TIMES, RETRYING! STOCK QUOTE CONCURRENT DOWNLOADS - PAUSED: " + getPausedDownloads() + ", QUEUED: " + (getCurrentlyDownloading() - getPausedDownloads()) + "\tTOTAL: " + getCurrentlyDownloading() + " -> " + errMessage, true, false);
            }

            availableThreads.release();
            decrementCurrentlyDownloading();
        } while (exceeded > 1 && exceeded < 10);

        if (paused) decrementPause();

        if (getCurrentlyDownloading() == 0 && availableThreads.availablePermits() == MAX_CONCURRENT_DOWNLOADS)
            resetFailedDownloads();
        if (temp.size() < 100) Main.getController().updateCurrentTask("Error with quote download", true, false);

        incrementDownloadsSinceToggle();

        Main.getController().updateCurrentTask("COMPLETED! STOCK QUOTE CONCURRENT DOWNLOADS - PAUSED: " + getPausedDownloads() + ", QUEUED: " + (getCurrentlyDownloading() - getPausedDownloads()) + "\tTOTAL: " + getCurrentlyDownloading(), false, false);

        return temp;
    }

    /**
     * Access the currently used API Key
     * @return Currently used AlphaVantage API Key
     */
    String getApiKey() {
        return apiKey;
    }
}