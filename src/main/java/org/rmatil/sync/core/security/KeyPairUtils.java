package org.rmatil.sync.core.security;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class KeyPairUtils {

    public static RSAPrivateKey privateKeyFromString(String pkcs8EncodedPrivateKey)
            throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        byte[] bytes = KeyPairUtils.hexStringToByteArray(pkcs8EncodedPrivateKey);

        return KeyPairUtils.privateKeyFromBytes(bytes);
    }

    public static RSAPrivateKey privateKeyFromBytes(byte[] pkcs8EncodedPrivateKey)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(pkcs8EncodedPrivateKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(privateKeySpec);
    }

    public static RSAPublicKey publicKeyFromString(String x509EncodedPublicKey)
            throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        byte[] bytes = KeyPairUtils.hexStringToByteArray(x509EncodedPublicKey);

        return KeyPairUtils.publicKeyFromBytes(bytes);
    }

    public static RSAPublicKey publicKeyFromBytes(byte[] x509EncodedPublicKey)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(x509EncodedPublicKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(publicKeySpec);
    }

    public static String privateKeyToString(RSAPrivateKey privateKey) {
        byte[] bytes = KeyPairUtils.privateKeyToBytes(privateKey);

        return KeyPairUtils.byteToHexString(bytes);
    }

    public static byte[] privateKeyToBytes(RSAPrivateKey privateKey) {
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());

        return pkcs8EncodedKeySpec.getEncoded();
    }

    public static String publicKeyToString(RSAPublicKey publicKey) {
        byte[] bytes = KeyPairUtils.publicKeyToBytes(publicKey);

        return KeyPairUtils.byteToHexString(bytes);
    }

    public static byte[] publicKeyToBytes(RSAPublicKey publicKey) {
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey.getEncoded());

        return x509EncodedKeySpec.getEncoded();
    }

    /**
     * Convert the given byte array to its hex representation.
     * From <a href="http://stackoverflow.com/questions/2817752/java-code-to-convert-byte-to-hexadecimal">http://stackoverflow.com/questions/2817752/java-code-to-convert-byte-to-hexadecimal</a>
     *
     * @param data The byte array to be converted.
     *
     * @return The hex representation of the given byte array
     */
    public static String byteToHexString(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }

        return sb.toString();
    }

    /**
     * Convert the given hex string to its array representation.
     * From <a href="http://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java">http://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java</a>
     *
     * @param hexString The hex string
     *
     * @return Its array representation
     */
    public static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

}
