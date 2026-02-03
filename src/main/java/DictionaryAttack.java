import util.Logger;

import java.io.*;
import java.util.Arrays;
import java.util.HexFormat;

public class DictionaryAttack {
    public static boolean start(String target, HashAlgorithm algo, String path) throws IOException {
        long start = System.currentTimeMillis();
        Logger.info("Started dictionary attack...");
        byte[] targetBytes = HexFormat.of().parseHex(target);
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                byte[] bytes = Hasher.hash(line, algo);
                if (Arrays.equals(bytes, targetBytes)) {
                    Logger.debug("Match: " + line);
                    return true;
                }
            }
        }
        long end = System.currentTimeMillis();
        Logger.info("Dictionary attack finished in " + (end - start) + " ms");
        return false;
    }
}

