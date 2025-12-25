import util.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.concurrent.*;

public class AppThreaded {
    // "zzzzz", length = 5, MD5
//    private static final String TARGET = "95ebc3c7b3b9f1d2c40fec14415d3cb8";

//    private static final String TARGET = "95ebc3c7b3b9f1d2c40fec14415d3cb7";

    // "1945hr", length 6, MD5
    private static final String TARGET = "71e50ae29377c232b34b79a7b5900c01";

    // "123", length 3, MD5
//    private static final String TARGET = "202cb962ac59075b964b07152d234b70";

    // "password", length 8, MD5
//    private static final String TARGET = "5f4dcc3b5aa765d61d8327deb882cf99";

    // "1", length 1, MD5
//    private static final String TARGET = "c4ca4238a0b923820dcc509a6f75849b";

    private static final HashAlgorithm TARGET_ALGO = HashAlgorithm.MD5;

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 6;
    private static final int THREADS = Runtime.getRuntime().availableProcessors();
//    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyz0123456789";
//    private static final String CHARSET = "0123456789";
    private static BigInteger allPerms = BigInteger.ZERO;

    private static BruteForceManager manager;

    private static ArrayList<String> results = new ArrayList<>();
//    public static synchronized void addResult(String result) {
//        if (results.contains(result)) {
//            Logger.error("Duplicated result: " + result);
//        }
//        results.add(result);
//    }

    private static void calculateAllPermutations() {
        int maxChar = CHARSET.length();
        for (int i = MIN_LENGTH; i <= MAX_LENGTH; i++) {
            allPerms = allPerms.add(BigInteger.valueOf(maxChar).pow(i));
        }
    }

    public static void shutdown() {
        if (manager == null) {
            return;
        }
        manager.shutdown();
    }

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        Logger.info("Started!");
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        calculateAllPermutations();
        int fragments = 100;

        BigInteger chunk = allPerms.divide(BigInteger.valueOf(fragments));
        BigInteger chunkRem = allPerms.mod(BigInteger.valueOf(fragments));

        int latchSize = Math.min(fragments, THREADS);
        manager = new BruteForceManager(latchSize);

        UI ui = new UI(allPerms, manager, 1000);

        ArrayList<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < fragments; i++) {
            BigInteger count = chunk;
            if (i == fragments - 1) {
                count = count.add(chunkRem);
            }
            tasks.add(new BruteForceTask(
                    TARGET,
                    TARGET_ALGO,
                    CHARSET,
                    MAX_LENGTH,
                    chunk.multiply(BigInteger.valueOf(i)),
                    count,
                    manager
            ));
        }

        try {
            ui.start();
            pool.invokeAll(tasks);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            ui.shutdown();
            // make sure that updateProgressLatch is dead to prevent deadlock!
            manager.killLatch();
        }

        long end = System.currentTimeMillis();
        long time = end - start;

//        System.out.println(allPerms.intValue() == results.size());

        Logger.info("Finished in " + time + " ms");
        Logger.info("Total attempts: " + manager.getTotalProgress());
        Logger.info(manager.getTotalProgress().divide(BigInteger.valueOf(time)).divide(BigInteger.valueOf(1000)) + " MH/s");
        Logger.info("Shutting down...");

        // not counting shutdown into runtime, since it takes a really long time
        pool.shutdown();
    }
}
