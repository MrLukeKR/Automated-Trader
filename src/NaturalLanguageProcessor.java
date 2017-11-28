import javafx.scene.control.ProgressBar;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.text.BreakIterator;
import java.util.*;

public class NaturalLanguageProcessor {

    static DatabaseHandler dh;

    static private Set<String> STOP_WORDS = new HashSet<>();
    static private Set<String> USELESS_SENTENCES = new HashSet<>();

    static public void initialise(DatabaseHandler dbh) throws SQLException {
        dh = dbh;

        ArrayList<String> stopWords = dh.executeQuery("SELECT Gram FROM ngrams WHERE Blacklisted = 1");

        if (STOP_WORDS.isEmpty())
            for (String word : stopWords)
                if (!word.isEmpty())
                    STOP_WORDS.add(word);
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
        //TODO: Stop word, blacklist and punctuation removal
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

    static public void enumerateSentencesFromArticles(ProgressBar pb) throws SQLException {
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Content IS NOT NULL AND Blacklisted = 0 AND Duplicate = 0 AND Redirected = 0 AND Enumerated = 0");
        System.out.println("Enumerating sentences for " + unprocessedIDs.size() + " documents...");

        double i = 0, t = unprocessedIDs.size() - 1;

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
                        Integer[] accumulations = {1,Collections.frequency(cSentences,cSentence)};
                        Integer[] existingAccumulations = {0,0};

                        if(temporaryDatabase.containsKey(cSentence))
                            existingAccumulations = temporaryDatabase.get(cSentence);

                        for(int a = 0; a < 2; a++) accumulations[a] += existingAccumulations[a];

                        temporaryDatabase.put(cSentence, accumulations);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
            }

            Controller.updateProgress(i++, t, pb);
        }

        i = 0;
        t = temporaryDatabase.size() - 1;

        for(String key : temporaryDatabase.keySet()){
            Integer[] accumulations = temporaryDatabase.get(key);
            dh.executeCommand("INSERT INTO sentences(Hash, Sentence, Documents, Occurrences) VALUES (MD5('" + key + "'), '" + key + "', '" + accumulations[0] + "','" + accumulations[1] + "') ON DUPLICATE KEY UPDATE Documents = Documents + " + accumulations[0] + ", Occurrences = Occurrences + " + accumulations[1] + ";");

            Controller.updateProgress(i++, t, pb);
        }

        for(String id : unprocessedIDs)
            dh.executeCommand("UPDATE newsarticles SET Enumerated = 1 WHERE ID = '" + id + "';");

    }

    static public void determineUselessSentences() throws SQLException {
        dh.executeCommand("UPDATE Sentences SET Blacklisted = 1 WHERE Occurrences > 1;"); //If not unique, blacklist

        ArrayList<String> uselessSentences = dh.executeQuery("SELECT Sentence FROM sentences WHERE Blacklisted = 1");

        if (USELESS_SENTENCES.isEmpty())
            for (String sentence : uselessSentences)
                if (!sentence.isEmpty())
                    USELESS_SENTENCES.add(sentence);
    }

    static public double getPriceChangeOnDate(String symbol, String date) throws SQLException {
        String truncDate = date.split(" ")[0];

        double priceOnDate = Double.parseDouble(dh.executeQuery("SELECT COALESCE(ClosePrice,0) FROM dailystockprices WHERE Symbol = '" + symbol + "' AND TradeDate >= '" + truncDate + "' ORDER BY TradeDate ASC LIMIT 1;").get(0)),
                priceOnPrev = Double.parseDouble(dh.executeQuery("SELECT COALESCE(ClosePrice,0) FROM dailystockprices WHERE Symbol='" + symbol + "' AND TradeDate < '" + truncDate + "' ORDER BY TradeDate DESC LIMIT 1;").get(0)); //TODO: Refactor this to not use as many queries

        return priceOnDate - priceOnPrev;
    }

    static public void enumerateNGramsFromArticles(int n, ProgressBar pb) throws SQLException, FileNotFoundException {
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Content IS NOT NULL AND Blacklisted = 0 AND Duplicate = 0 AND Redirected = 0 AND Enumerated = 1 AND Tokenised = 0 AND DATE(Published) != CURDATE()");
        System.out.println("Enumerating n-grams for " + unprocessedIDs.size() + " documents...");

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

                Set<String> noDuplicateNGrams = new HashSet<>(ngrams);

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

                for (String ngram : noDuplicateNGrams)
                    if (ngram != null)
                        try {
                            Double[] accumulations = {1.0, (double) Collections.frequency(ngrams, ngram), increase, decrease, increaseAmount, decreaseAmount};
                            Double[] existingAccumulations = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};

                            if (temporaryDatabase.containsKey(ngram))
                                existingAccumulations = temporaryDatabase.get(ngram);

                            for (int a = 0; a < 4; a++) accumulations[a] += existingAccumulations[a];

                            temporaryDatabase.put(ngram, accumulations);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
            }

            Controller.updateProgress(k++, t, pb);
        }

        k = 0;
        t = temporaryDatabase.size() - 1;

        PrintWriter writer = new PrintWriter("res/Ngrams.sql");

        for (String key : temporaryDatabase.keySet()) {
            Double[] values = temporaryDatabase.get(key);
            writer.println("INSERT INTO ngrams(Hash, Gram, n, Documents, Occurrences, Increase, Decrease, IncreaseAmount, DecreaseAmount) VALUES (MD5('" + key + "'), '" + key + "'," + key.split(" ").length + "," + values[0] + "," + values[1] + "," + values[2] + "," + values[3] + "," + values[4] + "," + values[5] + ") ON DUPLICATE KEY UPDATE Documents = Documents + " + Math.round(values[0]) + ", Occurrences = Occurrences + " + Math.round(values[1]) + ", Increase = Increase + " + values[2] + ", Decrease = Decrease + " + values[3] + ", IncreaseAmount = " + values[4] + ", DecreaseAmount = " + values[5] + ";");
            Controller.updateProgress(k++, t, pb);
        }

        for (String unprocessedID : unprocessedIDs)
            writer.println("UPDATE newsarticles SET Tokenised = 1 WHERE ID = " + unprocessedID);

        writer.close();
        System.out.println("Finished processing n-grams");
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
            ngrams.add(phrase);
        }

        return ngrams;
    }

    static public ArrayList<String> splitToWords(String document) {
        return new ArrayList<>(Arrays.asList(document.split(" ")));
    }

    /**
     * @param document - Text to be analysed
     * @return String with all NOT phrases converted to single words e.g. NOT GOOD = !GOOD
     */
    static public String applyLogic(String document) {
        //TODO
        return null;
    }

    static public double calculateSentiment(ArrayList<String> wordList) {
        //TODO
        return 0.0;
    }

    static public void addToPhraseList(String phrase) {
        //TODO
    }
}
