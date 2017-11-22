import javafx.application.Platform;
import javafx.scene.control.ProgressBar;

import java.sql.SQLException;
import java.text.BreakIterator;
import java.util.*;

public class NaturalLanguageProcessor {

    static DatabaseHandler dh;

    static private Set<String> STOP_WORDS = new HashSet<>();

    static public void initialise(DatabaseHandler dbh) throws SQLException {
        dh = dbh;

        ArrayList<String> uselessSentences = dh.executeQuery("SELECT Sentence FROM sentences WHERE Blacklisted = 1");

        if (STOP_WORDS.isEmpty())
            for (String sentence : uselessSentences)
                if (!sentence.isEmpty())
                    STOP_WORDS.add(sentence);

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

        double i = 0, t = unprocessedIDs.size();
        pb.setVisible(true);

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
                        if (dh.executeQuery("SELECT * FROM sentences WHERE sentence = '" + cSentence + "';").isEmpty())
                            dh.executeCommand("INSERT INTO sentences(Sentence) VALUES ('" + cSentence + "');");

                        dh.executeCommand("UPDATE sentences SET Documents = Documents + 1, Occurrences = Occurrences + " + Collections.frequency(cSentences, cSentence) + " WHERE sentence = '" + cSentence + "';");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
            }

            final double val = i++ / t;
            Platform.runLater(() -> pb.setProgress(val));
            dh.executeCommand("UPDATE newsarticles SET Enumerated = 1 WHERE ID = " + unprocessedID);
        }

        pb.setVisible(false);
    }

    static public void determineUselessSentences() {

    }

    static public void enumerateNGramsFromArticles(int n) throws SQLException {
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Content IS NOT NULL AND Blacklisted = 0 AND Duplicate = 0 AND Redirected = 0 AND Enumerated = 1 AND Tokenised = 0");
        System.out.println("Enumerating n-grams for " + unprocessedIDs.size() + " documents...");
        for (String unprocessedID : unprocessedIDs) {
            String unprocessed = dh.executeQuery("SELECT Content FROM newsarticles WHERE ID = " + unprocessedID).get(0);
            if (unprocessed != null) {
                ArrayList<String> sentences = splitToSentences(unprocessed, Locale.US);
                ArrayList<String> ngrams = new ArrayList<>();

                for (String sentence : sentences) {
                    String cSentence = cleanSentence(sentence, false);
                    if (cSentence != null)
                        for (int i = 1; i <= n; i++)
                            if (cSentence.split(" ").length >= n)
                                ngrams.addAll(splitToNGrams(cSentence, Locale.US, i));
                }

                Set<String> noDuplicateNGrams = new HashSet<>(ngrams);

                for (String ngram : noDuplicateNGrams)
                    if (ngram != null)
                        try {
                            if (dh.executeQuery("SELECT * FROM ngrams WHERE gram = '" + ngram + "');").isEmpty())
                                dh.executeCommand("INSERT INTO ngrams(Gram, n) VALUES ('" + ngram + "'," + ngram.split(" ").length + ");");

                            int occurrences = Collections.frequency(ngrams, ngram);
                            dh.executeCommand("UPDATE ngrams SET Documents = Documents + 1, Occurrences = Occurrences + " + occurrences + " WHERE Gram = '" + ngram + "';");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
            }

            dh.executeCommand("UPDATE newsarticles SET Tokenised = 1 WHERE ID = " + unprocessedID);
        }
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
