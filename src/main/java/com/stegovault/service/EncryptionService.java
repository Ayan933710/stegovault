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
// CIPHER_MODE = the full specification of HOW to encrypt
    // "AES" = use AES algorithm
    // "CBC" = Cipher Block Chaining mode
    //         CBC means each block of data is mixed with the previous block
    //         before encrypting - this makes it MUCH harder to crack
    //         because identical messages produce completely different outputs
    // "PKCS5Padding" = a standard way to fill empty space
    //         AES works in fixed 16-byte blocks
    //         If your message is 20 bytes, the last block has 12 empty bytes
    //         PKCS5Padding fills those empty bytes in a standard way
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_MODE = "AES/CBC/PKCS5Padding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE = 16;

    public String generateAesKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE, new SecureRandom());
         // ── DUAL KEY SYSTEM ──
        // We don't encrypt with just one key
        // We combine TWO keys using XOR:
        //   Key 1 = the random AES key stored in the DATABASE
        //   Key 2 = a key DERIVED from the user's PASSWORD
        // Both are required to decrypt - if either is wrong, decryption fails

        // Step 1: Decode the database key from Base64 back to raw bytes
        // decode() is the reverse of encodeToString() from generateAesKey()
        // Result: 32 raw bytes representing the database key
        byte[] keyBytes = keyGen.generateKey().getEncoded();
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    public String encrypt(String message, String aesKeyB64,
                          String password) throws Exception {
        byte[] dbKey = Base64.getDecoder().decode(aesKeyB64);
        byte[] pwKey = hashPassword(password);
        // Step 2: Convert the password into a 32-byte key using SHA-256 hashing
        // "hello123" password → SHA256 → always same 32 bytes
        // This is one-way - you can't reverse a hash to get the original password
        byte[] finalKey = xorArrays(dbKey, pwKey);
 // Step 3: XOR the two keys together to create the FINAL encryption key
        // XOR = exclusive OR - a bitwise operation
        // XOR truth table:  0 XOR 0 = 0
        //                   1 XOR 1 = 0
        //                   0 XOR 1 = 1
        //                   1 XOR 0 = 1
        // Magic property: (A XOR B) XOR B = A  ← it reverses itself!
        // So: to encrypt we use (dbKey XOR pwKey)
        //     to decrypt we need BOTH dbKey AND pwKey again
        //     if password is wrong → pwKey is different → finalKey is wrong → decryption FAILS
        byte[] iv = new byte[IV_SIZE];
        // Step 4: Generate a random IV (Initialization Vector)
        // new byte[IV_SIZE] creates an empty array of 16 bytes (all zeros)
        // nextBytes() fills the array with random bytes
        // This IV is different every time encrypt() is called
        // Result: same message encrypted twice = completely different ciphertext
        new SecureRandom().nextBytes(iv);
        // Step 5: Create and configure the Cipher (encryption engine)
        // getInstance(CIPHER_MODE) = get AES/CBC/PKCS5Padding cipher machine
        // init() = configure the cipher - tell it:
        //   ENCRYPT_MODE = we want to ENCRYPT (not decrypt)
        //   new SecretKeySpec(finalKey, ALGORITHM) = here's the key (wrapped properly)
        //   new IvParameterSpec(iv) = here's the random IV to use
        Cipher cipher = Cipher.getInstance(CIPHER_MODE);
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(finalKey, ALGORITHM),
                new IvParameterSpec(iv));
        // Step 6: Actually encrypt the message
        // message.getBytes("UTF-8") = convert string to raw bytes using UTF-8 encoding
        // doFinal() = process all bytes and return the encrypted result
        // If anything is wrong with the key/IV, this throws an exception
        byte[] ciphertext = cipher.doFinal(message.getBytes("UTF-8"));
        // Step 7: Combine IV + ciphertext into one array
        // We MUST store the IV alongside the ciphertext
        // Because decryption needs the same IV that was used for encryption
        // IV is NOT secret - it's okay to store it with the ciphertext
        // Format of result: [16 bytes of IV][rest is ciphertext]
        byte[] result = new byte[IV_SIZE + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, IV_SIZE);
        System.arraycopy(ciphertext, 0, result, IV_SIZE, ciphertext.length);
    // Step 8: Convert combined bytes to Base64 string for storage/transmission
        return Base64.getEncoder().encodeToString(result);
    }
    // METHOD 3: decrypt()
    // PURPOSE: Reverse of encrypt() - recover the original message
    // PARAMETERS:
    //   encryptedB64 = the encrypted data from encrypt() (Base64 format)
    //   aesKeyB64    = the same database AES key used during encryption
    //   password     = the password the user enters to decrypt
    // RETURNS: The original plain text message
    // IMPORTANT: If password is WRONG, cipher.doFinal() throws BadPaddingException
    //            That exception travels up to the Controller which fires the Kill Switch
    public String decrypt(String encryptedB64, String aesKeyB64,
                          String password) throws Exception {
        // Step 1: Rebuild the same finalKey using database key + password
        // This is IDENTICAL to what was done in encrypt()
        // If the password is wrong → pwKey is different → finalKey is wrong                    
        byte[] dbKey = Base64.getDecoder().decode(aesKeyB64);
        byte[] pwKey = hashPassword(password);
        byte[] finalKey = xorArrays(dbKey, pwKey);
        // Step 2: Decode the Base64 encrypted string back to raw bytes
        // This gives us the combined [IV + ciphertext] array from encrypt()
        byte[] data = Base64.getDecoder().decode(encryptedB64);
        // Step 3: Split the data back into IV and ciphertext
        // Remember the format from encrypt(): [16 bytes IV][rest is ciphertext]
        byte[] iv = new byte[IV_SIZE];
        byte[] ciphertext = new byte[data.length - IV_SIZE];
        System.arraycopy(data, 0, iv, 0, IV_SIZE);
        System.arraycopy(data, IV_SIZE, ciphertext, 0, ciphertext.length);
        
        
        // Step 4: Create and configure cipher for DECRYPTION
        // DECRYPT_MODE = we want to DECRYPT (opposite of encrypt)
        // Same key and IV must be used as during encryption
        Cipher cipher = Cipher.getInstance(CIPHER_MODE);
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(finalKey, ALGORITHM),
                new IvParameterSpec(iv));
        // Step 5: Decrypt the ciphertext
        // ⚠️ THIS IS THE CRITICAL LINE ⚠️
        // If finalKey is WRONG (because password was wrong):
        //   → PKCS5 padding at the end won't match
        //   → Java throws BadPaddingException
        //   → That exception propagates to the Controller
        //   → Controller catches it and fires the KILL SWITCH
        //   → The AES key is permanently deleted from the database
        byte[] plaintext = cipher.doFinal(ciphertext);
        // Step 6: Convert decrypted bytes back to a readable String
        // "UTF-8" must match the encoding used in encrypt()
        return new String(plaintext, "UTF-8");
    }
    //HELPER METHOD 1: hashPassword()
    // PURPOSE: Convert a password string into exactly 32 bytes
    //          using SHA-256 hashing algorithm
    // WHY: AES-256 needs exactly 32 bytes as a key
    //      Passwords can be any length - hashing standardizes them
    // PROPERTY: Same password ALWAYS produces same 32 bytes
    //           Different passwords ALWAYS produce different 32 bytes
    //           You CANNOT reverse a hash to get the original password
    private byte[] hashPassword(String password) throws Exception {
        return java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(password.getBytes("UTF-8"));
    }
    // HELPER METHOD 2: xorArrays()
    // PURPOSE: XOR two byte arrays together element by element
    // EXAMPLE: xorArrays([10110101], [01001011])
    //                             = [11111110]
    // KEY PROPERTY: xorArrays(xorArrays(A, B), B) = A
    //               Applying XOR twice with same key reverses it
    //               This is why XOR works for encryption
    private byte[] xorArrays(byte[] a, byte[] b) {
        byte[] result = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = (byte)(a[i] ^ b[i % b.length]);
        }
        return result;
    }
}