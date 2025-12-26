import util.Logger;

import java.util.Arrays;

public class App {
    private final static boolean DEBUG = true;

    private final static String[] OPTIONS = {
            "-t, --type <s|t|c>",
            "-a, --algo <MD5|SHA256>",
            "--hash <HASH>",
            "-m, --mask <MASK>",
            "-l, --length <LENGTH>",
            "-c, --charset <CHARSET>",
            "-h, --help"
    };

    private final static String[] OPTION_DESC = {
            "Select approach type: 's' (sequential), 't' (threaded), 'c' (cuda).",
            "Select hash encryption algorithm: 'MD5' or 'SHA256'.",
            "Input hash to be cracked.",
            "Mask out known characters. Example: '-m user????', this will only generate permutations for " +
                    "unknown characters '?' with the specified charset.",
            "Max length for password. If mask is set, max length = mask length.",
            "Charset to use for password cracking. Available macros: '[A-Z]', '[a-z]', '[0-9]', everything " +
                    "else is going to be parsed as a separate character. Example: '-c [a-z][A-Z][0-9]!?@#$'",
            "Show this screen."
    };

    private static final String STRING_09 = "[0-9]";
    private static final String STRING_AZ_L = "[a-z]";
    private static final String STRING_AZ_U = "[A-Z]";
    private static final int THREADS = Runtime.getRuntime().availableProcessors();
    private static String charset = null;
    private static ApproachType type = null;
    private static HashAlgorithm algo = null;
    private static String hash = null;
    private static String mask = null;
    private static int length = -1;

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

    private static ApproachType parseType(String type) {
        return switch (type) {
            case "s" -> ApproachType.SEQUENTIAL;
            case "t" -> ApproachType.THREADED;
            case "c" -> ApproachType.CUDA;
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
                    ApproachType tmpType = parseType(value);
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
                        length = Integer.parseInt(value);
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

    public static void main(String[] args) {
        if (!DEBUG) {
            if (args.length == 0) {
                printHelp();
                return;
            }
            parseArgs(args);
        } else {
            Logger.debug("!!! RUNNING IN DEBUG MODE !!!");
            type = ApproachType.THREADED;
            algo = HashAlgorithm.MD5;
            // "1945hr"
            hash = "71e50ae29377c232b34b79a7b5900c01";
            charset = buildCharset("[a-z][0-9]");
        }

        System.out.println("threads: " + THREADS);
        System.out.println("type: " + type);
        System.out.println("algo: " + algo);
        System.out.println("hash: " + hash);
        System.out.println("mask: " + mask);
        System.out.println("charset: " + charset);
        System.out.println("length: " + length);

        if (type.equals(ApproachType.SEQUENTIAL)) {
            runSequential();
        } else if (type.equals(ApproachType.THREADED)) {
            runThreaded();
        } else if (type.equals(ApproachType.CUDA)) {
            runCuda();
        }
    }

    private static void runCuda() {
    }

    private static void runThreaded() {
    }

    private static void runSequential() {
    }
}