public class StringProducer {
    private int currentLength;
    private final int maxLength;
    private final int maxChar;
    private final StringBuilder strBuilder;
    private final int[] buffer;
    private final String charset;

    StringProducer(String charset, int maxLength) {
        this.currentLength = 1;
        this.maxLength = maxLength;
        this.strBuilder = new StringBuilder();
        this.charset = charset;
        this.maxChar = charset.length();
        this.buffer = new int[maxLength];
    }

    /// This function returns a String of the next permutation of set 'charset' and 'maxLength' constraints.
    /// If the next permutation is impossible due to the given constraints, returns null.
    public String produceNext() {
        // this is basically just a base N counter
        if (buffer[0] == maxChar) {
            // if current string is last possible permutation, return null
            if (currentLength == maxLength) {
                return null;
            }
            // else, reset all elements < currentLength in buffer, increase currentLength
            for (int i = 0; i < currentLength; i++) {
               buffer[i] = 0;
            }
            currentLength++;
        }
        // reset strBuilder and build new string
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
