import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

public class AlphaVantageHandler {
    private String apiKey;

    public void init(String apiKey){
        this.apiKey = apiKey;
    }

    public ArrayList<String> submitRequest(String request) throws IOException{
        URL url = new URL(request);
        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

        //TODO: Ensure the download is successful
        String sTemp;
        ArrayList<String> temp = new ArrayList<>();

        while((sTemp = reader.readLine()) != null)
            temp.add(sTemp);

        return temp;
    }

    public String getApiKey(){
        return apiKey;
    }
}
