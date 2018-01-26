public class Utils {
    static public String stringToByteValue(String symbol) {
        String byteString = "";

        for (Byte symbolByte : symbol.getBytes())
            byteString += String.format("%8s", Integer.toBinaryString(symbolByte.byteValue())).replace(' ', '0');

        return '1' + byteString; //Put 1 in-front of string to act as an integer (i.e. starting with a 0 is disallowed)
    }
}
