package com.stegovault.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// This class is like a table in the database
// Every time someone hides a message in an image, one row is added here
@Entity
@Table(name = "secret_keys") // table ka naam database mein
@Data           // automatically getters and setters banata hai
@Builder        // object banane mein help karta hai
@NoArgsConstructor  // empty constructor banata hai
@AllArgsConstructor // sab fields wala constructor banata hai
public class SecretKey {

    // Unique ID for each message - like a roll number
    // This same ID is hidden inside the image
    @Id
    @Column(name = "uuid", nullable = false, length = 36)
    private String uuid;

    // The secret key used to lock and unlock the message
    // Stored as a text string in the database
    @Column(name = "aes_key", nullable = false, length = 512)
    private String aesKey;

    // Counts wrong password attempts
    // Even 1 wrong attempt = key deleted forever (Kill Switch!)
    @Column(name = "failed_attempts")
    @Builder.Default
    private Integer failedAttempts = 0; // starts from zero

    // Records the time when this key was created
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // current time
}