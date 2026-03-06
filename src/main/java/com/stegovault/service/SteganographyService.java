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
            bits[i] = (payloadBitLength >> (31 - i))