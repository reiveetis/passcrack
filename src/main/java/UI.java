import util.Logger;

import java.math.BigInteger;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class UI extends Thread {
    private final BigInteger total;
    private final BruteForceManager manager;
    private final int UPDATE_MS;
    private final ScheduledExecutorService scheduler;
    private boolean isTyping = false;
    public AtomicBoolean shouldStop;
    private final Scanner sc = new Scanner(System.in);


    public UI(BigInteger total, BruteForceManager manager, int updateMs) {
        this.total = total;
        this.manager = manager;
        this.UPDATE_MS = updateMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.shouldStop = new AtomicBoolean(false);
    }

    private void update() throws InterruptedException {
        manager.resetCurrentProgress();
        manager.prepareProgressUpdate();
        manager.updateProgressLatch.await();
        System.out.println(manager.getTotalProgress().add(manager.getCurrentProgress()) + "/" + total);
    }

    public void shutdown() {
        sc.close();
        shouldStop.set(true);
        scheduler.shutdown();
    }

    @Override
    public void run() {
        Logger.info("Started!");
        scheduler.scheduleAtFixedRate(() -> {
            if (shouldStop.get()) {
                scheduler.shutdown();
                return;
            }
            try {
                update();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.error(e.getMessage());
            }
        }, UPDATE_MS, UPDATE_MS, TimeUnit.MILLISECONDS);

        // TODO: implement input

        Logger.info("Shutting down...");
    }
}
