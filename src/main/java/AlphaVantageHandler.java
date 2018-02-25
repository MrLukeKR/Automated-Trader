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


class AlphaVantageHandler {
    private final Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 9150));

    private String apiKey;
    private boolean useProxy = false;
    private boolean allPaused = false;
    private int failedDownloads = 0;
    private int pausedDownloads = 0;
    private int currentlyDownloading = 0;
    private final int MAX_CONCURRENT_DOWNLOADS = 30;
    private final int REDUCED_CONCURRENT_DOWNLOADS = 5;
    private int downloadsSinceToggle = 0;
    private int successfulDownloads = 0;
    private final Semaphore availableThreads = new Semaphore(MAX_CONCURRENT_DOWNLOADS, false);

    public void init(String apiKey){
        this.apiKey = apiKey;
    }

    public synchronized void setPauseDownloadThreads(boolean pause){ allPaused = pause;}

    private synchronized int getDownloadsSinceToggle() {return downloadsSinceToggle;}
    private synchronized void incrementDownloadsSinceToggle(){downloadsSinceToggle++;}
    private synchronized void resetDownloadsSinceToggle(){downloadsSinceToggle = 0;}
    private synchronized boolean getUseProxy() {return useProxy;}
    private synchronized void toggleProxy() {useProxy = !useProxy;}

    private synchronized void decrementPause(){ pausedDownloads--; System.out.println("> DECREMENTING PAUSE");}
    private synchronized void incrementPause(){ pausedDownloads++; System.out.println("> INCREMENTING PAUSE");}
    private synchronized int getPausedDownloads() {return pausedDownloads;}
    private synchronized void decrementCurrentlyDownloading(){ currentlyDownloading--;}
    private synchronized void incrementCurrentlyDownloading(){ currentlyDownloading++;}
    private synchronized int getCurrentlyDownloading() {return currentlyDownloading;}
    private synchronized void unpauseAll(){ allPaused = false; pausedDownloads = 0;}
    private synchronized void pauseAll(){allPaused = true;}
    private synchronized boolean getAllPaused() {return allPaused;}
    private synchronized void resetPaused() {pausedDownloads = 0;}
    private synchronized int getFailedDownloads() { return failedDownloads;}
    private synchronized void incrementFailedDownloads(){ failedDownloads++;}
    private synchronized void resetFailedDownloads(){failedDownloads = 0;}
    private synchronized void incrementSuccessfulDownloads(){successfulDownloads++;}
    private synchronized int getSuccessfulDownloads(){return successfulDownloads;}

    public ArrayList<String> submitRequest(String request) throws IOException {
        int exceeded = 1;
        ArrayList<String> temp = new ArrayList<>();
        boolean paused = false, failed = false;
        boolean downloadInitiated = false;
        int fails = 0;
        String errMessage = "";

        availableThreads.acquireUninterruptibly();

        do {
            //try { TimeUnit.SECONDS.sleep(CALL_LIMIT); } catch (Exception e) { e.printStackTrace(); }
            HttpURLConnection connection = null;
            Reader reader = null;
            URL url;
            InputStream is = null;

            if(getDownloadsSinceToggle() >= 30) {
                System.out.println("!---SWITCHING PROXY---!");
                toggleProxy();
                resetDownloadsSinceToggle();
            }

            int CALL_LIMIT = 2;
            while (paused) {
                if(getAllPaused()){
                    decrementPause();
                    paused = false;

                    while(getPausedDownloads() > 0)
                        try{TimeUnit.SECONDS.sleep(CALL_LIMIT); } catch (Exception e) { e.printStackTrace(); }

                    if(getAllPaused() && getPausedDownloads() == 0)
                        unpauseAll();
                }

                if (getPausedDownloads() >= Math.min(MAX_CONCURRENT_DOWNLOADS, getCurrentlyDownloading()) && !allPaused)
                    pauseAll();

                try { TimeUnit.SECONDS.sleep(exceeded ); } catch (Exception e) { e.printStackTrace(); }
            }


            if(!downloadInitiated) {
                incrementCurrentlyDownloading();
                downloadInitiated=true;
            }

            try {

                try { TimeUnit.SECONDS.sleep(CALL_LIMIT); } catch (Exception e) { e.printStackTrace(); }

                url = new URL(request);

                if(getUseProxy())
                    connection = (HttpURLConnection) url.openConnection(proxy);
                else
                    connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                is = connection.getInputStream();
                reader = new InputStreamReader(is);

                final char[] buf = new char[10240];
                int read;
                final StringBuilder sb = new StringBuilder();
                while ((read = reader.read(buf,0,buf.length)) > 0)
                    sb.append(buf, 0, read);

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
            }catch (Exception e){
                errMessage = e.toString();
                failed = true;
            }finally {
                if(is!=null) is.close();
                if(connection != null) connection.disconnect();
                if(reader != null) reader.close();
            }

            if(failed){
                if(fails == 0)
                    incrementFailedDownloads();
                fails++;

                //if(failedDownloads >= getCurrentlyDownloading()) {
                    //setPauseDownloadThreads(true);
                    paused = true;
                    incrementPause();
                //}

                exceeded++;
                failed = false;
                System.err.println("FAILED "+ getFailedDownloads() +" TIMES, RETRYING! STOCK QUOTE CONCURRENT DOWNLOADS - PAUSED: " + getPausedDownloads() + ", QUEUED: " + (getCurrentlyDownloading() - getPausedDownloads()) + "\tTOTAL: " + getCurrentlyDownloading() + "\r\n\t-> " + errMessage);
            }

        } while (exceeded > 1 && exceeded < 10);

        decrementCurrentlyDownloading();

        if(getCurrentlyDownloading() == 0 && availableThreads.availablePermits() == MAX_CONCURRENT_DOWNLOADS)
            resetFailedDownloads();

        if (temp.size() < 100) //Minimum size of a download is 100 records
            System.err.println("Error with quote download");

        incrementSuccessfulDownloads();
        incrementDownloadsSinceToggle();

        System.out.println("COMPLETED! STOCK QUOTE CONCURRENT DOWNLOADS - PAUSED: " + getPausedDownloads() + ", QUEUED: " + (getCurrentlyDownloading() - getPausedDownloads()) + "\tTOTAL: " + getCurrentlyDownloading());
        availableThreads.release();

        return temp;
    }

    public String getApiKey(){
        return apiKey;
    }
}
