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

    private final static int OFF_FORCE_STOP_FLAG = 0;
    private final static int OFF_PROGRESS_FLAG = 1;
    private final static int OFF_BUFFER_CURRENT_LENGTH = 2;
    private final static int OFF_BUFFER = 3;

    private static final int UPDATE_MS = 1000;

    public static void main(String[] args) {
        MPI.Init(args);
        // start read args to init vars
        int max = 6;
        byte[] targetBytes = HexFormat.of().parseHex("71e50ae29377c232b34b79a7b5900c01");
        String charset = "abcdefghijklmnopqrstuvwxyz0123456789";
        String maskStr = "....hr";
        char[] mask = maskStr.toCharArray();
        char maskCh = '.';
        if (mask.length != 0) {
            max = 0;
            for (int i = 0; i < mask.length; i++) {
                if (mask[i] == maskCh) {
                    mask[i] = 0;
                    max++;
                }
            }
        }
        BigInteger allPerms = BigInteger.ZERO;
        if (mask.length != 0) {
            allPerms = calculateAllPermutations(maskStr, maskCh, charset.length());
        } else {
            allPerms = calculateAllPermutations(1, max, charset.length());
        }
        HashAlgorithm algorithm = HashAlgorithm.MD5;
        // end read args to init vars

        final int SIZE_BUFFER_AND_CURRENT_LENGTH = max + 1;

        int self = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        long start = 0;
        if (self == ROOT) {
            start = System.currentTimeMillis();
        }

        Request killReq, doneReq, progressReq;
        BigInteger estProgress = BigInteger.ZERO;
        BigInteger attempts = BigInteger.ZERO;
        int[] currentLengthPtr = {0};
        int maxCh = charset.length();

        int[] buffer;
        int[] sndBuf = new int[max + 3];
        int[] rcvBuf = new int[max + 3];

        if (self == ROOT) {
            killReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_FORCE_STOP_FLAG, 1, MPI.INT, MPI.ANY_SOURCE, TAG_FORCE_STOP);
            doneReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_BUFFER_CURRENT_LENGTH, SIZE_BUFFER_AND_CURRENT_LENGTH, MPI.INT, MPI.ANY_SOURCE, TAG_DONE);
            progressReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_BUFFER_CURRENT_LENGTH, SIZE_BUFFER_AND_CURRENT_LENGTH, MPI.INT, MPI.ANY_SOURCE, TAG_PROGRESS);
            int doneCnt = size - 1;
            long timerStart = System.currentTimeMillis();

            // prepare sndBuf
            sndBuf[OFF_FORCE_STOP_FLAG] = 1;
            sndBuf[OFF_PROGRESS_FLAG] = 1;

            while (true) {
                // check for kill switch
                if (killReq.Test() != null) {
                    // sync buffers
                    killReq.Wait();
//                    Logger.debug("ROOT GOT KILL SIGNAL!");
                    for (int i = 0; i < size; i++) {
                        if (i == ROOT) continue;
                        MPI.COMM_WORLD.Isend(sndBuf, OFF_FORCE_STOP_FLAG, 1, MPI.INT, i, TAG_FORCE_STOP);
                    }
                }
                // done countdown latch
                if (doneReq.Test() != null) {
                    doneReq.Wait();
//                    Logger.debug("ROOT GOT DONE SIGNAL! " + doneCnt);
                    doneCnt--;
                    // compute bigInt
                    currentLengthPtr[0] = rcvBuf[OFF_BUFFER_CURRENT_LENGTH];
                    attempts = attempts.add(bufferToBigInt(Arrays.copyOfRange(rcvBuf, OFF_BUFFER, OFF_BUFFER + max), maxCh, currentLengthPtr[0]));
                    if (doneCnt <= 0) {
                        break;
                    }
                    // repost request
                    doneReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_BUFFER_CURRENT_LENGTH, SIZE_BUFFER_AND_CURRENT_LENGTH, MPI.INT, MPI.ANY_SOURCE, TAG_DONE);
                }
                // progress update
                if (progressReq.Test() != null) {
                    progressReq.Wait();
//                    Logger.debug("ROOT GOT PROGRESS SIGNAL!");
                    currentLengthPtr[0] = rcvBuf[OFF_BUFFER_CURRENT_LENGTH];
                    estProgress = estProgress.add(bufferToBigInt(Arrays.copyOfRange(rcvBuf, OFF_BUFFER, OFF_BUFFER + max), maxCh, currentLengthPtr[0]));
                    progressReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_BUFFER_CURRENT_LENGTH, SIZE_BUFFER_AND_CURRENT_LENGTH, MPI.INT, MPI.ANY_SOURCE, TAG_PROGRESS);
                }
                // progress update timer
                if (System.currentTimeMillis() - timerStart > UPDATE_MS) {
                    System.out.println(estProgress + "/" + allPerms);
                    for (int i = 0; i < size; i++) {
                        if (i == ROOT) continue;
                        MPI.COMM_WORLD.Isend(sndBuf, OFF_PROGRESS_FLAG, 1, MPI.INT, i, TAG_PROGRESS);
                    }
                    timerStart = System.currentTimeMillis();
                }
            }
        } else {
            BigInteger chunk = allPerms.divide(BigInteger.valueOf(size - 1));
            BigInteger chunkRem = allPerms.mod(BigInteger.valueOf(size - 1));
            BigInteger limit = chunk;
            BigInteger counter = BigInteger.ZERO;
            if (self == size - 1) {
                limit = limit.add(chunkRem);
            }
            BigInteger currentProgress = BigInteger.ZERO;
            int last;
            BigInteger selfChunkSize = chunk.multiply(BigInteger.valueOf(self - 1));
            if (mask.length == 0) {
                buffer = bigIntToBuffer(selfChunkSize, max, currentLengthPtr, maxCh);
            } else {
                buffer = computeMaskBuffer(selfChunkSize, max, maxCh);
                currentLengthPtr[0] = max;
            }
            killReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_FORCE_STOP_FLAG, 1, MPI.INT, ROOT, TAG_FORCE_STOP);
            progressReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_PROGRESS_FLAG, 1, MPI.INT, ROOT, TAG_PROGRESS);
            while (rcvBuf[OFF_FORCE_STOP_FLAG] != 1) {
                // build bytes[]
                byte[] bytes;
                if (mask.length == 0) {
                    bytes = new byte[currentLengthPtr[0]];
                    int pos = 0;
                    for (int i = max - currentLengthPtr[0]; i <= max - 1; i++) {
                        bytes[pos] = (byte)charset.charAt(buffer[i]);
                        pos++;
                    }
                } else {
                    bytes = new byte[mask.length];
                    int pos = 0;
                    for (int i = 0; i < mask.length; i++) {
                        if (mask[i] == 0) {
                            bytes[i] = (byte)charset.charAt(buffer[pos]);
                            pos++;
                        } else {
                            bytes[i] = (byte)mask[i];
                        }
                    }
                }


//                String str = new String(bytes);
//                System.out.println(str);

                // match
                if (matchToTarget(bytes, targetBytes, algorithm)) {
                    Logger.debug("Match: " + new String(bytes));
                    sndBuf[OFF_FORCE_STOP_FLAG] = 1;
                    MPI.COMM_WORLD.Isend(sndBuf, OFF_FORCE_STOP_FLAG, 1, MPI.INT, ROOT, TAG_FORCE_STOP);
                    break;
                }

                // increase/decrease
                buffer[max - 1]++;
                counter = counter.add(BigInteger.ONE);
                currentProgress = currentProgress.add(BigInteger.ONE);
                if (counter.compareTo(limit) >= 0) {
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
                } else if (progressReq.Test() != null) {
                    progressReq.Wait();
                    sendDataBufferToRoot(currentProgress, max, maxCh, sndBuf, SIZE_BUFFER_AND_CURRENT_LENGTH, TAG_PROGRESS);
                    currentProgress = BigInteger.ZERO;
                    progressReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_PROGRESS_FLAG, 1, MPI.INT, ROOT, TAG_PROGRESS);
                }
            }
            sendDataBufferToRoot(counter, max, maxCh, sndBuf, SIZE_BUFFER_AND_CURRENT_LENGTH, TAG_DONE);
