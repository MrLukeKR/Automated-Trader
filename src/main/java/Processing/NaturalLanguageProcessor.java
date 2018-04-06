package Processing;

import Default.Controller;
import Default.DatabaseHandler;
import Default.Main;
import javafx.scene.control.ProgressBar;

import java.sql.SQLException;
import java.text.BreakIterator;
import java.util.*;

/**
 * @author Luke K. Rose <psylr5@nottingham.ac.uk>
 * @version 1.0
 * @since 0.1
 */

public class NaturalLanguageProcessor {

    private static DatabaseHandler dh;
    private static ProgressBar pb;

    /**
     * Initialises the Natural Language Processor with a database handler to prevent deadlocks when accessing the database and a progress bar to visualise any progress made during method calls
     *
     * @param dbh         Database handler
     * @param nlpProgress Natural Language Processor Progress bar
     */
    static public void initialise(DatabaseHandler dbh, ProgressBar nlpProgress) {
        dh = dbh;
        pb = nlpProgress;

        Main.getController().updateCurrentTask("Initialised Natural Language Processor", false, false);
    }

    /**
     * Splits a document into its component sentences
     *
     * @param document HTML document to split into sentences
     * @return A list of sentences
     */
    private static ArrayList<String> splitToSentences(String document) {
        //Based on the code from https://stackoverflow.com/questions/2687012/split-string-into-sentences
        BreakIterator it = BreakIterator.getSentenceInstance(Locale.US);
        it.setText(document);

        int start = it.first();
        ArrayList<String> sentenceList = new ArrayList<>();

        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next())
            sentenceList.add(document.substring(start, end));


