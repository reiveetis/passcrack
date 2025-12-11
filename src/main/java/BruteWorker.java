import util.Logger;

import java.math.BigInteger;
import java.util.Arrays;

public class BruteWorker extends Thread {
    private int currentLength;
    private final int maxLength;
    private final int maxChar;
    private final StringBuilder strBuilder;
    private final int[] buffer;
    private final String charset;
    private BigInteger counter;
    private final int last;

    public BruteWorker(String charset, int maxLength, BigInteger start, BigInteger counter) {
        this.maxLength = maxLength;
        this.strBuilder = new StringBuilder();
        this.charset = charset;
        this.maxChar = charset.length();
        this.counter = counter;
        this.buffer = computeBuffer(start);
        this.last = maxLength - 1;
    }



    // this is wrong
//    private int[] computeBuffer(BigInteger start) {
//        Logger.debug(String.valueOf(start));
//        int[] result = new int[maxLength];
//        if (start.equals(BigInteger.ZERO)) {
//            currentLength = 1;
//            return result;
//        }
//        int index = maxLength - 1;
//        while (start.compareTo(BigInteger.ZERO) > 0) {
//            int tmp = start.mod(BigInteger.valueOf(maxChar)).intValue();
//            result[index] = tmp;
//            index--;
//            currentLength++;
//            start = start.divide(BigInteger.valueOf(maxChar));
//        }
//        Logger.debug(Arrays.toString(result));
//        return result;
//    }

    private int[] computeBuffer(BigInteger start) {
//        Logger.debug(String.valueOf(start));
        int[] result = new int[maxLength];
        if (start.equals(BigInteger.ZERO)) {
            currentLength = 1;
            return result;
        }

        BigInteger skip = BigInteger.valueOf(maxChar);
        while (start.compareTo(skip) > 0) {
//            System.out.println("skip: " + skip);
            start = start.subtract(skip);

            // skip = skip * maxChar
            skip = skip.multiply(BigInteger.valueOf(maxChar));
        }

        int index = maxLength - 1;
        while (start.compareTo(BigInteger.ZERO) > 0) {
            int tmp = start.mod(BigInteger.valueOf(maxChar)).intValue();
            result[index] = tmp;
            index--;
            currentLength++;
            start = start.divide(BigInteger.valueOf(maxChar));
        }
//        Logger.debug(Arrays.toString(result));
        return result;
    }

    private void resetBuffer() {
        for (int i = last; i >= maxLength - currentLength; i--) {
            buffer[i] = 0;
        }
    }

    @Override
    public void run() {
        Logger.info("Started!");
        while (true) {
            if (counter.compareTo(BigInteger.ZERO) <= 0) {
                break;
            }
            if (buffer[0] == maxChar) {
                break;
            }

            // build string
            strBuilder.setLength(0);
            for (int i = maxLength - currentLength; i <= last; i++) {
                strBuilder.append(charset.charAt(buffer[i]));
            }

            // do stuff with string
//            System.out.println(strBuilder);

            // increase/decrease
            buffer[last]++;
            counter = counter.subtract(BigInteger.ONE);

            // carryover
            int i = last;
            while (i > 0) {
                if (buffer[i] == maxChar) {
                    if (currentLength == maxLength - i) {
                        currentLength++;
                        resetBuffer();
                        break;
                    }
                    buffer[i - 1]++;
                    buffer[i] = 0;
                    i--;
                } else {
                    break;
                }
            }
        }

        // stupid "barrier" for main termination
        AppThreaded.finished.addAndGet(1);
        Logger.info("Done!");
    }

}
