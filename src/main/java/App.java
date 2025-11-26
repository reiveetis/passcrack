import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public class App {
    // "hello", length = 5, MD5
    private static final String TARGET = "5d41402abc4b2a76b9719d911017c592";
    static void main(String[] args) {
        long start = System.currentTimeMillis();
        System.out.println("Computing for: " + TARGET);
        byte[] targetBytes = HexFormat.of().parseHex(TARGET);
        StringProducer strProd = new StringProducer(5);
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