        return sentenceList;
    }

    /**
     * Splits a document to sentences and removes any blacklisted sentences
     *
     * @param document HTML Document to clean
     * @return A document that is free of blacklisted sentences
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    private static String cleanDocument(String document) throws SQLException {
        ArrayList<String> sentences = splitToSentences(document);

        StringBuilder cleanDocument = new StringBuilder();

        int i = 1;
        for (String sentence : sentences) {
            if (!isSentenceBlacklisted(cleanSentence(sentence))) {
                cleanDocument.append(sentence);
                if (i++ < sentences.size()) cleanDocument.append(" ");
            }
        }

        return cleanDocument.toString();
    }

    /**
     * Cleans a sentence (removes punctuation, normalises to the same case, performs simple logic conversions [NOT X -> !X] and trims white space)
     *
     * @param sentence Sentence to clean
     * @return Cleaned sentence
     */
    private static String cleanSentence(String sentence) {
        sentence = sentence.toUpperCase();                                                          //Convert to Upper Case
        sentence = sentence.replaceAll("[^a-zA-Z\\s]", "");                       //Remove non-alphabetic characters
        sentence = sentence.replaceAll("NOT ", "!");                               //Perform logic conversions
        sentence = sentence.replaceAll("\\s\\s+", " ");                           //Trim multi-spaces

        if (sentence.isEmpty())
            return null;
        else
            return sentence.trim();
    }

    /**
     * Splits articles to sentences and sends the resultant sentence collection to the database
     *
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void enumerateSentencesFromArticles() throws SQLException {
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Content IS NOT NULL AND Blacklisted = 0 AND Duplicate = 0 AND Redirected = 0 AND Enumerated = 0");
        Main.getController().updateCurrentTask("Enumerating sentences for " + unprocessedIDs.size() + " documents...", false, false);

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


            Controller.updateProgress(++i, t, pb);

            dh.addBatchCommand("UPDATE newsarticles SET Enumerated = 1 WHERE ID = '" + unprocessedID + "';");
            Main.getController().updateCurrentTask("Enumerated " + temporaryDatabase.size() + " sentences", false, false);

            temporaryDatabase.clear();
        }

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);
        dh.executeBatch();
        dh.setAutoCommit(true);
        Controller.updateProgress(0, pb);
    }

    /**
     * Asserts whether or not a sentence has been blacklisted in the database (e.g. because it is spam/not actual news article data)
     *
     * @param sentence Sentence to check for blacklisted status
     * @return True if the sentence has been blacklisted, False otherwise
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static private boolean isSentenceBlacklisted(String sentence) throws SQLException {
        ArrayList<String> result = dh.executeQuery("SELECT COALESCE(Blacklisted, 0) FROM sentences WHERE Hash = MD5('" + sentence + "')");

        return !result.isEmpty() && result.get(0).equals("1");
    }

    /**
     * Calculates the price change between a given date and the last available date (or 0 if considering a newly introduced stock)
     *
     * @param symbol Stock ticker to calculate the price change of
     * @param date   Date to calculate the price change on
     * @return Difference between price on date and nearest date before that date
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
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

    /**
     * Enumerates N-Grams from a document (splits them into n-word tokens)
     *
     * @param n Size of tokens/grams (e.g. n = 2 -> 2 word tokens)
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void enumerateNGramsFromArticles(int n) throws SQLException {
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Content IS NOT NULL AND Blacklisted = 0 AND Duplicate = 0 AND Redirected = 0 AND Enumerated = 1 AND Tokenised = 0 AND PublishedDate < CURDATE()"); //TODO: (Use join) Price difference can't be calculated for the weekend or after hours before the next day
        Main.getController().updateCurrentTask("Enumerating n-grams for " + unprocessedIDs.size() + " documents...", false, false);

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);
        int k = 0, t = unprocessedIDs.size() - 1;

        Map<String, Double[]> temporaryDatabase = new HashMap<>();

        for (String unprocessedID : unprocessedIDs) {
            String unprocessed = dh.executeQuery("SELECT Content FROM newsarticles WHERE ID = " + unprocessedID).get(0);
            if (unprocessed != null) {
                ArrayList<String> ngrams = getNGramsFromSentences(splitToSentences(cleanDocument(unprocessed)),n);

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
                                    accumulations[a] = Double.MAX_VALUE;
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
        Main.getController().updateCurrentTask("Finished processing n-grams", false, false);
    }

    /**
     * Sends a Map of n-gram/price-change values to the database
     *
     * @param temporaryDatabase Map containing n-gram keys associated with total accumulated price-change values
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
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

    /**
     * Splits a sentence into its component N-Grams
     *
     * @param cleanedSentence Sentence to split into N-Grams
     * @param n               Size of grams/tokens (e.g. n=2 -> 2 word tokens)
     * @return List of n-grams
     */
    private static ArrayList<String> splitToNGrams(String cleanedSentence, int n) {
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

    /**
     * Splits a document into single words
     *
     * @param document Document to split
     * @return A list of words
     */
    private static ArrayList<String> splitToWords(String document) {
        return new ArrayList<>(Arrays.asList(document.split(" ")));
    }

    /**
     * Calculates the average news sentiment for Today
     *
     * @param stock     Stock to calculate the average news sentiment for
     * @param ngramSize Size of n-grams to consider
     * @return Average sentiment of news articles published on Today's date
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public double getTodaysAverageSentiment(String stock, int ngramSize) throws SQLException {
        String latestDate = dh.executeQuery("SELECT MAX(TradeDate) FROM dailystockprices WHERE Symbol='" + stock + "'").get(0);
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Symbol='" + stock + "' AND PublishedDate = '" + latestDate +"' AND Content IS NOT NULL AND Enumerated = 1 AND Tokenised = 1 AND Processed = 0 AND Blacklisted = 0");

        if(unprocessedIDs.isEmpty())
            return 0.5;

        double sentiment = 0;

        for (String id : unprocessedIDs)
            sentiment += evaluateArticleSentiment(Integer.valueOf(id), ngramSize);

        return sentiment / unprocessedIDs.size();
    }

    /**
     * Calculates the sentiment values for all unprocessed articles
     *
     * @param ngramSize Largest size of n-grams to process up to
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public void processArticlesForSentiment(int ngramSize) throws SQLException {
        Main.getController().updateCurrentTask("Processing Sentiment of Articles", false, false);

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
            Main.getController().updateCurrentTask("Sentiment for Article ID " + id + ": " + sentiment,false,false);

            Controller.updateProgress(++curr, t, pb);
        }

        Controller.updateProgress(0, pb);

        dh.executeBatch();
        dh.setAutoCommit(true);
    }

    /**
     * Gets the average per-day sentiments of a given stock, over the past n records
     *
     * @param stock Stock to retrieve the sentiment data for
     * @param size  Amount of days to gather the average sentiment for
     * @return Array of average sentiment over n days
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public double[] getAverageSentiments(String stock, int size) throws SQLException {
        ArrayList<String> results = dh.executeQuery("SELECT COALESCE(AVG(newsarticles.Mood), 0.5) FROM dailystockprices LEFT JOIN newsarticles ON (dailystockprices.Symbol, dailystockprices.TradeDate) = (newsarticles.Symbol, newsarticles.PublishedDate) WHERE dailystockprices.Symbol='" + stock + "' GROUP BY dailystockprices.TradeDate ORDER BY dailystockprices.TradeDate ASC");

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

    /**
     * Retrieves the average sentiment for a given stock on a given date
     *
     * @param stock Stock to retrieve the average sentiment for
     * @param date  Date to retrieve the sentiments of
     * @return Average sentiment over all news articles on a given date
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    static public double getAverageSentimentOnDate(String stock, String date) throws SQLException {
        ArrayList<String> result = dh.executeQuery("SELECT COALESCE(AVG(Mood),0.5) FROM newsarticles WHERE Symbol = '" + stock + "' AND PublishedDate = '" + date + "' AND Processed = 1");

        if (result == null || result.isEmpty())
            return 0.5;
        else
            return Double.parseDouble(result.get(0));
    }

    /**
     * Enumerates all n-grams within a list of sentences
     *
     * @param sentences Sentences to extract the n-grams from
     * @param ngramSize Largest size of gram to enumerate
     * @return List of n-grams
     */
    private static ArrayList<String> getNGramsFromSentences(ArrayList<String> sentences, int ngramSize){
        ArrayList<String> ngrams = new ArrayList<>();

        for (String sentence : sentences) {
            String cSentence = cleanSentence(sentence);
            if (cSentence != null)
                for (int i = 1; i <= ngramSize; i++)
                    if (cSentence.split(" ").length >= ngramSize)
                        ngrams.addAll(Objects.requireNonNull(splitToNGrams(cSentence, i)));
        }

        return ngrams;
    }

    /**
     * Calculates the sentiment of an article, given its unique ID
     *
     * @param articleID Unique article ID
     * @param ngramSize Largest size of gram to consider
     * @return Article sentiment
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    private static double evaluateArticleSentiment(int articleID, int ngramSize) throws SQLException {
        String article = dh.executeQuery("SELECT Content FROM newsarticles WHERE ID = " + articleID + ";").get(0);

        double sentiment = 0.5;

        if (article != null) {
            ArrayList<String> ngrams = getNGramsFromSentences(splitToSentences(cleanDocument(article)),ngramSize);
            Set<String> noDuplicateNGrams = new HashSet<>(ngrams);
            sentiment = calculateSentiment(new ArrayList<>(noDuplicateNGrams));
        }

        return sentiment;
    }

    /**
     * Calculates the sentiment of a list of words
     *
     * @param wordList List of n-grams to gather the sentiments for
     * @return Average sentiment over the entire given list of n-grams
     * @throws SQLException Throws SQLException if there is an error with accessing the MySQL/MariaDB database
     */
    private static double calculateSentiment(ArrayList<String> wordList) throws SQLException {
        double totalSentiment = 0;

        if (wordList.isEmpty())
            return -1;

        for (String word : wordList) {
            ArrayList<String> results = dh.executeQuery("SELECT Increase, Decrease FROM ngrams WHERE Hash=MD5('" + word + "');");

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