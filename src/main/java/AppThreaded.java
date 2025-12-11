import util.Logger;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

public class AppThreaded {
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 6;
    private static final int THREADS = 20;
    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
//    private static final String CHARSET = "0123456789";
    private static BigInteger allPerms = BigInteger.ZERO;
    private static BigInteger chunk = BigInteger.ZERO;
    private static BigInteger chunkRem = BigInteger.ZERO;
    public static AtomicInteger finished = new AtomicInteger(0);

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
        chunk = allPerms.divide(BigInteger.valueOf(THREADS));
        chunkRem = allPerms.mod(BigInteger.valueOf(THREADS));
        for (int i = 0; i < THREADS; i++) {
            BigInteger count = chunk;
            if (i == THREADS - 1) {
                count = count.add(chunkRem);
            }
            new BruteWorker(
                    CHARSET,
                    MAX_LENGTH,
                    chunk.multiply(BigInteger.valueOf(i)),
                    count
            ).start();
        }
        while (finished.get() != THREADS) {
            // wait
        }
        long end = System.currentTimeMillis();
        Logger.info("Completed in " + (end - start) + " ms");
    }
}
