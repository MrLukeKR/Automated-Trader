import java.sql.SQLException;
import java.text.BreakIterator;
import java.util.*;

public class NaturalLanguageProcessor {

    static DatabaseHandler dh;

    static private Set<String> STOP_WORDS = new HashSet<>();

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

    static public String cleanSentence(String sentence, boolean removeBlacklistedWords) {
        //TODO: Stop word, blacklist and punctuation removal
        sentence = sentence.toUpperCase();                                                          //Convert to Upper Case
        sentence = sentence.replaceAll("[^a-zA-Z\\s]", "");                       //Remove non-alphabetic characters
        sentence = sentence.replaceAll("NOT ", "!");                               //Perform logic conversions
        if (removeBlacklistedWords)
            sentence = removeStopWords(sentence);                                                   //Remove blacklisted terms
        sentence = sentence.replaceAll("\\s\\s+", " ");                           //Trim multi-spaces

        return sentence.trim();
    }

    static public void enumerateWordsFromArticles(int n) throws SQLException {
        ArrayList<String> unprocessedIDs = dh.executeQuery("SELECT ID FROM newsarticles WHERE Content IS NOT NULL AND Blacklisted = 0 AND Duplicate = 0 AND Redirected = 0 AND Enumerated = 0");
        for (String unprocessedID : unprocessedIDs) {
            String unprocessed = dh.executeQuery("SELECT Content FROM newsarticles WHERE ID = " + unprocessedID).get(0);
            if (unprocessed != null) {
                ArrayList<String> sentences = splitToSentences(unprocessed, Locale.US);
                ArrayList<String> cSentences = new ArrayList<>();
                ArrayList<String> ngrams = new ArrayList<>();

                for (String sentence : sentences) {
                    String cSentence = cleanSentence(sentence, false);
                    if (cSentence != null) {
                        cSentences.add(cSentence);
                        for (int i = 1; i <= n; i++)
                            if (cSentence.split(" ").length >= n)
                                ngrams.addAll(splitToNGrams(cSentence, Locale.US, i));
                    }
                }

                Set<String> noDuplicateNGrams = new HashSet<>(ngrams);
                Set<String> noDuplicateSentences = new HashSet<>(cSentences);

                for (String ngram : noDuplicateNGrams)
                    if (ngram != null) {
                        dh.executeCommand("INSERT INTO ngrams(Gram, n) VALUES ('" + ngram + "'," + ngram.split(" ").length + ") ON DUPLICATE KEY UPDATE Documents = Documents + 1;");
                        int occurrences = Collections.frequency(ngrams, ngram);
                        dh.executeCommand("UPDATE ngrams SET Occurrences = Occurrences + " + occurrences + " WHERE Gram = '" + ngram + "';");
                    }

                for (String cSentence : noDuplicateSentences)
                    if (cSentence != null && cSentence.split(" ").length > n) {
                        dh.executeCommand("INSERT INTO ngrams(Gram, n) VALUES ('" + cSentence + "'," + cSentence.split(" ").length + ") ON DUPLICATE KEY UPDATE Documents = Documents + 1;");
                        dh.executeCommand("UPDATE ngrams SET Occurrences = Occurrences + " + Collections.frequency(cSentences, cSentence) + " WHERE Gram = '" + cSentence + "';");
                    }
            }

            dh.executeCommand("UPDATE newsarticles SET Enumerated = 1 WHERE ID = " + unprocessedID);
        }
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
