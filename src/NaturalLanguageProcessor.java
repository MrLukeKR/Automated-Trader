import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Locale;

public class NaturalLanguageProcessor {

    static DatabaseHandler dh;

    static public void initialise(DatabaseHandler dbh) {
        dh = dbh;
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

    static public ArrayList<String> splitToWords(String document, Locale languageLocale) {
        BreakIterator it = BreakIterator.getWordInstance(languageLocale);
        it.setText(document);

        int start = it.first();
        ArrayList<String> wordList = new ArrayList<>();

        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next())
            wordList.add(document.substring(start, end));

        return wordList;
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
