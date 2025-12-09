import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class StringProducer {
    private static final int PERCENT = 100;
    private static final int DECIMAL_POINTS = 1;
    private static final int PREFERRED_PRECISION = 2;
    // we're going to lose precision while trying to convert fraction to percentage
    private static final int ACTUAL_PRECISION = PREFERRED_PRECISION + (int)Math.log10(PERCENT);

    private int currentLength;
    private final int maxLength;
    private final int maxChar;
    private final StringBuilder strBuilder;
    private final int[] buffer;
    private final String charset;
    private char[] mask = null;

    private BigDecimal allPerms = BigDecimal.ZERO;
    private BigDecimal currPerm = BigDecimal.ZERO;
    private BigDecimal chunkSize = BigDecimal.ZERO;
    private BigDecimal chunkCounter = BigDecimal.ZERO;

    StringProducer(String charset, int minLength, int maxLength) {
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
        chunkSize = allPerms.divideToIntegralValue(BigDecimal.valueOf(Math.pow(10, Math.log10(PERCENT) + DECIMAL_POINTS)));
    }

    StringProducer(String charset, String mask, char maskCh) {
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
        chunkSize = allPerms.divideToIntegralValue(BigDecimal.valueOf(Math.pow(10, Math.log10(PERCENT) + DECIMAL_POINTS)));
    }

    private void calculateAllPerms() {
        for (int i = currentLength; i <= maxLength; i++) {
            allPerms = allPerms.add(BigDecimal.valueOf(maxChar).pow(i));
        }
    }

    public boolean shouldShowProgress() {
        if (chunkCounter.compareTo(chunkSize) > 0) {
            chunkCounter = BigDecimal.ZERO;
            return true;
        }
        return false;
    }

    public void getProgress() {
        System.out.println(currPerm + "/" + allPerms);
        System.out.println(currPerm
                .divide(allPerms, ACTUAL_PRECISION, RoundingMode.DOWN)
                .multiply(BigDecimal.valueOf(PERCENT))
                .setScale(DECIMAL_POINTS, RoundingMode.DOWN) + "%");
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
        chunkCounter = chunkCounter.add(BigDecimal.ONE);
        currPerm = currPerm.add(BigDecimal.ONE);
        return strBuilder.toString();
    }
}
