
import org.json.JSONArray;
import org.json.JSONObject;
import sun.security.util.Password;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.ArrayList;
import java.util.Scanner;

public class NewsAPIHandler {
    static final String INTRINIO_API_CALL = "https://api.intrinio.com/news?ticker=";
    static private String INTRINIO_USERNAME;
    static private String INTRINIO_PASSWORD;


    static public void authenticate(String username, String password){
        INTRINIO_USERNAME = username;
        INTRINIO_PASSWORD = password;

        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(INTRINIO_USERNAME,INTRINIO_PASSWORD.toCharArray());
            }
        });
    }

    static public void getLatestNews(ArrayList<String> stockList, DatabaseHandler dh) throws IOException {
        for(String symbol : stockList) {
            URL url = new URL(INTRINIO_API_CALL + symbol);

            String doc = null;
            try (InputStream in = url.openStream()) {
                Scanner s = new Scanner(in).useDelimiter(("\\A"));
                doc = s.next();
            }

            try {
                JSONObject obj = new JSONObject(doc);
                JSONArray arr = obj.getJSONArray("data");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject ob2 = (JSONObject) arr.get(i);
                    System.out.println(ob2.getString("title"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}