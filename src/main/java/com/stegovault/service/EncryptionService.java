package com.stegovault.service;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_MODE = "AES/CBC/PKCS5Padding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE = 16;

    public String generateAesKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE, new SecureRandom());
        byte[] keyBytes = keyGen.generateKey().getEncoded();
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    public String encrypt(String message, String aesKeyB64,
                          String password) throws Exception {
        byte[] dbKey = Base64.getDecoder().decode(aesKeyB64);
        byte[] pwKey = hashPassword(password);
        byte[] finalKey = xorArrays(dbKey, pwKey);

        byte[] iv = new byte[IV_SIZE];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_MODE);
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(finalKey, ALGORITHM),
                new IvParameterSpec(iv));

        byte[] ciphertext = cipher.doFinal(message.getBytes("UTF-8"));

        byte[] result = new byte[IV_SIZE + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, IV_SIZE);
        System.arraycopy(ciphertext, 0, result, IV_SIZE, ciphertext.length);

        return Base64.getEncoder().encodeToString(result);
    }

    public String decrypt(String encryptedB64, String aesKeyB64,
                          String password) throws Exception {
        byte[] dbKey = Base64.getDecoder().decode(aesKeyB64);
        byte[] pwKey = hashPassword(password);
        byte[] finalKey = xorArrays(dbKey, pwKey);

        byte[] data = Base64.getDecoder().decode(encryptedB64);
        byte[] iv = new byte[IV_SIZE];
        byte[] ciphertext = new byte[data.length - IV_SIZE];
        System.arraycopy(data, 0, iv, 0, IV_SIZE);
        System.arraycopy(data, IV_SIZE, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(CIPHER_MODE);
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(finalKey, ALGORITHM),
                new IvParameterSpec(iv));

        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, "UTF-8");
    }

    private byte[] hashPassword(String password) throws Exception {
        return java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(password.getBytes("UTF-8"));
    }

    private byte[] xorArrays(byte[] a, byte[] b) {
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte)(a[i] ^ b[i % b.length]);
        }
        return result;
    }
}