public class Utils {
    static public String stringToByteValue(String symbol) {
        String byteString = "";

        for (Byte symbolByte : symbol.getBytes())
            byteString += String.format("%8s", Integer.toBinaryString(symbolByte.byteValue())).replace(' ', '0');

        return byteString;
    }

    static public int stringToNumber(String symbol){
        return stringToByteValue(symbol).hashCode();
    }
}
