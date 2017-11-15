import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class NewsAPIHandler {
    static final String INTRINIO_API_CALL = "https://api.intrinio.com/news?ticker=";
    static final String INTRINIO_CSV_CALL = "https://api.intrinio.com/news.csv?page_size=10000&ticker=";
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

    static public void getHistoricNews(String stock, DatabaseHandler dh) throws IOException, SQLException, JSONException, InterruptedException {
        if(isOverLimit(dh)) return;

        int values[] = getCSVMetaData(stock,dh); //getNews returns count of number of pages + latest news, because querying just for the number of pages is a waste of an API credit

        int pageSize = 10000; //TODO: Maybe make this variable if necessary
        int storedArticles = Integer.parseInt(dh.executeQuery("SELECT COUNT(*) FROM newsarticles WHERE Symbol='" + stock + "';").get(0));
        int startPage = values[PAGES] - (int) Math.floor(storedArticles / pageSize);

        int i = startPage;

        while(i >= 1 && !isOverLimit(dh))
            getCSVNews(stock, dh, i--);
    }

    static public int getCurrentCalls(DatabaseHandler dh) throws SQLException {
        ArrayList<String> sCalls = dh.executeQuery("SELECT Calls FROM apicalls WHERE Date = CURDATE() AND Name='INTRINIO';");

        int calls = 0;

        if(!sCalls.isEmpty())
            calls = Integer.parseInt(sCalls.get(0));

        return calls;
    }

    static public boolean isOverLimit(DatabaseHandler dh, int callsToPerform) throws SQLException {
        int limit = Integer.parseInt(dh.executeQuery("SELECT DailyLimit FROM apimanagement WHERE Name='INTRINIO';").get(0));

        return (callsToPerform + getCurrentCalls(dh)) == limit;
    }

    static public boolean isOverLimit(DatabaseHandler dh) throws SQLException {
        return isOverLimit(dh, 0);
    }

    static public int[] getCSVMetaData(String stock, DatabaseHandler dh) throws JSONException, IOException, SQLException, InterruptedException {
        URL url = new URL(INTRINIO_CSV_CALL + stock);

        TimeUnit.MILLISECONDS.sleep(1000); // To prevent blocking
        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));

        String curr;
        ArrayList<String> csvArray = new ArrayList<>();

        curr=br.readLine();

        String[] splitString = curr.split(",");

        dh.executeCommand("INSERT INTO apicalls VALUES('INTRINIO', CURDATE(), 1) ON DUPLICATE KEY UPDATE Calls = Calls +1;");

        int pages = Integer.parseInt(splitString[3].split(":")[1].trim());
        int articles = Integer.parseInt(splitString[0].split(":")[1].trim());

        int[] values = {pages,articles};

        return values;
    }

    static public void getCSVNews(String stock, DatabaseHandler dh, int page) throws IOException, SQLException, InterruptedException {
        System.out.println("Getting headlines for " + stock + " (Page " + page + ")");
        URL url = new URL(INTRINIO_CSV_CALL + stock + "&page_number=" + page);

        TimeUnit.MILLISECONDS.sleep(1000);
        BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
        String curr;

        ArrayList<String> newsArray = new ArrayList<String>();

        for(int i = 0; i < 2; i++) //Remove preamble
        br.readLine();

        while((curr = br.readLine())!=null)
            newsArray.add(curr);

        br.close();

        for(String news : newsArray){
            String[] splitNews = news.split(",");
            if(splitNews.length == 7) {
                String title = splitNews[3].replaceAll("'", "").replaceAll("\"", "");
                String summary = splitNews[6].replaceAll("'", "");
                String date = splitNews[4].replaceAll("'", "");
                String link = splitNews[5].replaceAll("'", "");
                date = date.split(" ")[0] + " " + date.split(" ")[1];

                String data = "'" + stock + "','" + title + "','" + summary + "','" + date + "','" + link + "'";

                ArrayList<String> duplicate = dh.executeQuery("SELECT * FROM newsarticles WHERE URL ='" + link + "' AND Symbol = '" + stock + "'");

                if (!duplicate.isEmpty()) break;

                System.out.println("Discovered News Article for " + stock + ": " + title);

                ArrayList<String> results = dh.executeQuery("SELECT * FROM newsarticles WHERE Headline = '" + title + "' AND Symbol = '" + stock + "'");
                String command;
                if (results.isEmpty())
                    command = "INSERT INTO newsarticles (Symbol, Headline,Description,Published,URL) VALUES (" + data + ");";
                else
                    command = "INSERT INTO newsarticles (Symbol, Headline,Description,Published,URL, Duplicate) VALUES (" + data + ", 1);";

                try {
                    dh.executeCommand(command);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
    }

    static public void getNews(String stock, DatabaseHandler dh, int page) throws IOException, SQLException {

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

        dh.executeCommand("INSERT INTO apicalls VALUES('INTRINIO', CURDATE(), 1) ON DUPLICATE KEY UPDATE Calls = Calls +1;");

        try {
            JSONObject obj = new JSONObject(doc);
            JSONArray arr = obj.getJSONArray("data");
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
    }

    static public void getHistoricNews(ArrayList<String> stockList, DatabaseHandler dh) throws IOException, SQLException, JSONException, InterruptedException {
        for(String symbol : stockList)
            getHistoricNews(symbol,dh);
    }
}