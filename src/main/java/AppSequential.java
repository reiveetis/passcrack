import java.util.Scanner;

public class AppSequential {
    // "hello", length = 5, SHA-256
//    private static final String TARGET = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    // "hello", length = 5, MD5
//    private static final String TARGET = "5d41402abc4b2a76b9719d911017c592";

    // "zzzzz", length = 5, MD5
    private static final String TARGET = "95ebc3c7b3b9f1d2c40fec14415d3cb8";


    private static final HashAlgorithm TARGET_ALGO = HashAlgorithm.MD5;
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 5;
    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
//    private static final String CHARSET = "0123456789";
    private static final boolean VERBOSE = false;
    private static final boolean VERBOSE_WAIT_FOR_INPUT = false;
    private static final String MASK = "";
    private static final char MASK_CH = '.';

    // TODO: CLI?
    // TODO: add user input
    // TODO: add in-app string hashing so you don't need to copy paste every time
    // TODO: hash bank
    // - hash bank would need a trie
    // - rockyou.txt
    // - check when it's worth using hash bank vs. brute force
    static void main(String[] args) {
        long start = System.currentTimeMillis();
        Scanner sc = new Scanner(System.in);
        System.out.println("Computing for: " + TARGET);
        StringConsumer strCons = new StringConsumer(TARGET, TARGET_ALGO);
        StringProducer strProd;
        if (MASK == "") {
            strProd = new StringProducer(CHARSET, MIN_LENGTH, MAX_LENGTH);
        } else {
            strProd = new StringProducer(CHARSET, MASK, MASK_CH);
        }
        String str = strProd.produceNext();
        while (str != null) {
//            if (strProd.shouldShowProgress()) {
//                System.out.println(strProd.getProgress() + "%");
//            }
            if (VERBOSE) {
                System.out.println("Trying: " + str);
                if (VERBOSE_WAIT_FOR_INPUT) {
                    sc.nextLine();
                }
            }
            if (strCons.matchToTarget(str)) {
                System.out.println("Found match: " + str);
                break;
            }
            str = strProd.produceNext();
        }
        long end = System.currentTimeMillis();
        System.out.println("Computing took: " + (end - start) + "ms");
    }
}