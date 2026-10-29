package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "invitations")
public class Invitation {

    @Id
    private String id;

    @Indexed
    private String email;

    private String codeHash;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean active;

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getCodeHash() { return codeHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isActive() { return active; }

    public void setId(String id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setActive(boolean active) { this.active = active; }
}
