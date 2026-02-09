import util.Logger;

import java.io.*;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HexFormat;

public class DictionaryAttack {
    public static boolean start(String target, HashAlgorithm algo, String path) throws IOException {
        long start = System.currentTimeMillis();
        Logger.info("Started dictionary attack...");
        byte[] targetBytes = HexFormat.of().parseHex(target);
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            BigInteger counter = BigInteger.ZERO;
            long timer = System.currentTimeMillis();
            String line;
            while ((line = br.readLine()) != null) {
                if (System.currentTimeMillis() - timer > 1000) {
                    System.out.println("tried " + counter + " passwords");
                    timer = System.currentTimeMillis();
                }
                counter = counter.add(BigInteger.ONE);
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

