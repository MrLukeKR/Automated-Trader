import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class AlphaVantageHandler {
    private String apiKey;

    private static final int WAIT_PERIOD = 2; //This is used to prevent the server rejecting requests from excessive usage

    public void init(String apiKey){
        this.apiKey = apiKey;
    }

    public ArrayList<String> submitRequest(String request) throws IOException, InterruptedException {
        int exceeded = 1;
        ArrayList<String> temp = new ArrayList<>();

        do {
            URL url = new URL(request);
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            //TODO: 503 Error handling

            String sTemp;

            while ((sTemp = reader.readLine()) != null)
                if (sTemp.contains("Please consider optimizing your API call frequency.") || sTemp.contains("}") || sTemp.contains("{")) {
                    TimeUnit.SECONDS.sleep(exceeded++);
                } else if (sTemp.contains("Invalid API call")) {
                    System.err.println("Possible invalid API call: " + request);
                    TimeUnit.SECONDS.sleep(exceeded++);
                } else {
                    temp.add(sTemp);
                    if (exceeded != 1) exceeded = 1;
                }
        } while (exceeded > 1 && exceeded < 10);

        if (temp.size() <= 2)
            System.err.println("Error with quote download");

        //TimeUnit.SECONDS.sleep(WAIT_PERIOD);

        return temp;
    }

    public String getApiKey(){
        return apiKey;
    }
}
