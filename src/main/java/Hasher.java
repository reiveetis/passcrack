import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hasher {
    private final static MessageDigest sha256;
    private final static MessageDigest md5;

    static {
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /// This function returns a hashed byte[] of given String and Algorithm.
    public static byte[] hash(String str, HashAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA256 -> hashSHA256(str);
            case MD5 -> hashMD5(str);
        };
    }

    /// This function returns a SHA-256 hashed byte[] of given String.
    public static byte[] hashSHA256(String str) {
        return sha256.digest(str.getBytes(StandardCharsets.UTF_8));
    }

    /// This function returns an MD5 hashed byte[] of given String.
    public static byte[] hashMD5(String str) {
        return md5.digest(str.getBytes(StandardCharsets.UTF_8));
    }
}
