import util.Logger;

import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;

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

    private static final HashAlgorithm TARGET_ALGO = HashAlgorithm.MD5;

    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 6;
    private static final int THREADS = Runtime.getRuntime().availableProcessors();
//    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyz0123456789";
//    private static final String CHARSET = "0123456789";
    private static BigInteger allPerms = BigInteger.ZERO;
    private static BigInteger progress = BigInteger.ZERO;
//    public static AtomicInteger finished = new AtomicInteger(0);
    public static CountDownLatch finishedLatch;

    public static synchronized void addProgress(BigInteger counter) {
        progress = progress.add(counter);
    }

    public static BigInteger getProgress() {
        return progress;
    }

//    private static ArrayList<String> results = new ArrayList<>();
//
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

    static void main(String[] args) {
        long start = System.currentTimeMillis();
        Logger.info("Started!");
        calculateAllPermutations();
        UI ui = new UI(allPerms);

        int fragments = 100;
        finishedLatch = new CountDownLatch(fragments);

        BigInteger chunk = allPerms.divide(BigInteger.valueOf(fragments));
        BigInteger chunkRem = allPerms.mod(BigInteger.valueOf(fragments));

//        ui.start();

        // TODO: instead of this, make a queue
        // TODO: block with synchronizer until all cores have jobs

        // TODO: once queue is implemented, start threads from beginning, middle and end fragments
        // TODO: this way we have a better chance at guessing the hash
        // TODO: we could shuffle fragments in this order: B,M,E,B,M,E... (Beginning, Middle, End)
        // TODO: that means we have to divide whole set by 3
        for (int i = 0; i < fragments; i++) {
            if (finishedLatch.getCount() == 0) { break; }
            BigInteger count = chunk;
            if (i == fragments - 1) {
                count = count.add(chunkRem);
            }
            new BruteWorker(
                    TARGET,
                    TARGET_ALGO,
                    CHARSET,
                    MAX_LENGTH,
                    chunk.multiply(BigInteger.valueOf(i)),
                    count,
                    finishedLatch
            ).start();
            while (Math.abs(i + 1 - finishedLatch.getCount()) == THREADS) {
                // block while all threads busy
            }
        }

        // barrier
        try {
            finishedLatch.await();
        } catch (InterruptedException e) {
            Logger.error(e.getMessage());
        }

        ui.shouldStop.set(true);

//        System.out.println(allPerms.intValue() == results.size());

        System.out.println(progress + "/" + allPerms);

        long end = System.currentTimeMillis();
        Logger.info("Completed in " + (end - start) + " ms");
        System.exit(0);
    }
}
