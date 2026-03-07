package com.stegovault.controller;

import com.stegovault.entity.SecretKey;
import com.stegovault.repository.SecretKeyRepository;
import com.stegovault.service.EncryptionService;
import com.stegovault.service.SteganographyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController // This class handles HTTP requests and returns JSON/files
@RequestMapping("/api") // All endpoints start with /api
@CrossOrigin(origins = "*") // Allow requests from any origin (browser)
@RequiredArgsConstructor // Lombok: auto-inject dependencies via constructor
@Slf4j // Lombok: gives us log.info(), log.error(), etc.
public class MainController {

    // Spring automatically injects these (Dependency Injection)
    private final SecretKeyRepository secretKeyRepository;
    private final EncryptionService encryptionService;
    private final SteganographyService steganographyService;

    // ── ENCODE ENDPOINT ──
    // POST /api/encode
    // Receives: image + message + password
    // Returns: steganographic PNG image with hidden encrypted payload
    @PostMapping("/encode")
    public ResponseEntity<?> encode(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam("message") String message,
            @RequestParam("password") String password) {

        log.info("Encode request: image={}, msgLength={}",
                imageFile.getOriginalFilename(), message.length());

        try {
            // Basic validation
            if (imageFile.isEmpty())
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No image provided"));
            if (message.isBlank())
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Message cannot be empty"));
            if (password.length() < 4)
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Password too short (min 4 chars)"));
            if (!imageFile.getContentType().equals("image/png"))
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only PNG images supported"));

            // Step 1: Generate unique ID and AES key for this message
            String uuid = UUID.randomUUID().toString();
            String aesKey = encryptionService.generateAesKey();

            // Step 2: Save key to database (THE KEY VAULT)
            secretKeyRepository.save(
                    SecretKey.builder()
                            .uuid(uuid)
                            .aesKey(aesKey)
                            .build());
            log.info("Key saved to vault for UUID: {}", uuid);

            // Step 3: Encrypt the message
            String encrypted = encryptionService.encrypt(
                    message, aesKey, password);

            // Step 4: Hide UUID + encrypted message in image LSBs
            byte[] stegoImage = steganographyService.encode(
                    imageFile.getBytes(), uuid, encrypted);

            log.info("Encoding complete. Image size: {} bytes",
                    stegoImage.length);

            // Step 5: Return the stego image as a downloadable file
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData(
                    "attachment", "stegovault_secret.png");

            return new ResponseEntity<>(stegoImage, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Encode failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

}