//            Logger.debug("Done!");
        }
        MPI.COMM_WORLD.Barrier();
        if (self == ROOT) {
            printStats(System.currentTimeMillis() - start, attempts);
        }
        MPI.Finalize();
    }

    private static int[] computeMaskBuffer(BigInteger num, int max, int maxCh) {
        int[] result = new int[max];
        if (num.equals(BigInteger.ZERO)) {
            return result;
        }

        int index = max - 1;
        while (num.compareTo(BigInteger.ZERO) > 0) {
            int tmp = num.mod(BigInteger.valueOf(maxCh)).intValue();
            result[index] = tmp;
            index--;
            num = num.divide(BigInteger.valueOf(maxCh));
        }
        return result;
    }

    private static void sendDataBufferToRoot(BigInteger currentProgress, int max, int maxCh, int[] sndBuf, int SIZE_BUFFER_AND_CURRENT_LENGTH, int tag) {
        int[] tempCurrentLengthPtr = {0};
        int[] counterBuf = bigIntToBuffer(currentProgress, max, tempCurrentLengthPtr, maxCh);
        System.arraycopy(counterBuf, 0, sndBuf, OFF_BUFFER, max);
        sndBuf[OFF_BUFFER_CURRENT_LENGTH] = tempCurrentLengthPtr[0];
        MPI.COMM_WORLD.Isend(sndBuf, OFF_BUFFER_CURRENT_LENGTH, SIZE_BUFFER_AND_CURRENT_LENGTH, MPI.INT, ROOT, tag);
    }

    private static BigInteger calculateAllPermutations(int min, int max, int maxCh) {
        BigInteger result = BigInteger.ZERO;
        for (int i = min; i <= max; i++) {
            result = result.add(BigInteger.valueOf(maxCh).pow(i));
        }
        return result;
    }

    private static BigInteger calculateAllPermutations(String mask, char maskCh, int maxCh) {
        BigInteger result = BigInteger.ZERO;
        int unk = 0;
        for (int i = 0; i < mask.length(); i++) {
            if (mask.charAt(i) == maskCh) {
                unk++;
            }
        }
        result = result.add(BigInteger.valueOf(maxCh).pow(unk));
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

    private static int[] bigIntToBuffer(BigInteger num, int max, int[] destLen, int maxCh) {
        int[] result = new int[max];
        if (num.equals(BigInteger.ZERO)) {
            destLen[0] = 1;
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

        destLen[0] = skipCnt + 1;
        return result;
    }

    private static BigInteger bufferToBigInt(int[] buf, int maxCh, int currentLength) {
        BigInteger result = BigInteger.ZERO;
        for (int i = 1; i < currentLength; i++) {
            result = result.add(BigInteger.valueOf(maxCh).pow(i));
        }

        int exp = 0;
        for (int i = buf.length - 1; i >= 0; i--) {
            if (buf[i] > 0) {
                result = result.add(BigInteger.valueOf(buf[i]).multiply(BigInteger.valueOf(maxCh).pow(exp)));
            }
            exp++;
        }

        return result;
    }

}
