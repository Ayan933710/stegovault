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

    // ── DECODE ENDPOINT WITH KILL SWITCH ──
    // POST /api/decode
    // Receives: stego image + password
    // Returns: decrypted message OR triggers Kill Switch on wrong password
    @PostMapping("/decode")
    public ResponseEntity<Map<String, String>> decode(
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam("password") String password) {

        log.info("Decode request: image={}", imageFile.getOriginalFilename());

        try {
            // Step 1: Extract hidden UUID + encrypted message from image
            String[] extracted = steganographyService.decode(
                    imageFile.getBytes());
            String uuid = extracted[0];
            String encrypted = extracted[1];
            log.info("Extracted UUID: {}", uuid);

            // Step 2: Look up AES key in database
            Optional<SecretKey> keyOpt = secretKeyRepository.findById(uuid);

            // KEY ALREADY DESTROYED (Kill Switch was triggered before)
            if (keyOpt.isEmpty()) {
                log.warn("Key not found - already destroyed: {}", uuid);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of(
                                "error", "KEY_DESTROYED",
                                "message", "This message is permanently " +
                                        "inaccessible. The Kill Switch was already " +
                                        "triggered by a previous failed attempt."));
            }

            SecretKey keyRecord = keyOpt.get();

            // Step 3: Try to decrypt
            try {
                String decrypted = encryptionService.decrypt(
                        encrypted, keyRecord.getAesKey(), password);

                // SUCCESS - correct password
                log.info("Decryption successful for UUID: {}", uuid);
                return ResponseEntity.ok(Map.of(
                        "status", "SUCCESS",
                        "message", decrypted));

            } catch (Exception decryptFailed) {

                // KILL SWITCH ACTIVATION :
                // Wrong password → decryption throws exception
                // We permanently delete the key RIGHT NOW
                log.warn("KILL SWITCH: Wrong password for UUID: {}", uuid);
                secretKeyRepository.deleteById(uuid);
                log.warn("Key PERMANENTLY DESTROYED for UUID: {}", uuid);

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "error", "KILL_SWITCH_TRIGGERED",
                                "message", "Wrong password. The Kill Switch has " +
                                        "been triggered. The encryption key has been " +
                                        "PERMANENTLY DESTROYED. This message can " +
                                        "never be recovered."));
            }

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Decode failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── STATUS ENDPOINT ──
    // GET /api/status - health check
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "status", "ONLINE",
                "activeKeys", secretKeyRepository.count(),
                "message", "StegoVault is operational"));
    }
}
