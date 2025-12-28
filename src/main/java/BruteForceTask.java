import util.Logger;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.Callable;

public class BruteForceTask implements Callable<ArrayList<byte[]>> {
    private int currentLength;
    private final int maxLength;
    private final int maxChar;
    private final int[] buffer;
    private final String charset;
    private final BigInteger limit;
    private BigInteger counter = BigInteger.ZERO;
    private final int last;
    private final byte[] targetHash;
    private final HashAlgorithm algorithm;
    private final BruteForceManager manager;
    private char[] mask = null;
    private ArrayList<byte[]> history;

    public BruteForceTask(String targetHash, HashAlgorithm algorithm, String charset, int maxLength, BigInteger start,
                          BigInteger limit, BruteForceManager manager) {
        this.maxLength = maxLength;
        this.charset = charset;
        this.maxChar = charset.length();
        this.limit = limit;
        this.buffer = computeBuffer(start);
        this.last = maxLength - 1;
        this.targetHash = HexFormat.of().parseHex(targetHash);
        this.algorithm = algorithm;
        this.manager = manager;
        if (manager.isKeepHistory) {
            history = new ArrayList<>();
        }
    }

    public BruteForceTask(String targetHash, HashAlgorithm algorithm, String charset, String mask, char maskCh,
                          BigInteger start, BigInteger limit, BruteForceManager manager) {
        this.mask = mask.toCharArray();
        for (int i = 0; i < this.mask.length; i++) {
            if (this.mask[i] == maskCh) {
                this.mask[i] = 0;
                this.currentLength++;
            }
        }
        this.maxLength = currentLength;
        this.charset = charset;
        this.maxChar = charset.length();
        this.limit = limit;
        this.buffer = computeMaskBuffer(start);
        this.last = currentLength - 1;
        this.targetHash = HexFormat.of().parseHex(targetHash);
        this.algorithm = algorithm;
        this.manager = manager;
        if (manager.isKeepHistory) {
            history = new ArrayList<>();
        }
    }

    private int[] computeMaskBuffer(BigInteger start) {
        int[] result = new int[maxLength];
        if (start.equals(BigInteger.ZERO)) {
            return result;
        }

        int index = maxLength - 1;
        while (start.compareTo(BigInteger.ZERO) > 0) {
            int tmp = start.mod(BigInteger.valueOf(maxChar)).intValue();
            result[index] = tmp;
            index--;
            start = start.divide(BigInteger.valueOf(maxChar));
        }
        return result;
    }

    public boolean matchToTarget(String str) {
        byte[] currentHash = hash(str, algorithm);
        return Arrays.equals(currentHash, targetHash);
    }

    public boolean matchToTarget(byte[] bytes) {
        byte[] currentHash = hash(bytes, algorithm);
        return Arrays.equals(currentHash, targetHash);
    }

    /// This function returns a hashed byte[] of given String and Algorithm.
    public static byte[] hash(String str, HashAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA256 -> hashSHA256(str.getBytes());
            case MD5 -> hashMD5(str.getBytes());
        };
    }

    public static byte[] hash(byte[] bytes, HashAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA256 -> hashSHA256(bytes);
            case MD5 -> hashMD5(bytes);
        };
    }

    /// This function returns a SHA-256 hashed byte[] of given String.
    public static byte[] hashSHA256(byte[] bytes) {
        MessageDigest sha256 = null;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            Logger.debug(e.getMessage());
        }
        return sha256.digest(bytes);
    }

    /// This function returns an MD5 hashed byte[] of given String.
    public static byte[] hashMD5(byte[] bytes) {
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Logger.debug(e.getMessage());
        }
        return md5.digest(bytes);
    }

    private int[] computeBuffer(BigInteger start) {
//        System.out.println("start: " + start);
        int[] result = new int[maxLength];
        if (start.equals(BigInteger.ZERO)) {
            currentLength = 1;
            return result;
        }

        BigInteger skip = BigInteger.valueOf(maxChar);
        int skipCnt = 0;
        while (start.compareTo(skip) >= 0) {
            skipCnt++;
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

        // adjust for edge case of currentLength being incorrect
        currentLength = skipCnt + 1;

//        System.out.println(Arrays.toString(result));
        return result;
    }

    private void resetBuffer() {
        for (int i = last; i >= maxLength - currentLength; i--) {
            buffer[i] = 0;
        }
    }

    @Override
    public ArrayList<byte[]> call() {
//        Logger.info("Started!");

        boolean isProgressUpdated = false;
        boolean isKeepHistory = manager.isKeepHistory;
        while (!manager.isMatchFound) {
            if (manager.updateProgressLatch.getCount() > 0 && !isProgressUpdated) {
                manager.addCurrentProgress(counter);
                isProgressUpdated = true;
            } else if (manager.updateProgressLatch.getCount() == 0 && isProgressUpdated) {
                isProgressUpdated = false;
            }

            // build byte[]
            byte[] bytes;
            if (mask == null) {
                bytes = new byte[currentLength];
                int pos = 0;
                for (int i = maxLength - currentLength; i <= last; i++) {
                    bytes[pos] = (byte)charset.charAt(buffer[i]);
                    pos++;
                }
            } else {
                bytes = new byte[mask.length];
                int pos = 0;
                for (int i = 0; i < mask.length; i++) {
                    if (mask[i] == 0) {
                        bytes[i] = (byte)charset.charAt(buffer[pos]);
                        pos++;
                    } else {
                        bytes[i] = (byte)mask[i];
                    }
                }
            }

            if (isKeepHistory) {
                history.add(bytes);
            }

//            String str = new String(bytes);
//            System.out.println(str);
//            AppThreaded.addResult(str);

            if (matchToTarget(bytes)) {
                Logger.debug("Match: " + new String(bytes));
                manager.isMatchFound = true;

                // kill latch
                Logger.info("Killing latch...");
                manager.killLatch();
                break;
            }

            // increase/decrease
            buffer[last]++;
            counter = counter.add(BigInteger.ONE);

            if (counter.compareTo(limit) >= 0) {
                break;
            }

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

        manager.addTotalProgress(counter);
        if (manager.updateProgressLatch.getCount() > 0 && !isProgressUpdated) {
            manager.updateProgressLatch.countDown();
        }
//        Logger.info("Done!");
        return history;
    }
}
