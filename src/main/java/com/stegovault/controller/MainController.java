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

}
