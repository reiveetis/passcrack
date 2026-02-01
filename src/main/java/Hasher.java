import util.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hasher {
    /// This function returns a hashed byte[] of given String and Algorithm.
    public static byte[] hash(String str, HashAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA256 -> hashSHA256(str.getBytes());
            case MD5 -> hashMD5(str.getBytes());
        };
    }

    public static byte[] hash(byte[] bytes, HashAlgorithm algorithm) {
        return switch (algorithm) {
            case SHA256 -> hashSHA256(bytes);
            case MD5 -> hashMD5(bytes);
        };
    }

    /// This function returns a SHA-256 hashed byte[] of given String.
    public static byte[] hashSHA256(byte[] bytes) {
        MessageDigest sha256 = null;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            Logger.debug(e.getMessage());
        }
        return sha256.digest(bytes);
    }

    /// This function returns an MD5 hashed byte[] of given String.
    public static byte[] hashMD5(byte[] bytes) {
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Logger.debug(e.getMessage());
        }
        return md5.digest(bytes);
    }
}
