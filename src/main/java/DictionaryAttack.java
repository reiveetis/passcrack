import util.Logger;

import java.io.*;
import java.util.Arrays;
import java.util.HexFormat;

public class DictionaryAttack {
    private static byte[] hash(String hash, HashAlgorithm algo) {
        byte[] temp = null;
        if (algo.equals(HashAlgorithm.MD5)) {
            temp = Hasher.hashMD5(hash);
        } else if (algo.equals(HashAlgorithm.SHA256)) {
            temp = Hasher.hashSHA256(hash);
        }
        return temp;
    }

    public static boolean start(String target, HashAlgorithm algo, String path) throws IOException {
        long start = System.currentTimeMillis();
        Logger.info("Started dictionary attack...");
        byte[] targetBytes = HexFormat.of().parseHex(target);
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                byte[] bytes = hash(line, algo);
                if (Arrays.equals(bytes, targetBytes)) {
                    Logger.debug("Found match: " + line);
                    return true;
                }
            }
        }
        long end = System.currentTimeMillis();
        Logger.info("Dictionary attack finished in " + (end - start) + " ms");
        return false;
    }
}

