package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "farmers")
public class Farmer {

    @Id
    private String id;

    private String name;
    private String village;
    private String phone;
    private String cropType;
    private String quantity;
    private String preferredContact;   // Call / WhatsApp / Email
    private String message;
    private String status;             // NEW / CONTACTED / CLOSED
    private String language;           // en / hi
    private LocalDateTime createdAt;

    public Farmer() {
    }

    public Farmer(String name, String village, String phone,
                  String cropType, String quantity,
                  String preferredContact, String message, String language) {
        this.name = name;
        this.village = village;
        this.phone = phone;
        this.cropType = cropType;
        this.quantity = quantity;
        this.preferredContact = preferredContact;
        this.message = message;
        this.language = language;
        this.status = "NEW";
        this.createdAt = LocalDateTime.now();
    }

    // ---------- Getters ----------
    public String getId() { return id; }
    public String getName() { return name; }
    public String getVillage() { return village; }
    public String getPhone() { return phone; }
    public String getCropType() { return cropType; }
    public String getQuantity() { return quantity; }
    public String getPreferredContact() { return preferredContact; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public String getLanguage() { return language; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ---------- Setters ----------
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setVillage(String village) { this.village = village; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setCropType(String cropType) { this.cropType = cropType; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public void setPreferredContact(String preferredContact) { this.preferredContact = preferredContact; }
    public void setMessage(String message) { this.message = message; }
    public void setStatus(String status) { this.status = status; }
    public void setLanguage(String language) { this.language = language; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}