package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "odop_partners")
public class OdopPartner {

    @Id
    private String id;

    private String name;
    private String district;
    private String contact;          // phone or email
    private String odopProduct;      // district's ODOP product
    private String role;             // Land Owner / Operations Partner / Investment Partner / Farmer Network Partner
    private String message;

    private String attachmentPath;   // server file path (nullable)
    private String attachmentName;   // original file name (nullable)

    private String status;           // NEW / CONTACTED / CLOSED
    private String language;         // en / hi
    private LocalDateTime createdAt;

    public OdopPartner() {
    }

    public OdopPartner(String name, String district, String contact,
                       String odopProduct, String role,
                       String message, String language) {
        this.name = name;
        this.district = district;
        this.contact = contact;
        this.odopProduct = odopProduct;
        this.role = role;
        this.message = message;
        this.language = language;
        this.status = "NEW";
        this.createdAt = LocalDateTime.now();
    }

    // ---------- Getters ----------
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDistrict() { return district; }
    public String getContact() { return contact; }
    public String getOdopProduct() { return odopProduct; }
    public String getRole() { return role; }
    public String getMessage() { return message; }
    public String getAttachmentPath() { return attachmentPath; }
    public String getAttachmentName() { return attachmentName; }
    public String getStatus() { return status; }
    public String getLanguage() { return language; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ---------- Setters ----------
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDistrict(String district) { this.district = district; }
    public void setContact(String contact) { this.contact = contact; }
    public void setOdopProduct(String odopProduct) { this.odopProduct = odopProduct; }
    public void setRole(String role) { this.role = role; }
    public void setMessage(String message) { this.message = message; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }
    public void setStatus(String status) { this.status = status; }
    public void setLanguage(String language) { this.language = language; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}