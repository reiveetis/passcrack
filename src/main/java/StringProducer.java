import java.math.BigInteger;

public class StringProducer {
    private int currentLength;
    private final int maxLength;
    private final int maxChar;
    private final StringBuilder strBuilder;
    private final int[] buffer;
    private final String charset;
    private char[] mask = null;
    private BigInteger allPerms = BigInteger.ZERO;
    private BigInteger currPerm = BigInteger.ZERO;

    public StringProducer(String charset, int minLength, int maxLength) {
        if (maxLength < minLength) {
            throw new IllegalArgumentException("maxLength must be greater than minLength");
        }
        if (minLength <= 0) {
            throw new IllegalArgumentException("minLength must be greater than zero");
        }
        this.currentLength = minLength;
        this.maxLength = maxLength;
        this.strBuilder = new StringBuilder();
        this.charset = charset;
        this.maxChar = charset.length();
        this.buffer = new int[maxLength];
        calculateAllPerms();
    }

    public StringProducer(String charset, String mask, char maskCh) {
        this.mask = mask.toCharArray();
        int unknownCount = 0;
        for (int i = 0; i < this.mask.length; i++) {
            if (this.mask[i] == maskCh) {
                this.mask[i] = 0;
                unknownCount++;
            }
        }
        this.maxLength = unknownCount;
        this.currentLength = maxLength;
        this.strBuilder = new StringBuilder();
        this.charset = charset;
        this.maxChar = charset.length();
        this.buffer = new int[maxLength];
        calculateAllPerms();
    }

    private void calculateAllPerms() {
        for (int i = currentLength; i <= maxLength; i++) {
            allPerms = allPerms.add(BigInteger.valueOf(maxChar).pow(i));
        }
    }

    public BigInteger getCurrPerm() {
        return currPerm;
    }

    public BigInteger getProgress() {
        return currPerm;
    }

    public BigInteger getAllPermutations() {
        return allPerms;
    }

    /// This function returns a String of the next permutation of set 'charset' and 'maxLength' constraints.
    /// If the next permutation is impossible due to the given constraints, returns null.
    public String produceNext() {
        // this is basically just a base N counter
        if (mask != null && currentLength == 0) {
            return null;
        }
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
        if (mask == null) {
            // maskless branch
            for (int i = 0; i < currentLength; i++) {
                strBuilder.append(charset.charAt(buffer[i]));
            }
        } else {
            // mask branch
            int curr = 0;
            for (char c : mask) {
                if (c == 0) {
                    strBuilder.append(charset.charAt(buffer[curr]));
                    curr++;
                    continue;
                }
                strBuilder.append(c);
            }
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
        currPerm = currPerm.add(BigInteger.ONE);
        return strBuilder.toString();
    }
}
