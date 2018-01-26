import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class AlphaVantageHandler {
    private String apiKey;

    public void init(String apiKey){
        this.apiKey = apiKey;
    }

    public ArrayList<String> submitRequest(String request) throws IOException, InterruptedException {
        int exceeded = 0;
        ArrayList<String> temp = new ArrayList<>();

        do {
        URL url = new URL(request);
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

        //TODO: Ensure the download is successful
        String sTemp;

            while ((sTemp = reader.readLine()) != null)
                if (sTemp.contains("Please consider optimizing your API call frequency."))
                    TimeUnit.SECONDS.sleep(++exceeded);
                else {
                    temp.add(sTemp);
                    if (exceeded != 0) exceeded = 0;
                }
        } while (exceeded > 0);

        return temp;
    }

    public String getApiKey(){
        return apiKey;
    }
}
