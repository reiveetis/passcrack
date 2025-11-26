public class StringProducer {
    private int currentLength;
    private final int length;
    private final int maxChar;
    private final StringBuilder strBuilder;
    private final int[] buffer;
    private final String charset;

    StringProducer(String charset, int length) {
        this.currentLength = 1;
        this.length = length;
        this.strBuilder = new StringBuilder();
        this.charset = charset;
        this.maxChar = charset.length();
        this.buffer = new int[length];
    }

    /// This function returns a String of the next permutation of set 'charset' and 'length' constraints.
    /// If the next permutation is impossible due to the given constraints, returns null
    public String produceNext() {
        if (buffer[0] == maxChar) {
            // if current string is last permutation, return null
            if (currentLength == length) {
                return null;
            }
            // else, reset all elements < currentLength, increase currentLength
            for (int i = 0; i < currentLength; i++) {
               buffer[i] = 0;
            }
            currentLength++;
        }
        strBuilder.setLength(0);
        for (int i = 0; i < currentLength; i++) {
            strBuilder.append(charset.charAt(buffer[i]));
        }
        // increment last element
        buffer[currentLength - 1]++;
        // check for carry over
        short carryOffset = 1;
        while (buffer[currentLength - carryOffset] == maxChar) {
            // if carry over index out of bounds, leave
            if (carryOffset == currentLength) break;
            // set current to 0
            buffer[currentLength - carryOffset] = 0;
            // increment prev
            carryOffset++;
            buffer[currentLength - carryOffset]++;
        }
        return strBuilder.toString();
    }
}
