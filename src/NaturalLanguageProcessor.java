import javafx.scene.control.ProgressBar;

import java.sql.SQLException;
import java.text.BreakIterator;
import java.util.*;

public class NaturalLanguageProcessor {

    static DatabaseHandler dh;
    static ProgressBar pb;

    static private Set<String> STOP_WORDS = new HashSet<>();
    static private Set<String> USELESS_SENTENCES = new HashSet<>();

    static public void initialise(DatabaseHandler dbh, ProgressBar nlpProgress) {
        dh = dbh;
        pb = nlpProgress;

        /*
        ArrayList<String> stopWords = dh.executeQuery("SELECT Gram FROM ngrams WHERE Blacklisted = 1");

        if (STOP_WORDS.isEmpty())
            for (String word : stopWords)
                if (!word.isEmpty())
                    STOP_WORDS.add(word);
         */

        System.out.println("Initialised Natural Language Processor");
    }

    static public ArrayList<String> splitToSentences(String document, Locale languageLocale) {
        //https://stackoverflow.com/questions/2687012/split-string-into-sentences
        BreakIterator it = BreakIterator.getSentenceInstance(languageLocale);
        it.setText(document);

        int start = it.first();
        ArrayList<String> sentenceList = new ArrayList<>();

        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next())
            sentenceList.add(document.substring(start, end));


