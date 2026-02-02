import mpi.MPI;
import mpi.Request;
import util.Logger;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HexFormat;


public class AppMPJ {
    private final static int ROOT = 0;
    private final static int TAG_FORCE_STOP = 0;
    private final static int TAG_DONE = 1;
    private final static int TAG_PROGRESS = 2;

    private final static int OFF_FORCE_STOP = 0;
    private final static int OFF_DONE = 1;
    private final static int OFF_PROGRESS = 2;

    public static void main(String[] args) {
        MPI.Init(args);
        // read args to init vars
        int max = 5;
        byte[] targetBytes = HexFormat.of().parseHex("71e50ae29377c232b34b79a7b5900c01");
        String charset = "abcdefghijklmnopqrstuvwxyz0123456789";
        BigInteger allPerms = calculateAllPermutations(1, max, charset.length());
        HashAlgorithm algorithm = HashAlgorithm.MD5;

        int self = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        long start = 0;
        if (self == ROOT) {
            start = System.currentTimeMillis();
        }

        Request killReq;
        Request doneReq;
        Request progressReq;
        BigInteger chunk;
        BigInteger chunkRem;
        BigInteger counter;
        int[] currentLengthPtr = {0};
        int maxCh = charset.length();

        int[] buffer;
        int[] sndBuf = new int[max + 3];
        int[] rcvBuf = new int[max + 3];

        // TODO: send buffer data with flags at the same time to save bandwidth
        if (self == ROOT) {
            killReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_FORCE_STOP, 1, MPI.INT, MPI.ANY_SOURCE, TAG_FORCE_STOP);
            doneReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_DONE, 1, MPI.INT, MPI.ANY_SOURCE, TAG_DONE);
            int doneCnt = size - 1;

            while (true) {
                // check for kill switch
                if (killReq.Test() != null) {
                    // sync buffers
                    killReq.Wait();
                    Logger.debug("ROOT GOT KILL SIGNAL!");
                    sndBuf[OFF_FORCE_STOP] = 1;
                    for (int i = 0; i < size; i++) {
                        MPI.COMM_WORLD.Isend(sndBuf, OFF_FORCE_STOP, 1, MPI.INT, i, TAG_FORCE_STOP);
                    }
                    break;
                }
                if (doneReq.Test() != null) {
                    doneReq.Wait();
                    Logger.debug("ROOT GOT DONE SIGNAL! " + doneCnt);
                    doneCnt--;
                    if (doneCnt <= 0) {
                        break;
                    }
                    doneReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_DONE, 1, MPI.INT, MPI.ANY_SOURCE, TAG_DONE);
                }
            }
        } else {
            chunk = allPerms.divide(BigInteger.valueOf(size - 1));
            chunkRem = allPerms.mod(BigInteger.valueOf(size - 1));
            counter = chunk;
            if (self == size - 1) {
                counter = counter.add(chunkRem);
            }
            buffer = bigIntToBuffer(chunk.multiply(BigInteger.valueOf(self - 1)), max, currentLengthPtr, maxCh);

            killReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_FORCE_STOP, 1, MPI.INT, ROOT, TAG_FORCE_STOP);
            while (rcvBuf[OFF_FORCE_STOP] != 1) {
                // build bytes[]
                byte[] bytes;
                bytes = new byte[currentLengthPtr[0]];
                int pos = 0;
                for (int i = max - currentLengthPtr[0]; i <= max - 1; i++) {
                    bytes[pos] = (byte)charset.charAt(buffer[i]);
                    pos++;
                }

//                String str = new String(bytes);
//                System.out.println(str);

                // match
                if (matchToTarget(bytes, targetBytes, algorithm)) {
                    Logger.debug("Match: " + new String(bytes));
                    sndBuf[OFF_FORCE_STOP] = 1;
                    MPI.COMM_WORLD.Isend(sndBuf, OFF_FORCE_STOP, 1, MPI.INT, ROOT, TAG_FORCE_STOP);
                    break;
                }

                // increase/decrease
                buffer[max - 1]++;
                counter = counter.subtract(BigInteger.ONE);
                if (counter.compareTo(BigInteger.ZERO) <= 0) {
                    break;
                }

                // carryover
                int i = max - 1;
                while (i > 0) {
                    if (buffer[i] == maxCh) {
                        if (currentLengthPtr[0] == max - i) {
                            currentLengthPtr[0]++;
                            resetBuffer(buffer);
                            break;
                        }
                        buffer[i - 1]++;
                        buffer[i] = 0;
                        i--;
                    } else {
                        break;
                    }
                }

                if (killReq.Test() != null) {
                    // sync buffers
                    killReq.Wait();
                }
            }

            Logger.debug("Done!");
            sndBuf[OFF_DONE] = 1;
            MPI.COMM_WORLD.Isend(sndBuf, OFF_DONE, 1, MPI.INT, ROOT, TAG_DONE);
        }


        MPI.COMM_WORLD.Barrier();
        if (self == ROOT) {
            printStats(System.currentTimeMillis() - start, BigInteger.ZERO);
        }

        MPI.Finalize();
    }

    private static BigInteger calculateAllPermutations(int min, int max, int maxCh) {
        BigInteger result = BigInteger.ZERO;
        for (int i = min; i <= max; i++) {
            result = result.add(BigInteger.valueOf(maxCh).pow(i));
        }
        return result;
    }

    private static void printStats(long time, BigInteger tries) {
        Logger.info("Finished in: " + time + " ms");
        Logger.info("Total attempts: " + tries);
        Logger.info("Hashing speed: " + tries.divide(BigInteger.valueOf(time)).divide(BigInteger.valueOf(1000)) + " MH/s");
        Logger.info("Shutting down...");
    }

    private static void resetBuffer(int[] buf) {
        Arrays.fill(buf, 0);
    }

    private static boolean matchToTarget(byte[] bytes, byte[] targetBytes, HashAlgorithm algorithm) {
        byte[] currentHash = Hasher.hash(bytes, algorithm);
        return Arrays.equals(currentHash, targetBytes);
    }

    private static int[] bigIntToBuffer(BigInteger num, int max, int[] currentLengthPtr, int maxCh) {
        int[] result = new int[max];
        if (num.equals(BigInteger.ZERO)) {
            currentLengthPtr[0] = 1;
            return result;
        }

        // After every carryover, the buffer is set to 0, therefore we just skip those
        BigInteger skip = BigInteger.valueOf(maxCh);
        int skipCnt = 0;
        while (num.compareTo(skip) >= 0) {
            skipCnt++;
            num = num.subtract(skip);
            skip = skip.multiply(BigInteger.valueOf(maxCh));
        }

        int index = max - 1;
        while (num.compareTo(BigInteger.ZERO) > 0) {
            int tmp = num.mod(BigInteger.valueOf(maxCh)).intValue();
            result[index] = tmp;
            index--;
            num = num.divide(BigInteger.valueOf(maxCh));
        }

        currentLengthPtr[0] = skipCnt + 1;
        return result;
    }

    private static BigInteger bufferToBigInt(int[] buf, int max, int[] currentLengthPtr) {
        BigInteger result = BigInteger.ZERO;
        for (int i = 1; i < currentLengthPtr[0]; i++) {
            result = result.add(BigInteger.valueOf(max).pow(i));
        }

        int exp = 0;
        for (int i = buf.length - 1; i >= 0; i--) {
            if (buf[i] > 0) {
                result = result.add(BigInteger.valueOf(buf[i]).multiply(BigInteger.valueOf(max).pow(exp)));
            }
            exp++;
        }

        return result;
    }

}
