import util.Logger;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class AppThreaded {
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 5;
    private static final int THREADS = 10;
    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static BigInteger allPerms = BigInteger.ZERO;
    private static BigInteger chunk = BigInteger.ZERO;
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
        for (int i = 0; i < THREADS; i++) {
            new BruteWorker(
                    chunk.multiply(BigInteger.valueOf(i)),
                    chunk.multiply(BigInteger.valueOf(i + 1)),
                    CHARSET.length()
            ).start();
        }
        while (finished.get() != THREADS) {
            // wait
        }
        long end = System.currentTimeMillis();
        Logger.info("Completed in " + (end - start) + " ms");
    }
}
