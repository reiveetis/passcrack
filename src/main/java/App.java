import util.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {
    private final static boolean DEBUG = true;

    private final static String[] OPTIONS = {
            "-t, --type <s|t|c>",
            "-a, --algo <MD5|SHA256>",
            "--hash <HASH>",
            "-m, --mask <MASK>",
            "-l, --length <LENGTH>",
            "-c, --charset <CHARSET>",
            "-v, --verbose",
            "-h, --help"
    };

    private final static String[] OPTION_DESC = {
            "Select program type: 's' (sequential), 't' (threaded), 'c' (cuda).",
            "Select hash encryption algorithm: 'MD5' or 'SHA256'.",
            "Input hash to be cracked.",
            "Mask out known characters. Example: '-m user????', this will only generate permutations for " +
                    "unknown characters '?' with the specified charset.",
            "Max length for password. If mask is set, max length = mask length.",
            "Charset to use for password cracking. Available macros: '[A-Z]', '[a-z]', '[0-9]', everything " +
                    "else is going to be parsed as a separate character. Example: '-c [a-z][A-Z][0-9]!?@#$'",
            "Print out every single permutation. VERY SLOW!",
            "Show this screen."
    };

    private static final String STRING_09 = "[0-9]";
    private static final String STRING_AZ_L = "[a-z]";
    private static final String STRING_AZ_U = "[A-Z]";
    private static final int THREADS = Runtime.getRuntime().availableProcessors();
    public static final int UPDATE_MS = 1000;
    private static String charset = buildCharset(STRING_AZ_L + STRING_09);
    private static String hash = "";
    private static ProgramType type = null;
    private static HashAlgorithm algo = null;
    private static String mask = "";
    private static char maskCh = '?';
    private static int max = 6;
    private static int min = 1;
    private static boolean isVerbose = false;
    public static BruteForceManager manager;

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
            type = ProgramType.SEQUENTIAL;
            algo = HashAlgorithm.MD5;
            // "1945hr"
            hash = "71e50ae29377c232b34b79a7b5900c01";
            mask = "";
            charset = buildCharset("[a-z][0-9]");
            max = 0;
        }

        System.out.println("threads: " + THREADS);
        System.out.println("type: " + type);
        System.out.println("algo: " + algo);
        System.out.println("hash: " + hash);
        System.out.println("mask: " + mask);
        System.out.println("charset: " + charset);
        System.out.println("length: " + max);

        if (!isSetupValid()) {
            Logger.error("Missing parameters!");
            printHelp();
            System.exit(-1);
        }

        if (type.equals(ProgramType.SEQUENTIAL)) {
            runSequential(start);
        } else if (type.equals(ProgramType.THREADED)) {
            runThreaded(start);
        } else if (type.equals(ProgramType.CUDA)) {
            runCuda(start);
        }
    }

    private static void runCuda(long start) {
    }

    private static void runThreaded(long start) {
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
        int fragments = 20;
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

        Logger.info("Finished in " + time + " ms");
        Logger.info("Total attempts: " + manager.getTotalProgress());
        Logger.info(manager.getTotalProgress().divide(BigInteger.valueOf(time)).divide(BigInteger.valueOf(1000)) + " MH/s");
        Logger.info("Shutting down...");

        // not counting shutdown into runtime, since it takes a really long time
        pool.shutdown();
    }

    private static void runSequential(long start) {
        Logger.info("Started in sequential mode...");
        Logger.info("Computing for: " + hash);
        StringConsumer strCons = new StringConsumer(hash, algo);
        StringProducer strProd;
        if (mask.isEmpty()) {
            strProd = new StringProducer(charset, min, max);
        } else {
            strProd = new StringProducer(charset, mask, maskCh);
        }
        String str = strProd.produceNext();
        while (str != null) {
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
        Logger.info("Finished in " + (end - start) + " ms");
    }

    private static boolean isSetupValid() {
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

    private static HashAlgorithm parseAlgo(String algo) {
        return switch (algo) {
            case "MD5" -> HashAlgorithm.MD5;
            case "SHA256" -> HashAlgorithm.SHA256;
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
            if (opt.equals("-h") || opt.equals("--help")) {
                printHelp();
                break;
            }
            if (opt.equals("-v") || opt.equals("--verbose")) {
                isVerbose = true;
                break;
            }

            // options with values
            if (i == args.length - 1) {
                System.out.println("Missing required value for '" + opt + "'");
                break;
            }

            boolean isValueValid = false;
            String value = args[i + 1];
            switch (opt) {
                case "-t":
                case "--type":
                    ProgramType tmpType = parseType(value);
                    if (tmpType == null) {
                        break;
                    }
                    type = tmpType;
                    isValueValid = true;
                    break;
                case "-a":
                case "--algo":
                    HashAlgorithm tmpAlgo = parseAlgo(value);
                    if (tmpAlgo == null) {
                        break;
                    }
                    algo = tmpAlgo;
                    isValueValid = true;
                    break;
                case "--hash":
                    if (value.isEmpty()) {
                        break;
                    }
                    hash = value;
                    isValueValid = true;
                    break;
                case "-m":
                case "--mask":
                    if (value.isEmpty()) {
                        break;
                    }
                    mask = value;
                    isValueValid = true;
                    break;
                case "-l":
                case "--length":
                    try {
                        max = Integer.parseInt(value);
                        isValueValid = true;
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

    public static void shutdown() {
        if (manager == null) {
            return;
        }
        manager.shutdown();
    }
}