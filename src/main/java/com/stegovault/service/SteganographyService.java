package com.stegovault.service;

import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Service
public class SteganographyService {
// METHOD 1: encode()
    // PURPOSE: Hide secret data INSIDE an image using LSB technique
    // PARAMETERS:
    //   imageBytes       = the original PNG image as raw bytes
    //   uuid             = unique ID linking this image to its key in database
    //   encryptedMessage = the AES encrypted message (already encrypted)
    // RETURNS: Modified PNG image as bytes (looks identical to original)
    public byte[] encode(byte[] imageBytes, String uuid,
                         String encryptedMessage) throws Exception {
        // ── STEP 1: Load the image from bytes ──
        // ByteArrayInputStream wraps our byte array so ImageIO can read it
        // ImageIO.read() converts those bytes into a BufferedImage object
        // BufferedImage lets us access individual pixels with getRGB(x, y)
        BufferedImage image = ImageIO.read(
            new ByteArrayInputStream(imageBytes));

        if (image == null) {
            throw new IllegalArgumentException(
                "Invalid image. PNG format required.");
        }
        // ── STEP 2: Build the payload ──
        // Payload = the data we want to hide inside the image
        // Format: "uuid:encryptedMessage"
        // Example: "a1b2c3:x9Kp2mN8qR..."
        // We combine them with ":" so we can split them apart when decoding
        String payload = uuid + ":" + encryptedMessage;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        // ── STEP 3: Check if image is large enough ──
        // We need to store:
        //   32 bits  = a header that tells the decoder HOW MANY bits of data follow
        //   + (payloadBytes.length × 8) bits = the actual payload data
        //   (×8 because each byte = 8 bits)
        int bitsNeeded = 32 + (payloadBytes.length * 8);
        int bitsAvailable = image.getWidth() * image.getHeight() * 3;

        if (bitsNeeded > bitsAvailable) {
            throw new IllegalArgumentException(
                "Image too small! Use a larger PNG image.");
        }
        // ── STEP 4: Convert payload to individual bits ──
        // We need to work bit by bit (LSB replaces one bit at a time)
        // Create an array to hold every individual bit of our payload
        int[] bits = new int[bitsNeeded];
        int payloadBitLength = payloadBytes.length * 8;
         // ── STEP 5: Write the 32-bit length header ──
        // The header tells the decoder: "read exactly this many bits"
        // Without this, the decoder wouldn't know when to stop reading
        //
        // HOW IT WORKS - extracting each bit of payloadBitLength:
        // >> (31 - i) = right shift to bring bit i to position 0
        // & 1         = AND with 1 to extract just that single bit
        for (int i = 0; i < 32; i++) {
            bits[i] = (payloadBitLength >> (31 - i)) & 1;
        }
        // ── STEP 6: Write the payload bits after the header ──
        // Loop through each byte of the payload
        // Extract bit b from payloadBytes[i]
                // >> (7-b) shifts the target bit to position 0
                // & 1 extracts just that one bit
                // Store it in the bits array after the 32-bit header
        for (int i = 0; i < payloadBytes.length; i++) {
            for (int b = 0; b < 8; b++) {
                bits[32 + (i * 8) + b] = (payloadBytes[i] >> (7 - b)) & 1;
            }
        }
    // ── STEP 7: Embed bits into pixel LSBs ──
        // Go through every pixel in the image, row by row
        // Replace the LSB of R, G, B channels with our secret bits
         // tracks which bit we're currently embedding
        // "outer:" is a label for the outer loop
        // We use it so "break outer" can exit BOTH loops at once
        // (normally "break" only exits the innermost loop)
        int bitIndex = 0;
        outer:
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (bitIndex >= bitsNeeded) break outer;

                int pixel = image.getRGB(x, y);

                int alpha = (pixel >> 24) & 0xFF;
                int red   = (pixel >> 16) & 0xFF;
                int green = (pixel >>  8) & 0xFF;
                int blue  =  pixel        & 0xFF;

                if (bitIndex < bitsNeeded)
                    red   = (red   & 0xFE) | bits[bitIndex++];
                if (bitIndex < bitsNeeded)
                    green = (green & 0xFE) | bits[bitIndex++];
                if (bitIndex < bitsNeeded)
                    blue  = (blue  & 0xFE) | bits[bitIndex++];

                image.setRGB(x, y,
                    (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        // ── STEP 8: Convert modified image back to PNG bytes ──
        // ByteArrayOutputStream collects the written bytes in memory
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }
    // METHOD 2: decode()
    // PURPOSE: Extract hidden data from an image - EXACT REVERSE of encode()
    // PARAMETERS:
    //   imageBytes = the stego PNG image (the one with hidden data)
    // RETURNS: String array with 2 elements:
    //   [0] = the UUID  (to look up the AES key in database)
    //   [1] = the encrypted message (to decrypt with EncryptionService)
    public String[] decode(byte[] imageBytes) throws Exception {
        // ── STEP 1: Load the image ──
        // Same as encode - convert bytes to BufferedImage
        BufferedImage image = ImageIO.read(
            new ByteArrayInputStream(imageBytes));

        if (image == null) {
            throw new IllegalArgumentException("Invalid image file.");
        }
    // ── STEP 2: Read ALL LSBs from every pixel ──
        // We read in EXACTLY the same order we wrote in encode()
        // Row by row, left to right, Red then Green then Blue

        // Calculate total bits available (width × height × 3 channels)
        // Array to store every LSB we read from the image
        int totalPixelBits = image.getWidth() * image.getHeight() * 3;
        int[] bits = new int[totalPixelBits];
        int bitIndex = 0;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                bits[bitIndex++] = (pixel >> 16) & 1;
                bits[bitIndex++] = (pixel >>  8) & 1;
                bits[bitIndex++] =  pixel        & 1;
            }
        }
    // ── STEP 3: Read the 32-bit length header ──
        // First 32 bits tell us how many bits of payload data follow
        // We reconstruct the integer from 32 individual bits
        //
        // HOW IT WORKS - rebuilding integer from bits:
        // Start with 0, then for each bit:
        //   shift left by 1 (makes room for next bit)
        //   OR with the current bit (adds it in)
        int payloadBitLength = 0;
        for (int i = 0; i < 32; i++) {
            payloadBitLength = (payloadBitLength << 1) | bits[i];
        }
     // ── STEP 4: Validate the header ──
        // If payloadBitLength is 0 or negative - no data was hidden
        // If it's larger than available bits - image is wrong or corrupted
        if (payloadBitLength <= 0 ||
            payloadBitLength > totalPixelBits - 32) {
            throw new IllegalArgumentException(
                "No hidden data found in this image.");
        }
    // ── STEP 5: Reconstruct payload bytes from bits ──
        // Convert payloadBitLength from bits to bytes (divide by 8)
        int payloadByteLength = payloadBitLength / 8;
        byte[] payloadBytes = new byte[payloadByteLength];

        for (int i = 0; i < payloadByteLength; i++) {
            int byteVal = 0;
            for (int b = 0; b < 8; b++) {
                byteVal = (byteVal << 1) | bits[32 + (i * 8) + b];
            }
            payloadBytes[i] = (byte) byteVal;
        }
     // ── STEP 6: Convert bytes back to String ──
        // new String(bytes, charset) converts byte array to String
        // UTF_8 must match the encoding used in encode()
        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        // ── STEP 7: Split "UUID:EncryptedMessage" into two parts ──
        // indexOf(':') finds the position of the first colon character
        // Returns -1 if colon not found (means payload is corrupted)
        int colon = payload.indexOf(':');

        if (colon == -1) {
            throw new IllegalArgumentException("Payload corrupted.");
        }

        return new String[]{
            payload.substring(0, colon),
            payload.substring(colon + 1)
        };
    }
}