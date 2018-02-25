import javafx.scene.control.ProgressBar;

import java.sql.SQLException;
import java.text.BreakIterator;
import java.util.*;

class NaturalLanguageProcessor {

    private static DatabaseHandler dh;
    private static ProgressBar pb;

    static private final Set<String> STOP_WORDS = new HashSet<>();
    static private final Set<String> USELESS_SENTENCES = new HashSet<>();

    static public void initialise(DatabaseHandler dbh, ProgressBar nlpProgress) throws SQLException {
        dh = dbh;
        pb = nlpProgress;


        ArrayList<String> stopWords = dh.executeQuery("SELECT Gram FROM ngrams WHERE Blacklisted = 1");

        if (STOP_WORDS.isEmpty())
            for (String word : stopWords)
                if (!word.isEmpty())
                    STOP_WORDS.add(word);


        System.out.println("Initialised Natural Language Processor");
    }

    private static ArrayList<String> splitToSentences(String document) {
        //https://stackoverflow.com/questions/2687012/split-string-into-sentences
        BreakIterator it = BreakIterator.getSentenceInstance(Locale.US);
        it.setText(document);

        int start = it.first();
        ArrayList<String> sentenceList = new ArrayList<>();

        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next())
            sentenceList.add(document.substring(start, end));


        return sentenceList;
    }

    private static String removeStopWords(String sentence) {
        ArrayList<String> words = new ArrayList<>(Arrays.asList(sentence.split(" ")));

        StringBuilder cleanSentence = new StringBuilder();

        int i = 1;
        for (String word : words) {
            if (!STOP_WORDS.contains(word)) {
                cleanSentence.append(word);
                if (i++ < words.size()) cleanSentence.append(" ");
            }
        }

        return cleanSentence.toString();
    }

    private static String cleanDocument(String document) {
        ArrayList<String> sentences = splitToSentences(document);

        StringBuilder cleanDocument = new StringBuilder();

        int i = 1;
        for (String sentence : sentences) {
            if (!USELESS_SENTENCES.contains(cleanSentence(sentence))) {
                cleanDocument.append(sentence);
                if (i++ < sentences.size()) cleanDocument.append(" ");
            }
        }

        return cleanDocument.toString();
    }

    private static String cleanSentence(String sentence) {
        sentence = sentence.toUpperCase();                                                          //Convert to Upper Case
        sentence = sentence.replaceAll("[^a-zA-Z\\s]", "");                       //Remove non-alphabetic characters
        sentence = sentence.replaceAll("NOT ", "!");                               //Perform logic conversions
        sentence = removeStopWords(sentence);                                                   //Remove blacklisted terms
        sentence = sentence.replaceAll("\\s\\s+", " ");                           //Trim multi-spaces

        if (sentence.isEmpty())
            return null;
        else
            return sentence.trim();
    }

    static public void enumerateSentencesFromArticles() throws SQLException {
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Content IS NOT NULL AND Blacklisted = 0 AND Duplicate = 0 AND Redirected = 0 AND Enumerated = 0");
        System.out.println("Enumerating sentences for " + unprocessedIDs.size() + " documents...");

        double i = 0, t = unprocessedIDs.size() - 1;

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);

        dh.setAutoCommit(false);

        Map<String, Integer[]> temporaryDatabase = new HashMap<>();

        for (String unprocessedID : unprocessedIDs) {
            String unprocessed = dh.executeQuery("SELECT Content FROM newsarticles WHERE ID = " + unprocessedID).get(0);
            if (unprocessed != null) {
                ArrayList<String> sentences = splitToSentences(unprocessed);
                ArrayList<String> cSentences = new ArrayList<>();

                for (String sentence : sentences) {
                    String cSentence = cleanSentence(sentence);
                    if (cSentence != null)
                        cSentences.add(cSentence);
                }

                Set<String> noDuplicateSentences = new HashSet<>(cSentences);
                for (String cSentence : noDuplicateSentences)
                    try {
                        Integer[] accumulations = {1, Collections.frequency(cSentences, cSentence)};
                        Integer[] existingAccumulations = {0, 0};

                        if (temporaryDatabase.containsKey(cSentence))
                            existingAccumulations = temporaryDatabase.get(cSentence);

                        for (int a = 0; a < 2; a++) accumulations[a] += existingAccumulations[a];

                        temporaryDatabase.put(cSentence, accumulations);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
            }

            for (String key : temporaryDatabase.keySet()) {
                Integer[] accumulations = temporaryDatabase.get(key);
                dh.addBatchCommand("INSERT INTO sentences(Hash, Sentence, Documents, Occurrences) VALUES (MD5('" + key + "'), '" + key + "', '" + accumulations[0] + "','" + accumulations[1] + "') ON DUPLICATE KEY UPDATE Documents = Documents + " + accumulations[0] + ", Occurrences = Occurrences + " + accumulations[1] + ";");
            }


            Controller.updateProgress(i++, t, pb);

            dh.addBatchCommand("UPDATE newsarticles SET Enumerated = 1 WHERE ID = '" + unprocessedID + "';");
            System.out.println("Enumerated " + temporaryDatabase.size() + " sentences");
            dh.executeBatch();

            temporaryDatabase.clear();
        }

        Controller.updateProgress(0, pb);
        dh.setAutoCommit(true);
    }

    static public void determineUselessSentences() throws SQLException {
        dh.executeCommand("UPDATE sentences SET Blacklisted = 1 WHERE Occurrences > 5;");

        ArrayList<String> uselessSentences = dh.executeQuery("SELECT Sentence FROM sentences WHERE Blacklisted = 1");

        if (USELESS_SENTENCES.isEmpty())
            for (String sentence : uselessSentences)
                if (!sentence.isEmpty())
                    USELESS_SENTENCES.add(sentence);
    }

    static public void determineUselessNGrams() throws SQLException {
        int mostCommonNGram = Integer.valueOf(dh.executeQuery("SELECT MAX(Documents) FROM ngrams").get(0));

        //TODO: Remove useless Ngrams
    }

    private static double getPriceChangeOnDate(String symbol, String date) throws SQLException {
        String truncDate = date.split(" ")[0];
        double priceOnDate = 0, priceOnPrev = 0;

        ArrayList<String> result1 = dh.executeQuery("SELECT COALESCE(ClosePrice,0) FROM dailystockprices WHERE Symbol = '" + symbol + "' AND TradeDate >= '" + truncDate + "' ORDER BY TradeDate ASC LIMIT 1;");
        ArrayList<String> result2 = dh.executeQuery("SELECT COALESCE(ClosePrice,0) FROM dailystockprices WHERE Symbol='" + symbol + "' AND TradeDate < '" + truncDate + "' ORDER BY TradeDate DESC LIMIT 1;");

        try {
            if (!result1.isEmpty())
                priceOnDate = Double.parseDouble(result1.get(0));
            if (!result2.isEmpty())
                priceOnPrev = Double.parseDouble(result2.get(0));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return (priceOnDate - priceOnPrev) / priceOnDate * 100.0;
    }

    static public void enumerateNGramsFromArticles(int n) throws SQLException {
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Content IS NOT NULL AND Blacklisted = 0 AND Duplicate = 0 AND Redirected = 0 AND Enumerated = 1 AND Tokenised = 0 AND DATE(Published) < CURDATE()"); //TODO: (Use join) Price difference can't be calculated for the weekend or after hours before the next day
        System.out.println("Enumerating n-grams for " + unprocessedIDs.size() + " documents...");

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);
        int k = 0, t = unprocessedIDs.size() - 1;

        Map<String, Double[]> temporaryDatabase = new HashMap<>();

        for (String unprocessedID : unprocessedIDs) {
            String unprocessed = dh.executeQuery("SELECT Content FROM newsarticles WHERE ID = " + unprocessedID).get(0);
            if (unprocessed != null) {
                ArrayList<String> sentences = splitToSentences(cleanDocument(unprocessed));
                ArrayList<String> ngrams = new ArrayList<>();

                for (String sentence : sentences) {
                    String cSentence = cleanSentence(sentence);
                    if (cSentence != null)
                        for (int i = 1; i <= n; i++)
                            if (cSentence.split(" ").length >= n)
                                ngrams.addAll(Objects.requireNonNull(splitToNGrams(cSentence, Locale.US, i)));
                }

                sentences.clear();

                String[] symbolAndDate = dh.executeQuery("SELECT Symbol, Published FROM newsarticles WHERE ID = " + unprocessedID).get(0).split(",");

                double priceChange = getPriceChangeOnDate(symbolAndDate[0], symbolAndDate[1]); //TODO: Refactor this to not use as many queries
                double increase = 0, decrease = 0, increaseAmount = 0, decreaseAmount = 0;

                if (priceChange < 0) {
                    decrease = 1;
                    decreaseAmount = Math.abs(priceChange);
                } else {
                    increase = 1;
                    increaseAmount = priceChange;
                }

                Set<String> noDuplicateNGrams = new HashSet<>(ngrams);

                for (String ngram : noDuplicateNGrams)
                    if (ngram != null)
                        try {
                            Double[] accumulations = {1.0, (double) Collections.frequency(ngrams, ngram), increase, decrease, increaseAmount, decreaseAmount};
                            Double[] existingAccumulations = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};

                            if (temporaryDatabase.containsKey(ngram))
                                existingAccumulations = temporaryDatabase.get(ngram);

                            for (int a = 0; a < 6; a++) {
                                accumulations[a] += existingAccumulations[a];
                                if (accumulations[a] == Double.NaN) accumulations[a] = 0.0;
                                if (accumulations[a] == Double.POSITIVE_INFINITY)
                                    accumulations[a] = Double.MAX_VALUE; //TODO: Maybe don't use this method for determining an ngrams impact
                            }

                            temporaryDatabase.put(ngram, accumulations);

                            if (temporaryDatabase.size() >= 100000) {
                                sendNGramsToDatabase(temporaryDatabase);
                                temporaryDatabase.clear();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                dh.addBatchCommand("UPDATE newsarticles SET Tokenised = 1 WHERE ID = " + unprocessedID + ";");

                ngrams.clear();
                noDuplicateNGrams.clear();
            }

            Controller.updateProgress(++k, t, pb);
        }

        sendNGramsToDatabase(temporaryDatabase);
        Controller.updateProgress(0, pb);
        System.out.println("Finished processing n-grams");
    }

    static private void sendNGramsToDatabase(Map<String, Double[]> temporaryDatabase) throws SQLException {
        if (temporaryDatabase.isEmpty()) return;

        final int INSERT_SIZE = 30;
        int i = 0;

        dh.setAutoCommit(false);

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);

        StringBuilder command = new StringBuilder("INSERT INTO ngrams(Hash, Gram, n, Documents, Occurrences, Increase, Decrease) VALUES ");
        for (String key : temporaryDatabase.keySet()) {
            if (i % INSERT_SIZE == INSERT_SIZE - 1) {
                command.append(" ON DUPLICATE KEY UPDATE Documents = Documents + VALUES(Documents), Occurrences = Occurrences + VALUES(Occurrences), Increase = Increase + VALUES(Increase), Decrease = Decrease + VALUES(Decrease)");
                dh.addBatchCommand(command.toString());
                i++;
                command = new StringBuilder("INSERT INTO ngrams(Hash, Gram, n, Documents, Occurrences, Increase, Decrease) VALUES ");
            }

            if (i % INSERT_SIZE != 0)
                command.append(",");

            i++;

            Double[] values = temporaryDatabase.get(key);

            command.append("(MD5('").append(key).append("'), '").append(key).append("',").append(key.split(" ").length).append(",").append(values[0]).append(",").append(values[1]).append(",").append(values[2]).append(",").append(values[3]).append(")");
        }

        command.append(" ON DUPLICATE KEY UPDATE Documents = Documents + VALUES(Documents), Occurrences = Occurrences + VALUES(Occurrences), Increase = Increase + VALUES(Increase), Decrease = Decrease + VALUES(Decrease)");

        dh.addBatchCommand(command.toString());
        dh.executeBatch();
        dh.setAutoCommit(true);
    }

    static public ArrayList<String> splitToNGrams(ArrayList<String> cleanedSentences, Locale languageLocale, int n) {
        ArrayList<String> ngrams = new ArrayList<>();
        for (String cleanedSentence : cleanedSentences)
            ngrams.addAll(Objects.requireNonNull(splitToNGrams(cleanedSentence, languageLocale, n)));

        return ngrams;
    }

    private static ArrayList<String> splitToNGrams(String cleanedSentence, Locale languageLocale, int n) {
        ArrayList<String> wordList = splitToWords(cleanedSentence);
        ArrayList<String> ngrams = new ArrayList<>();

        if (wordList.size() < n) return null;

        for (int i = 0, j = n - 1; j < wordList.size(); i++, j++) {
            StringBuilder phrase = new StringBuilder(wordList.get(i));
            for (int x = i + 1; x <= j; x++)
                phrase.append(" ").append(wordList.get(x));

            ngrams.add(phrase.toString().replaceAll("[^a-zA-Z\\s]", ""));
        }

        return ngrams;
    }

    private static ArrayList<String> splitToWords(String document) {
        return new ArrayList<>(Arrays.asList(document.split(" ")));
    }

    static public double getTodaysAverageSentiment(String stock, int ngramSize) throws SQLException {
        String latestDate = dh.executeQuery("SELECT MAX(TradeDate) FROM dailystockprices WHERE Symbol='" + stock + "'").get(0);
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Symbol='" + stock + "' AND DATE(Published) = '" + latestDate +"' AND Content IS NOT NULL AND Enumerated = 1 AND Tokenised = 1 AND Processed = 0 AND Blacklisted = 0");

        if(unprocessedIDs.isEmpty())
            return 0.5;

        double sentiment = 0;

        for (String id : unprocessedIDs)
            sentiment += evaluateArticleSentiment(Integer.valueOf(id), ngramSize);

        return sentiment / unprocessedIDs.size();
    }

    static public void processArticlesForSentiment(int ngramSize) throws SQLException {
        System.out.println("Processing Sentiment of Articles");


        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);
        dh.setAutoCommit(false);
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Content IS NOT NULL AND Enumerated = 1 AND Tokenised = 1 AND Processed = 0 AND Blacklisted = 0");

        final double t = unprocessedIDs.size() - 1;
        double curr = 0;

        for (String id : unprocessedIDs) {
            double sentiment = evaluateArticleSentiment(Integer.valueOf(id), ngramSize);

            if (sentiment == -1)
                sentiment = 0.5;

            dh.addBatchCommand("UPDATE newsarticles SET processed = 1, mood = " + sentiment + " WHERE ID = " + id);
            System.out.println("Sentiment for Article ID " + id + ": " + sentiment);


            Controller.updateProgress(++curr, t, pb);
        }

        Controller.updateProgress(0, pb);

        dh.executeBatch();
        dh.setAutoCommit(true);
    }

    static public void enumerateSentiments(String stock) throws SQLException {
        ArrayList<String> dates = dh.executeQuery("SELECT TradeDate FROM dailystockprices WHERE Sentiment is null Symbol='" + stock + "' AND TradeDate < CURRENT_DATE ORDER BY TradeDate ASC");
        TreeMap<String, Double> sentiments = new TreeMap<>();

        for(String date : dates)
            sentiments.put(date, getAverageSentimentOnDate(stock, date));

        dh.setAutoCommit(false);
        for(String date : sentiments.keySet())
            dh.addBatchCommand("UPDATE dailystockprices SET Sentiment = " + sentiments.get(date) + " WHERE TradeDate = '" + date + "';");

        dh.executeBatch();
        dh.setAutoCommit(true);
    }

    static public double[] getAverageSentiments(String stock, int size) throws SQLException {
        ArrayList<String> results = dh.executeQuery("SELECT COALESCE(AVG(newsarticles.Mood), 0.5) FROM dailystockprices LEFT JOIN newsarticles ON (dailystockprices.Symbol, dailystockprices.TradeDate) = (newsarticles.Symbol, DATE(newsarticles.Published)) WHERE dailystockprices.Symbol='" + stock + "' GROUP BY dailystockprices.TradeDate ORDER BY dailystockprices.TradeDate ASC");

        double[] sentiments = new double[size];

        int i = 0;

        int startPoint = results.size() - size;

        for(String result : results) {
            if(i >= startPoint)
                sentiments[i - startPoint] = Double.parseDouble(result);
            i++;
        }

        return sentiments;
    }

    static private double getAverageSentimentOnDate(String stock, String date) throws SQLException {
        ArrayList<String> result = dh.executeQuery("SELECT COALESCE(AVG(Mood),0.5) FROM newsarticles WHERE Symbol = '" + stock + "' AND DATE(Published) = '" + date + "' AND Processed = 1");

        if (result == null || result.isEmpty())
            return 0.5;
        else
            return Double.parseDouble(result.get(0));
    }

    private static double evaluateArticleSentiment(int articleID, int ngramSize) throws SQLException {
        String article = dh.executeQuery("SELECT Content FROM newsarticles WHERE ID = " + articleID + ";").get(0);

        double sentiment = 0.5;

        if (article != null) {
            ArrayList<String> sentences = splitToSentences(cleanDocument(article));
            ArrayList<String> ngrams = new ArrayList<>();

            for (String sentence : sentences) {
                String cSentence = cleanSentence(sentence);
                if (cSentence != null)
                    for (int i = 1; i <= ngramSize; i++)
                        if (cSentence.split(" ").length >= ngramSize)
                            ngrams.addAll(Objects.requireNonNull(splitToNGrams(cSentence, Locale.US, i)));
            }

            sentences.clear();

            Set<String> noDuplicateNGrams = new HashSet<>(ngrams);

            sentiment = calculateSentiment(new ArrayList<>(noDuplicateNGrams));
        }

        return sentiment;
    }

    private static double calculateSentiment(ArrayList<String> wordList) throws SQLException {
        double totalSentiment = 0;

        if (wordList.isEmpty())
            return -1;

        for (String word : wordList) {
            ArrayList<String> results = dh.executeQuery("SELECT Increase, Decrease FROM ngrams WHERE Hash=MD5('" + word + "');"); //TODO: This enumerates beyond tokenised / enumerated articles (i.e. articles that have not yet been analysed)

            if (!results.isEmpty()) {
                String result = results.get(0);

                String[] splitResult = result.split(",");
                double increase = Double.parseDouble(splitResult[0]), decrease = Integer.parseInt(splitResult[1]);
                double sentiment = increase / (increase + decrease);

                totalSentiment += sentiment;
            }
        }

        return totalSentiment / wordList.size();
    }
}
