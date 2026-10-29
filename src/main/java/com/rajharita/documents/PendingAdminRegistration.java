package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "pending_admin_registrations")
public class PendingAdminRegistration {

    @Id
    private String id;

    private String name;
    private String username;
    private String email;
    private String passwordHash;
    private String otpHash;
    private String requestedFrom;
    private int attempts;
    private LocalDateTime createdAt;

    @Indexed(expireAfterSeconds = 0)
    private LocalDateTime expiresAt;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getOtpHash() { return otpHash; }
    public String getRequestedFrom() { return requestedFrom; }
    public int getAttempts() { return attempts; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public void setRequestedFrom(String requestedFrom) { this.requestedFrom = requestedFrom; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
