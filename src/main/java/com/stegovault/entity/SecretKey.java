package com.stegovault.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// This class represents a row in the "secret_keys" database table
// Each time someone hides a message in an image, one row is created here
@Entity
@Table(name = "secret_keys") // the actual table name in the database
@Data                        // Lombok: auto-generates getters and setters
@Builder                     // Lombok: lets us build objects like SecretKey.builder().uuid("...").build()
@NoArgsConstructor           // Lombok: creates an empty constructor
@AllArgsConstructor          // Lombok: creates a constructor with all fields
public class SecretKey {

    // This is the primary key (unique ID) for each record
    // The same UUID is also hidden inside the image using steganography
    // When decoding, we extract this UUID to find the correct key
    @Id
    @Column(name = "uuid", nullable = false, length = 36)
    private String uuid;

    // The AES encryption key saved as a Base64 string
    // This key is used to encrypt and decrypt the hidden message
    @Column(name = "aes_key", nullable = false, length = 512)
    private String aesKey;

    // Counts how many times a wrong password was entered
    // If this goes up even once, the Kill Switch fires and deletes the key
    @Column(name = "failed_attempts")
    @Builder.Default
    private Integer failedAttempts = 0; // starts at 0 by default

    // Stores the date and time when this key was created
    // Useful for tracking when a message was hidden
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // set to current time automatically
}
