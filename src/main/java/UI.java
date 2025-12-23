import util.Logger;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class UI extends Thread {
    private final BigInteger total;
    private final BruteForceManager manager;
    private final int UPDATE_MS;
    public AtomicBoolean shouldStop;


    public UI(BigInteger total, BruteForceManager manager, int updateMs) {
        this.total = total;
        this.manager = manager;
        this.UPDATE_MS = updateMs;
        shouldStop = new AtomicBoolean(false);
    }

    private void update() throws InterruptedException {
        manager.resetCurrentProgress();
        manager.prepareProgressUpdate();
        manager.updateProgressLatch.await();
        System.out.println(manager.getTotalProgress().add(manager.getCurrentProgress()) + "/" + total);
    }

    @Override
    public void run() {
        while (!shouldStop.get()) {
            try {
                sleep(UPDATE_MS);
                update();
            } catch (InterruptedException e) {
                Logger.error(e.getMessage());
            }
        }
    }
}
