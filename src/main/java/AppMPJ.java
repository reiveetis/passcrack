import mpi.MPI;
import mpi.Request;
import util.Logger;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HexFormat;


public class AppMPJ {
    private final static int ROOT = 0;

    private final static int ARG_CHARSET = 0;
    private final static int ARG_HASH = 1;
    private final static int ARG_ALGO = 2;
    private final static int ARG_MAX = 3;
    private final static int ARG_PERMS = 4;
    private final static int ARG_START = 5;
    private final static int ARG_DICT = 6;
    private final static int ARG_MASK = 7;
    private final static int ARG_MASKCH = 8;

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
        int args_start = -1;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("ARGS_START")) {
                args_start = i + 1;
            }
        }
        validateArgs(args, args_start);

        String charset = args[args_start + ARG_CHARSET];
        String hash = args[args_start + ARG_HASH];
        HashAlgorithm algo = HashAlgorithm.MD5;
        if (Integer.parseInt(args[args_start + ARG_ALGO]) == 1) {
            algo = HashAlgorithm.SHA256;
        }
        int max = Integer.parseInt(args[args_start + ARG_MAX]);
        BigInteger allPerms = new BigInteger(args[args_start + ARG_PERMS]);
        long start = Long.parseLong(args[args_start + ARG_START]);
        long dictTime = Long.parseLong(args[args_start + ARG_DICT]);
        String maskStr = args[args_start + ARG_MASK];
        char maskCh = args[args_start + ARG_MASKCH].charAt(0);
        byte[] targetBytes = HexFormat.of().parseHex(hash);
        char[] mask = maskStr.toCharArray();
        if (mask.length != 0) {
            max = 0;
            for (int i = 0; i < mask.length; i++) {
                if (mask[i] == maskCh) {
                    mask[i] = 0;
                    max++;
                }
            }
        }

        final int bufferAndLengthSize = max + 1;
        int self = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
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
            doneReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_BUFFER_CURRENT_LENGTH, bufferAndLengthSize, MPI.INT, MPI.ANY_SOURCE, TAG_DONE);
            progressReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_BUFFER_CURRENT_LENGTH, bufferAndLengthSize, MPI.INT, MPI.ANY_SOURCE, TAG_PROGRESS);
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
                    postRequestToAll(size, sndBuf, OFF_FORCE_STOP_FLAG, TAG_FORCE_STOP);
                }
                // done countdown latch
                if (doneReq.Test() != null) {
                    doneReq.Wait();
                    doneCnt--;
                    // compute bigInt
                    currentLengthPtr[0] = rcvBuf[OFF_BUFFER_CURRENT_LENGTH];
                    attempts = attempts.add(bufferToBigInt(Arrays.copyOfRange(rcvBuf, OFF_BUFFER, OFF_BUFFER + max), maxCh, currentLengthPtr[0]));
                    // countdown latch
                    if (doneCnt <= 0) {
                        break;
                    }
                    doneReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_BUFFER_CURRENT_LENGTH, bufferAndLengthSize, MPI.INT, MPI.ANY_SOURCE, TAG_DONE);
                }
                // progress update
                if (progressReq.Test() != null) {
                    progressReq.Wait();
                    currentLengthPtr[0] = rcvBuf[OFF_BUFFER_CURRENT_LENGTH];
                    estProgress = estProgress.add(bufferToBigInt(Arrays.copyOfRange(rcvBuf, OFF_BUFFER, OFF_BUFFER + max), maxCh, currentLengthPtr[0]));
                    progressReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_BUFFER_CURRENT_LENGTH, bufferAndLengthSize, MPI.INT, MPI.ANY_SOURCE, TAG_PROGRESS);
                }
                // progress update timer
                if (System.currentTimeMillis() - timerStart > UPDATE_MS) {
                    System.out.println(estProgress + "/" + allPerms);
                    postRequestToAll(size, sndBuf, OFF_PROGRESS_FLAG, TAG_PROGRESS);
                    timerStart = System.currentTimeMillis();
                }
            }
        } else {
            BigInteger chunk = allPerms.divide(BigInteger.valueOf(size - 1));
            BigInteger chunkRem = allPerms.mod(BigInteger.valueOf(size - 1));
            BigInteger limit = chunk;
            if (self == size - 1) {
                limit = limit.add(chunkRem);
            }
            BigInteger counter = BigInteger.ZERO;
            BigInteger currentProgress = BigInteger.ZERO;
            BigInteger selfChunkStart = chunk.multiply(BigInteger.valueOf(self - 1));
            if (mask.length == 0) {
                buffer = bigIntToBuffer(selfChunkStart, max, currentLengthPtr, maxCh);
            } else {
                buffer = computeMaskBuffer(selfChunkStart, max, maxCh);
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
                if (matchToTarget(bytes, targetBytes, algo)) {
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
                    sendDataBufferToRoot(currentProgress, max, maxCh, sndBuf, bufferAndLengthSize, TAG_PROGRESS);
                    currentProgress = BigInteger.ZERO;
                    progressReq = MPI.COMM_WORLD.Irecv(rcvBuf, OFF_PROGRESS_FLAG, 1, MPI.INT, ROOT, TAG_PROGRESS);
                }
            }
            sendDataBufferToRoot(counter, max, maxCh, sndBuf, bufferAndLengthSize, TAG_DONE);
//            Logger.debug("Done!");
        }
        MPI.COMM_WORLD.Barrier();
        if (self == ROOT) {
            App.printStats(System.currentTimeMillis() - start, dictTime, attempts);
        }
        MPI.Finalize();
    }

    private static void postRequestToAll(int size, int[] sndBuf, int flag, int tag) {
        for (int i = 0; i < size; i++) {
            if (i == ROOT) continue;
            MPI.COMM_WORLD.Isend(sndBuf, flag, 1, MPI.INT, i, tag);
        }
    }

    private static void validateArgs(String[] args, int args_start) {
        if (args_start == -1) {
            System.exit(-1);
        }
        for (int i = args_start; i < args.length; i++) {
            if (i == args_start + ARG_MASK) continue;
            if (args[i].isEmpty()) {
                System.exit(-1);
            }
        }
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
