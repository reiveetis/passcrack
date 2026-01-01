import util.Logger;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {
    private final static boolean DEBUG = true;
    private static final String STRING_09 = "[0-9]";
    private static final String STRING_AZ_L = "[a-z]";
    private static final String STRING_AZ_U = "[A-Z]";
    private static final int THREADS = Runtime.getRuntime().availableProcessors();
    private static final int UPDATE_MS = 1000;
    private static final int DEFAULT_MAX = 6;
    private static final int DEFAULT_MIN = 1;
    private static final char DEFAULT_MASK_CH = '?';
    private static final String DEFAULT_CHARSET = STRING_AZ_L + STRING_09;

    private final static String[] OPTIONS = {
            "-s",
            "-t",
            "-g",
            "--md5",
            "--sha256",
            "--hash <HASH>",
            "-c, --charset <CHARSET>",
            "-d, --dict <PATH_TO_FILE>",
            "-m, --mask <MASK>",
            "--mask-ch <CHAR>",
            "--max-len <LENGTH>",
            "--min-len <LENGTH>",
            "-v, --verbose",
            "-h, --help"
    };

    private final static String[] OPTION_DESC = {
            "Run program in sequential mode.",
            "Run program in threaded mode (uses all system threads).",
            "Run program in GPU (CUDA) mode.",
            "Select encryption algorithm MD5.",
            "Select encryption algorithm SHA-256.",
            "Input hash to be cracked.",
            "Charset to use for password cracking. Available macros: '[A-Z]', '[a-z]', '[0-9]', everything " +
                    "else is going to be parsed as a separate character. Default value is '"+ DEFAULT_CHARSET +"'. Example: '-c [a-z][A-Z][0-9]!?@#$'",
            "Dictionary attack. Input a path to a plain text file with passwords.",
            "Mask out known characters. Default mask character: '" + DEFAULT_MASK_CH + "'. Example: '-m user????', this will only generate permutations for " +
                    "unknown characters '?' with the specified charset.",
            "Set mask character to the specified character.",
            "Max length for password. Default value is "+ DEFAULT_MAX +". If mask is set, max length = mask length.",
            "Min length for password. Default value is "+ DEFAULT_MIN +". If mask is set, min length = mask length.",
            "Print out every single permutation. VERY SLOW!",
            "Show this screen."
    };

    private static String charset = buildCharset(STRING_AZ_L + STRING_09);
    private static String hash = "";
    private static ProgramType type = null;
    private static HashAlgorithm algo = null;
    private static String mask = "";
    private static String dictPath = "";
    private static char maskCh = DEFAULT_MASK_CH;
    private static int max = DEFAULT_MAX;
    private static int min = DEFAULT_MIN;
    private static boolean isVerbose = false;
    private static BruteForceManager manager;

    private static int testFragments = 20;

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        if (!DEBUG) {
            if (args.length == 0) {
                printHelp();
                return;
            }
            parseArgs(args);
        } else {
            Logger.debug("!!! RUNNING IN DEBUG MODE !!!");
            type = ProgramType.THREADED;
            algo = HashAlgorithm.MD5;
            // "1945hr"
            hash = "71e50ae29377c232b34b79a7b5900c01";
            mask = "";
            charset = buildCharset("[a-z][0-9]");
            max = 6;
//            dictPath = "./dict/rockyou.txt";
        }

        System.out.println("threads: " + THREADS);
        System.out.println("type: " + type);
        System.out.println("algo: " + algo);
        System.out.println("hash: " + hash);
        System.out.println("mask: " + mask);
        System.out.println("charset: " + charset);
        System.out.println("length: " + max);
        System.out.println("dictPath: " + dictPath);

        if (!canRun()) {
            Logger.error("Missing parameters!");
            printHelp();
            System.exit(1);
        }

        long dictTime = 0;
        if (!dictPath.isEmpty()) {
            try {
                long dictStart = System.currentTimeMillis();
                if (DictionaryAttack.start(hash, algo, dictPath)) {
                    dictTime = System.currentTimeMillis() - dictStart;
                    Logger.info("Finished in " + dictTime + " ms");
                    return;
                }
            } catch (IOException e) {
                Logger.error(e.getMessage());
            }
        }

        if (type.equals(ProgramType.SEQUENTIAL)) {
            runSequential(start, dictTime);
        } else if (type.equals(ProgramType.THREADED)) {
            runThreaded(start, dictTime);
        } else if (type.equals(ProgramType.CUDA)) {
            runCuda(start, dictTime);
        }
    }

    private static void runCuda(long start, long dictTime) {
    }

    private static void runThreaded(long start, long dictTime) {
        Logger.info("Started in threaded mode...");
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);

        BigInteger allPerms;
        int maxCh = charset.length();
        if (mask.isEmpty()) {
            allPerms = calculateAllPermutations(min, max, maxCh);
        } else {
            allPerms = calculateAllPermutations(mask, maskCh, maxCh);
        }

        // TODO: find/calculate best fragment size
        int fragments = 100;
        int latchSize = Math.min(fragments, THREADS);
        BigInteger chunk = allPerms.divide(BigInteger.valueOf(fragments));
        BigInteger chunkRem = allPerms.mod(BigInteger.valueOf(fragments));
        manager = new BruteForceManager(latchSize, false);
        UI ui = new UI(allPerms, manager, UPDATE_MS);

        ArrayList<Callable<ArrayList<byte[]>>> tasks = new ArrayList<>();
        for (int i = 0; i < fragments; i++) {
            BigInteger count = chunk;
            if (i == fragments - 1) {
                count = count.add(chunkRem);
            }
            if (mask.isEmpty()) {
                tasks.add(new BruteForceTask(
                        hash,
                        algo,
                        charset,
                        max,
                        chunk.multiply(BigInteger.valueOf(i)),
                        count,
                        manager
                ));
            } else {
                tasks.add(new BruteForceTask(
                        hash,
                        algo,
                        charset,
                        mask,
                        maskCh,
                        chunk.multiply(BigInteger.valueOf(i)),
                        count,
                        manager
                ));
            }
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

        printStats(time, dictTime, manager.getTotalProgress());
        // not counting shutdown into runtime, since it takes a really long time
        pool.shutdown();
    }


    private static void runSequential(long start, long dictTime) {
        Logger.info("Started in sequential mode...");
        Logger.info("Computing for: " + hash);
        StringConsumer strCons = new StringConsumer(hash, algo);
        StringProducer strProd;
        if (mask.isEmpty()) {
            strProd = new StringProducer(charset, min, max);
        } else {
            strProd = new StringProducer(charset, mask, maskCh);
        }
        BigInteger allPerms = strProd.getAllPermutations();
        long timerStart = System.currentTimeMillis();
        String str = strProd.produceNext();
        while (str != null) {
            long timerEnd = System.currentTimeMillis();
            if (timerEnd - timerStart > UPDATE_MS) {
                timerStart = timerEnd;
                System.out.println(strProd.getProgress() + "/" + allPerms);
            }
            if (isVerbose) {
                System.out.println("Trying: " + str);
            }
            if (strCons.matchToTarget(str)) {
                Logger.debug("Found match: " + str);
                break;
            }
            str = strProd.produceNext();
        }
        long end = System.currentTimeMillis();
        long time = end - start;
        printStats(time, dictTime, strProd.getCurrPerm());
    }

    private static void printStats(long time, long dictTime, BigInteger tries) {
        Logger.info("Finished in: " + time + " ms");
        Logger.info("Total attempts: " + tries);
        Logger.info("Hashing speed: " + tries.divide(BigInteger.valueOf(time - dictTime)).divide(BigInteger.valueOf(1000)) + " MH/s");
        Logger.info("Shutting down...");
    }

    private static boolean canRun() {
        return (
                !charset.isEmpty() &&
                !hash.isEmpty() &&
                type != null &&
                algo != null
        );
    }

    private static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    private static String padLeft(String s, int n) {
        return String.format("%" + n + "s", s);
    }

    private static void printHelp() {
        StringBuilder sb = new StringBuilder();
        int max = 0;
        System.out.println("Usage: java -jar passcrack.jar [options]");
        System.out.println("Options:");

        for (String opt : OPTIONS) {
            if (opt.length() > max) {
                max = opt.length();
            }
        }

        for (int i = 0; i < OPTIONS.length; i++) {
            sb.setLength(0);
            sb.append(padLeft("", 5));
            sb.append(padRight(OPTIONS[i], max + 1));
            sb.append(OPTION_DESC[i]);
            System.out.println(sb);
        }
    }

    private static ProgramType parseType(String type) {
        return switch (type) {
            case "s" -> ProgramType.SEQUENTIAL;
            case "t" -> ProgramType.THREADED;
            case "c" -> ProgramType.CUDA;
            default -> null;
        };
    }

    private static String buildCharset(String str) {
        if (str.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (str.contains(STRING_AZ_L)) {
            for (char i = 'a'; i <= 'z'; i++) {
                sb.append(i);
            }
            str = str.replace(STRING_AZ_L, "");
        }
        if (str.contains(STRING_AZ_U)) {
            for (char i = 'A'; i <= 'Z'; i++) {
                sb.append(i);
            }
            str = str.replace(STRING_AZ_U, "");
        }
        if (str.contains(STRING_09)) {
            for (char i = '0'; i <= '9'; i++) {
                sb.append(i);
            }
            str = str.replace(STRING_09, "");
        }
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (!sb.toString().contains(String.valueOf(ch))) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String opt = args[i];

            // valueless options
            if (opt.equals("-h") ||  opt.equals("--help")) {
                printHelp();
                break;
            }
            switch (opt) {
                case "-s":
                    type = ProgramType.SEQUENTIAL;
                    continue;
                case "-t":
                    type = ProgramType.THREADED;
                    continue;
                case "-g":
                    type = ProgramType.CUDA;
                    continue;
                case "--md5":
                    algo = HashAlgorithm.MD5;
                    continue;
                case "--sha256":
                    algo = HashAlgorithm.SHA256;
                    continue;
                case "-v":
                case "--verbose":
                    isVerbose = true;
                    continue;
            }

            // options with values
            if (i == args.length - 1) {
                System.out.println("Missing required value for '" + opt + "'");
                break;
            }

            boolean isValueValid = false;
            String value = args[i + 1];
            switch (opt) {
                case "--hash":
                    if (value.isEmpty()) {
                        break;
                    }
                    hash = value;
                    isValueValid = true;
                    break;
                case "-d":
                case "--dict":
                    if (value.isEmpty()) {
                        break;
                    }
                    File file = new File(value);
                    if (file.exists()) {
                        dictPath = value;
                        isValueValid = true;
                    }
                    break;
                case "-m":
                case "--mask":
                    if (value.isEmpty()) {
                        break;
                    }
                    mask = value;
                    isValueValid = true;
                    break;
                case "--mask-ch":
                    if (value.isEmpty()) {
                        break;
                    }
                    if (value.length() == 1) {
                        maskCh = value.charAt(0);
                        isValueValid = true;
                    }
                    break;
                case "--max-len":
                    try {
                        max = Integer.parseInt(value);
                        if (max > 0) {
                            isValueValid = true;
                        }
                    }  catch (NumberFormatException e) {
                        break;
                    }
                    break;
                case "--min-len":
                    try {
                        min = Integer.parseInt(value);
                        if (min > 0) {
                            isValueValid = true;
                        }
                    }  catch (NumberFormatException e) {
                        break;
                    }
                    break;
                case "-c":
                case "--charset":
                    String tmpCharset = buildCharset(value);
                    if (tmpCharset.isEmpty()) {
                        break;
                    }
                    charset = tmpCharset;
                    isValueValid = true;
                    break;
                default:
                    System.out.println("Unknown option: '" + opt + "'");
                    return;
            }

            if (!isValueValid) {
                System.out.println("Invalid value '" + value + "' for option '" + opt + "'");
                return;
            }

            // skip value
            i++;
        }
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

    public static void shutdownThreaded() {
        if (manager == null) {
            return;
        }
        manager.shutdown();
    }
}