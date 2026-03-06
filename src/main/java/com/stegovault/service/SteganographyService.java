package com.stegovault.service;

import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Service
public class SteganographyService {

    public byte[] encode(byte[] imageBytes, String uuid,
                         String encryptedMessage) throws Exception {

        BufferedImage image = ImageIO.read(
            new ByteArrayInputStream(imageBytes));

        if (image == null) {
            throw new IllegalArgumentException(
                "Invalid image. PNG format required.");
        }

        String payload = uuid + ":" + encryptedMessage;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        int bitsNeeded = 32 + (payloadBytes.length * 8);
        int bitsAvailable = image.getWidth() * image.getHeight() * 3;

        if (bitsNeeded > bitsAvailable) {
            throw new IllegalArgumentException(
                "Image too small! Use a larger PNG image.");
        }

        int[] bits = new int[bitsNeeded];
        int payloadBitLength = payloadBytes.length * 8;

        for (int i = 0; i < 32; i++) {
            bits[i] = (payloadBitLength >> (31 - i)) & 1;
        }

        for (int i = 0; i < payloadBytes.length; i++) {
            for (int b = 0; b < 8; b++) {
                bits[32 + (i * 8) + b] = (payloadBytes[i] >> (7 - b)) & 1;
            }
        }

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

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }

    public String[] decode(byte[] imageBytes) throws Exception {

        BufferedImage image = ImageIO.read(
            new ByteArrayInputStream(imageBytes));

        if (image == null) {
            throw new IllegalArgumentException("Invalid image file.");
        }

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

        int payloadBitLength = 0;
        for (int i = 0; i < 32; i++) {
            payloadBitLength = (payloadBitLength << 1) | bits[i];
        }

        if (payloadBitLength <= 0 ||
            payloadBitLength > totalPixelBits - 32) {
            throw new IllegalArgumentException(
                "No hidden data found in this image.");
        }

        int payloadByteLength = payloadBitLength / 8;
        byte[] payloadBytes = new byte[payloadByteLength];

        for (int i = 0; i < payloadByteLength; i++) {
            int byteVal = 0;
            for (int b = 0; b < 8; b++) {
                byteVal = (byteVal << 1) | bits[32 + (i * 8) + b];
            }
            payloadBytes[i] = (byte) byteVal;
        }

        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
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