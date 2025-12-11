import java.util.Arrays;
import java.util.HexFormat;

public class StringConsumer {
    private final byte[] targetHash;
    private final HashAlgorithm algorithm;

    public StringConsumer(String targetHash, HashAlgorithm targetAlgorithm) {
        // parse hex to byte[], because Hasher returns byte[]
        this.targetHash = HexFormat.of().parseHex(targetHash);
        this.algorithm = targetAlgorithm;
    }

    /// This function hashes given String and equates it to 'targetHash', if they match, returns true.
    public boolean matchToTarget(String str) {
        byte[] currentHash = Hasher.hash(str, algorithm);
        return Arrays.equals(currentHash, targetHash);
    }
}
