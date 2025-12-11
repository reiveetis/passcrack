import util.Logger;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AppThreaded {
    // "zzzzz", length = 5, MD5
//    private static final String TARGET = "95ebc3c7b3b9f1d2c40fec14415d3cb8";

    private static final String TARGET = "95ebc3c7b3b9f1d2c40fec14415d3cb7";

    // "1945hr", length 6, MD5
//    private static final String TARGET = "71e50ae29377c232b34b79a7b5900c01";

    // "123", length 3, MD5
//    private static final String TARGET = "202cb962ac59075b964b07152d234b70";


    private static final HashAlgorithm TARGET_ALGO = HashAlgorithm.MD5;

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 3;
    private static final int THREADS = 10;
//    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
//    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String CHARSET = "0123456789";
    private static BigInteger allPerms = BigInteger.ZERO;
    public static AtomicInteger finished = new AtomicInteger(0);
    public static AtomicBoolean isMatchFound = new AtomicBoolean(false);

    private static void calculateAllPermutations() {
        int maxChar = CHARSET.length();
        for (int i = MIN_LENGTH; i <= MAX_LENGTH; i++) {
            allPerms = allPerms.add(BigInteger.valueOf(maxChar).pow(i));
        }
    }

    static void main(String[] args) {
        long start = System.currentTimeMillis();
        Logger.info("Started!");
        calculateAllPermutations();
        BigInteger chunk = allPerms.divide(BigInteger.valueOf(THREADS));
        BigInteger chunkRem = allPerms.mod(BigInteger.valueOf(THREADS));
        Logger.debug("Chunk: " + chunk);
        Logger.debug("ChunkRem: " + chunkRem);
        for (int i = 0; i < THREADS; i++) {
            BigInteger count = chunk;
            if (i == THREADS - 1) {
                count = count.add(chunkRem);
            }
            new BruteWorker(
                    TARGET,
                    TARGET_ALGO,
                    CHARSET,
                    MAX_LENGTH,
                    chunk.multiply(BigInteger.valueOf(i)),
                    count
            ).start();
        }

        // barrier
        while (true) {
            if (isMatchFound.get()) { break;}
            if (finished.get() == THREADS) { break;}
        }
        long end = System.currentTimeMillis();
        Logger.info("Completed in " + (end - start) + " ms");
        System.exit(0);
    }
}
