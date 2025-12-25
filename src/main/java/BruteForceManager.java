import java.math.BigInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BruteForceManager {
    // kills all tasks
    public boolean isMatchFound = false;

    public CountDownLatch updateProgressLatch = new CountDownLatch(0);
    private final int LATCH_SIZE;

    // update on death
    private BigInteger totalProgress = BigInteger.ZERO;
    private final ReadWriteLock totalRw = new ReentrantReadWriteLock();

    // update on a timer
    private BigInteger currentProgress = BigInteger.ZERO;
    private final ReadWriteLock currRw = new ReentrantReadWriteLock();

    public BruteForceManager(int latchSize) {
        this.LATCH_SIZE = latchSize;
    }

    public void killLatch() {
        if (updateProgressLatch.getCount() == 0) {
            return;
        }
        while (updateProgressLatch.getCount() > 0) {
            updateProgressLatch.countDown();
        }
    }

    public void shutdown() {
        isMatchFound = true;
        killLatch();
    }

    public void resetCurrentProgress() {
        currRw.writeLock().lock();
        this.currentProgress = BigInteger.ZERO;
        currRw.writeLock().unlock();
    }

    public void addCurrentProgress(BigInteger value) {
        currRw.writeLock().lock();
        this.currentProgress = this.currentProgress.add(value);
        updateProgressLatch.countDown();
        currRw.writeLock().unlock();
    }

    public BigInteger getCurrentProgress() {
        currRw.readLock().lock();
        try {
            return this.currentProgress;
        } finally {
            currRw.readLock().unlock();
        }
    }

    public void addTotalProgress(BigInteger value) {
        totalRw.writeLock().lock();
        this.totalProgress = this.totalProgress.add(value);
        totalRw.writeLock().unlock();
    }

    public BigInteger getTotalProgress() {
        totalRw.readLock().lock();
        try {
            return this.totalProgress;
        } finally {
            totalRw.readLock().unlock();
        }
    }

    public void prepareProgressUpdate() {
        if (isMatchFound) {
            return;
        }
        updateProgressLatch = new CountDownLatch(LATCH_SIZE);
    }

}
