import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class AlphaVantageHandler {
    private String apiKey;

    private final int CALL_LIMIT = 1;

    public void init(String apiKey){
        this.apiKey = apiKey;
    }

    public ArrayList<String> submitRequest(String request) throws IOException, InterruptedException {
        int exceeded = 1;
        ArrayList<String> temp = new ArrayList<>();

        do {
            TimeUnit.SECONDS.sleep(CALL_LIMIT);

            URL url = new URL(request);
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
            //TODO: 503 Error handling

            String sTemp;

            while ((sTemp = reader.readLine()) != null) //TODO: Premature EOF handling
                if (sTemp.contains("Please consider optimizing your API call frequency.") || sTemp.contains("}") || sTemp.contains("{")) {
                    TimeUnit.SECONDS.sleep(exceeded++);
                } else if (sTemp.contains("Invalid API call")) {
                    System.err.println("Possible invalid API call: " + request);
                    TimeUnit.SECONDS.sleep(exceeded++);
                } else {
                    temp.add(sTemp);
                    if (exceeded != 1) exceeded = 1;
                }
            reader.close();
        } while (exceeded > 1 && exceeded < 5);


        if (temp.size() <= 2)
            System.err.println("Error with quote download");

        return temp;
    }

    public String getApiKey(){
        return apiKey;
    }
}