        return sentenceList;
    }

    static public String removeStopWords(String sentence) {
        ArrayList<String> words = new ArrayList<>(Arrays.asList(sentence.split(" ")));

        String cleanSentence = "";

        int i = 1;
        for (String word : words) {
            if (!STOP_WORDS.contains(word)) {
                cleanSentence += word;
                if (i++ < words.size()) cleanSentence += " ";
            }
        }

        return cleanSentence;
    }

    static public String cleanDocument(String document) {
        ArrayList<String> sentences = splitToSentences(document, Locale.US);

        String cleanDocument = "";

        int i = 1;
        for (String sentence : sentences) {
            if (!USELESS_SENTENCES.contains(cleanSentence(sentence, false))) {
                cleanDocument += sentence;
                if (i++ < sentences.size()) cleanDocument += " ";
            }
        }

        return cleanDocument;
    }

    static public String cleanSentence(String sentence, boolean removeBlacklistedWords) {
        sentence = sentence.toUpperCase();                                                          //Convert to Upper Case
        sentence = sentence.replaceAll("[^a-zA-Z\\s]", "");                       //Remove non-alphabetic characters
        sentence = sentence.replaceAll("NOT ", "!");                               //Perform logic conversions
        if (removeBlacklistedWords)
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
                ArrayList<String> sentences = splitToSentences(unprocessed, Locale.US);
                ArrayList<String> cSentences = new ArrayList<>();

                for (String sentence : sentences) {
                    String cSentence = cleanSentence(sentence, false);
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
        dh.executeCommand("UPDATE Sentences SET Blacklisted = 1 WHERE Occurrences > 5;");

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

    static public double getPriceChangeOnDate(String symbol, String date) {
        String truncDate = date.split(" ")[0];
        double priceOnDate = 0, priceOnPrev = 0;

        try {
            priceOnDate = Double.parseDouble(dh.executeQuery("SELECT COALESCE(ClosePrice,0) FROM dailystockprices WHERE Symbol = '" + symbol + "' AND TradeDate >= '" + truncDate + "' ORDER BY TradeDate ASC LIMIT 1;").get(0));
            priceOnPrev = Double.parseDouble(dh.executeQuery("SELECT COALESCE(ClosePrice,0) FROM dailystockprices WHERE Symbol='" + symbol + "' AND TradeDate < '" + truncDate + "' ORDER BY TradeDate DESC LIMIT 1;").get(0)); //TODO: Refactor this to not use as many queries
        } catch (Exception e) {
            e.printStackTrace();
        }

        return (priceOnDate - priceOnPrev) / priceOnDate * 100.0;
    }

    static public void enumerateNGramsFromArticles(int n) throws SQLException {
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Content IS NOT NULL AND Blacklisted = 0 AND Duplicate = 0 AND Redirected = 0 AND Enumerated = 1 AND Tokenised = 0 AND DATE(Published) < SUBDATE(CURDATE(), 1)"); //TODO: Price difference can't be calculated for the weekend or after hours before the next day
        System.out.println("Enumerating n-grams for " + unprocessedIDs.size() + " documents...");

        Controller.updateProgress(ProgressBar.INDETERMINATE_PROGRESS, pb);
        int k = 0, t = unprocessedIDs.size() - 1;

        Map<String, Double[]> temporaryDatabase = new HashMap<>();

        for (String unprocessedID : unprocessedIDs) {
            String unprocessed = dh.executeQuery("SELECT Content FROM newsarticles WHERE ID = " + unprocessedID).get(0);
            if (unprocessed != null) {
                ArrayList<String> sentences = splitToSentences(cleanDocument(unprocessed), Locale.US);
                ArrayList<String> ngrams = new ArrayList<>();

                for (String sentence : sentences) {
                    String cSentence = cleanSentence(sentence, false);
                    if (cSentence != null)
                        for (int i = 1; i <= n; i++)
                            if (cSentence.split(" ").length >= n)
                                ngrams.addAll(splitToNGrams(cSentence, Locale.US, i));
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

            Controller.updateProgress(k++, t, pb);
        }

        sendNGramsToDatabase(temporaryDatabase);
        temporaryDatabase.clear();
        Controller.updateProgress(0, pb);
        System.out.println("Finished processing n-grams");
    }

    static private void sendNGramsToDatabase(Map<String, Double[]> temporaryDatabase) throws SQLException {
        if (temporaryDatabase.isEmpty()) return;

        final int INSERT_SIZE = 30;
        int i = 0;

        dh.setAutoCommit(false);

        String command = "INSERT INTO ngrams(Hash, Gram, n, Documents, Occurrences, Increase, Decrease) VALUES ";
        for (String key : temporaryDatabase.keySet()) {
            if (i % INSERT_SIZE == INSERT_SIZE - 1) {
                command += " ON DUPLICATE KEY UPDATE Documents = Documents + VALUES(Documents), Occurrences = Occurrences + VALUES(Occurrences), Increase = Increase + VALUES(Increase), Decrease = Decrease + VALUES(Decrease)";
                dh.addBatchCommand(command);
                i++;
                command = "INSERT INTO ngrams(Hash, Gram, n, Documents, Occurrences, Increase, Decrease) VALUES ";
            }

            if (i % INSERT_SIZE != 0)
                command += ",";

            i++;

            Double[] values = temporaryDatabase.get(key);

            command += "(MD5('" + key + "'), '" + key + "'," + key.split(" ").length + "," + values[0] + "," + values[1] + "," + values[2] + "," + values[3] + ")";//TODO: Potentially reimplement adding up average change
        }

        command += " ON DUPLICATE KEY UPDATE Documents = Documents + VALUES(Documents), Occurrences = Occurrences + VALUES(Occurrences), Increase = Increase + VALUES(Increase), Decrease = Decrease + VALUES(Decrease)";

        dh.addBatchCommand(command);
        dh.executeBatch();
        dh.setAutoCommit(true);
    }

    static public ArrayList<String> splitToNGrams(ArrayList<String> cleanedSentences, Locale languageLocale, int n) {
        ArrayList<String> ngrams = new ArrayList<>();
        for (String cleanedSentence : cleanedSentences)
            ngrams.addAll(splitToNGrams(cleanedSentence, languageLocale, n));

        return ngrams;
    }

    static public ArrayList<String> splitToNGrams(String cleanedSentence, Locale languageLocale, int n) {
        ArrayList<String> wordList = splitToWords(cleanedSentence);
        ArrayList<String> ngrams = new ArrayList<>();

        if (wordList.size() < n) return null;

        for (int i = 0, j = n - 1; j < wordList.size(); i++, j++) {
            String phrase = wordList.get(i);
            for (int x = i + 1; x <= j; x++)
                phrase += " " + wordList.get(x);

            phrase.replaceAll("[^a-zA-Z\\s]", "");
            ngrams.add(phrase);
        }

        return ngrams;
    }

    static public ArrayList<String> splitToWords(String document) {
        return new ArrayList<>(Arrays.asList(document.split(" ")));
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

            if (sentiment != -1) {
                dh.addBatchCommand("UPDATE newsarticles SET processed = 1, mood = " + sentiment + " WHERE ID = " + id);
                System.out.println("Sentiment for Article ID " + id + ": " + sentiment);
            }
            Controller.updateProgress(curr++, t, pb);
        }

        Controller.updateProgress(0, pb);

        dh.executeBatch();
        dh.setAutoCommit(true);
    }

    static public double evaluateArticleSentiment(int articleID, int ngramSize) throws SQLException {
        String article = dh.executeQuery("SELECT Content FROM newsarticles WHERE ID = " + articleID + ";").get(0);

        double sentiment = 0.5;

        if (article != null) {
            ArrayList<String> sentences = splitToSentences(cleanDocument(article), Locale.US);
            ArrayList<String> ngrams = new ArrayList<>();

            for (String sentence : sentences) {
                String cSentence = cleanSentence(sentence, false);
                if (cSentence != null)
                    for (int i = 1; i <= ngramSize; i++)
                        if (cSentence.split(" ").length >= ngramSize)
                            ngrams.addAll(splitToNGrams(cSentence, Locale.US, i));
            }

            sentences.clear();

            Set<String> noDuplicateNGrams = new HashSet<>(ngrams);

            sentiment = calculateSentiment(new ArrayList<>(noDuplicateNGrams));
        }

        return sentiment;
    }

    static public double calculateSentiment(ArrayList<String> wordList) throws SQLException {
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
