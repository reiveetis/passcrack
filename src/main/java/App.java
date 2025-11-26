import java.util.Arrays;
import java.util.HexFormat;

public class App {
    // "hello", length = 5, SHA-256
//    private static final String TARGET = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    // "hello", length = 5, MD5
    private static final String TARGET = "5d41402abc4b2a76b9719d911017c592";

    // "zzzzz", length = 5, MD5
//    private static final String TARGET = "95ebc3c7b3b9f1d2c40fec14415d3cb8";

    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyz";

    static void main(String[] args) {
        long start = System.currentTimeMillis();
        System.out.println("Computing for: " + TARGET);
        byte[] targetBytes = HexFormat.of().parseHex(TARGET);
        StringProducer strProd = new StringProducer(CHARSET, 5);
        String str = strProd.produceNext();
        byte[] currHash;
        while (str != null) {
//            System.out.println(str);
            currHash = Hasher.hashMD5(str);
            if (Arrays.equals(currHash, targetBytes)) {
                System.out.println("Found match: " + str);
                break;
            }
            str = strProd.produceNext();
        }
        long end = System.currentTimeMillis();
        System.out.println("Computing took: " + (end - start) + "ms");
    }
}