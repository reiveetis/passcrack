import util.Logger;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public class BruteWorker extends Thread {
    private int currentLength;
    private final int maxLength;
    private final int maxChar;
    private final StringBuilder strBuilder;
    private final int[] buffer;
    private final String charset;
    private BigInteger counter;
    private final int last;
    private byte[] targetHash;
    private final HashAlgorithm algorithm;

    public BruteWorker(String targetHash, HashAlgorithm algorithm, String charset, int maxLength, BigInteger start, BigInteger counter) {
        this.maxLength = maxLength;
        this.strBuilder = new StringBuilder();
        this.charset = charset;
        this.maxChar = charset.length();
        this.counter = counter;
        this.buffer = computeBuffer(start);
        this.last = maxLength - 1;
        this.targetHash = HexFormat.of().parseHex(targetHash);
        this.algorithm = algorithm;
    }

    public boolean matchToTarget(String str) {
        byte[] currentHash = hash(str, algorithm);
        return Arrays.equals(currentHash, targetHash);
    }

    /// This function returns a hashed byte[] of given String and Algorithm.
    public static byte[] hash(String str, HashAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA256 -> hashSHA256(str);
            case MD5 -> hashMD5(str);
        };
    }

    /// This function returns a SHA-256 hashed byte[] of given String.
    public static byte[] hashSHA256(String str) {
        MessageDigest sha256 = null;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            Logger.debug(e.getMessage());
        }
        return sha256.digest(str.getBytes(StandardCharsets.UTF_8));
    }

    /// This function returns an MD5 hashed byte[] of given String.
    public static byte[] hashMD5(String str) {
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Logger.debug(e.getMessage());
        }
        return md5.digest(str.getBytes(StandardCharsets.UTF_8));
    }

    private int[] computeBuffer(BigInteger start) {
        int[] result = new int[maxLength];
        if (start.equals(BigInteger.ZERO)) {
            currentLength = 1;
            return result;
        }

        BigInteger skip = BigInteger.valueOf(maxChar);
        while (start.compareTo(skip) > 0) {
            start = start.subtract(skip);
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
            if (AppThreaded.isMatchFound.get()) {
                break;
            }
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
            if (matchToTarget(strBuilder.toString())) {
                Logger.debug("Match: " + strBuilder);
                AppThreaded.isMatchFound.set(true);
                break;
            }

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
