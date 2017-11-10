
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Scanner;

public class NewsAPIHandler {
    static final String INTRINIO_API_CALL = "https://api.intrinio.com/news?ticker=";
    static private String INTRINIO_USERNAME;
    static private String INTRINIO_PASSWORD;
    static private final int PAGES = 0, ARTICLES = 1; //Indices for accessing JSON metadata

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

    static public void getNews(String stock, DatabaseHandler dh) throws IOException, SQLException {
        if(isOverLimit(dh)) return;

        int values[] = getNews(stock,dh,1); //getNews returns count of number of pages + latest news, because querying just for the number of pages is a waste of an API credit

        int pageSize = 100; //TODO: Maybe make this variable if necessary
        int storedArticles = Integer.parseInt(dh.executeQuery("SELECT COUNT(*) FROM newsarticles WHERE Symbol='" + stock + "';").get(0));
        int startPage = values[PAGES] - (int) Math.floor(storedArticles / pageSize);

        int i = startPage;

        while(i >= 1 && !isOverLimit(dh))
            getNews(stock, dh, i--);
    }

    static public int getCurrentCalls(DatabaseHandler dh) throws SQLException {
        ArrayList<String> sCalls = dh.executeQuery("SELECT Calls FROM apicalls WHERE Date = CURDATE() AND Name='INTRINIO';");

        int calls = 0;

        if(!sCalls.isEmpty())
            calls = Integer.parseInt(sCalls.get(0));

        return calls;
    }

    static public boolean isOverLimit(DatabaseHandler dh, int callsToPerform) throws SQLException {
        ArrayList<String> sMaxCalls = dh.executeQuery("SELECT DailyLimit FROM apimanagement WHERE Name='INTRINIO';");

        int limit = Integer.parseInt(sMaxCalls.get(0));

        return (callsToPerform + getCurrentCalls(dh)) == limit;
    }

    static public boolean isOverLimit(DatabaseHandler dh) throws SQLException {
        return isOverLimit(dh, getCurrentCalls(dh));
    }

    static public int[] getNews(String stock, DatabaseHandler dh, int page) throws IOException, SQLException {
        int pages = 0;
        int articles = 0;
        URL url = new URL(INTRINIO_API_CALL + stock + "&page_number=" + page);

        if(page == 1)
            System.out.println("Downloading Latest News for " + stock + "...");
        else
            System.out.println("Downloading Historical News (Page " + page + ") for " + stock + "...");

        String doc = null;
        try (InputStream in = url.openStream()) {
            Scanner s = new Scanner(in).useDelimiter(("\\A"));
            doc = s.next();
        }

        dh.executeCommand("INSERT INTO apicalls VALUES ('INTRINIO',CURDATE(), '1') ON UPDATE SET Calls = Calls + 1;");

        try {
            JSONObject obj = new JSONObject(doc);
            JSONArray arr = obj.getJSONArray("data");
            pages = obj.getInt("total_pages");
            articles = obj.getInt("result_count");
            String punctuationRemover = "'";

            for (int i = 0; i < arr.length(); i++) {
                JSONObject ob2 = (JSONObject) arr.get(i);
                String title = ob2.getString("title").replaceAll(punctuationRemover, "");
                String summary = ob2.getString("summary").replaceAll(punctuationRemover, "");
                String date = ob2.getString("publication_date").replaceAll(punctuationRemover, "");
                String link = ob2.getString("url").replaceAll(punctuationRemover, "");
                date = date.split(" ")[0] + " " + date.split(" ")[1];

                String data = "'" + stock + "','" + title + "','" + summary + "','" + date + "','" + link + "'";

                String query = "SELECT * FROM newsarticles WHERE headline = '" + title + "' AND Symbol = '" + stock + "'";

                ArrayList<String> results = dh.executeQuery(query);

                if(results.isEmpty()) {
                    System.out.println(title);
                    String command = "INSERT INTO newsarticles (Symbol, Headline,Description,Published,URL) VALUES (" + data + ");";

                    try {
                        dh.executeCommand(command);
                    }catch (Exception e){ }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        int[] values = {pages,articles};

        return values;
    }

    static public void getNews(ArrayList<String> stockList, DatabaseHandler dh) throws IOException, SQLException {
        for(String symbol : stockList)
            getNews(symbol,dh);
    }
}