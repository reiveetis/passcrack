import util.Logger;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class UI extends Thread {
    private final BigInteger totalProgress;
    public AtomicBoolean shouldStop;

    public UI(BigInteger totalProgress) {
        this.totalProgress = totalProgress;
        shouldStop = new AtomicBoolean(false);
    }

    private void update() {
        System.out.println(AppThreaded.getProgress() + "/" + totalProgress);
    }

    @Override
    public void run() {
        while (!shouldStop.get()) {
            try {
                update();
                sleep(1000);
            } catch (InterruptedException e) {
                Logger.debug(e.getMessage());
            }
        }
    }
}
