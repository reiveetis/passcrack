import util.Logger;

import java.math.BigInteger;

public class BruteWorker extends Thread {
    BigInteger end;
    BigInteger current;
    int maxCh;

    BruteWorker(BigInteger start, BigInteger end, int maxCh) {
        this.current = start;
        this.end = end;
        this.maxCh = maxCh;
    }

    private String getPerm() {

    }

    @Override
    public void run() {
        Logger.info("Start: " + current + " end: " + end);
        while (current.compareTo(end) < 0) {
//            Logger.debug(current.toString());
            current = current.add(BigInteger.ONE);
        }
        AppThreaded.finished.addAndGet(1);
        Logger.info("Done!");
    }
}
