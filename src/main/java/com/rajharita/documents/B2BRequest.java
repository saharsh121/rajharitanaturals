package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "b2b_requests")
public class B2BRequest {

    @Id
    private String id;

    private String companyName;
    private String contactPerson;
    private String email;
    private String phone;
    private String materialRequired;
    private String quantity;
    private String frequency;         // One-time / Recurring
    private String deliveryLocation;
    private String message;

    private String attachmentPath;    // server file path (nullable)
    private String attachmentName;    // original file name (nullable)

    private String quoteStatus;       // PENDING / SUPPLY_CONFIRMED / QUOTE_SENT / CLOSED
    private String status;            // NEW / CONTACTED / CLOSED
    private String language;          // en / hi
    private LocalDateTime createdAt;

    public B2BRequest() {
    }

    public B2BRequest(String companyName, String contactPerson, String email,
                      String phone, String materialRequired, String quantity,
                      String frequency, String deliveryLocation,
                      String message, String language) {
        this.companyName = companyName;
        this.contactPerson = contactPerson;
        this.email = email;
        this.phone = phone;
        this.materialRequired = materialRequired;
        this.quantity = quantity;
        this.frequency = frequency;
        this.deliveryLocation = deliveryLocation;
        this.message = message;
        this.language = language;
        this.quoteStatus = "PENDING";
        this.status = "NEW";
        this.createdAt = LocalDateTime.now();
    }

    // ---------- Getters ----------
    public String getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getContactPerson() { return contactPerson; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getMaterialRequired() { return materialRequired; }
    public String getQuantity() { return quantity; }
    public String getFrequency() { return frequency; }
    public String getDeliveryLocation() { return deliveryLocation; }
    public String getMessage() { return message; }
    public String getAttachmentPath() { return attachmentPath; }
    public String getAttachmentName() { return attachmentName; }
    public String getQuoteStatus() { return quoteStatus; }
    public String getStatus() { return status; }
    public String getLanguage() { return language; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ---------- Setters ----------
    public void setId(String id) { this.id = id; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setMaterialRequired(String materialRequired) { this.materialRequired = materialRequired; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public void setDeliveryLocation(String deliveryLocation) { this.deliveryLocation = deliveryLocation; }
    public void setMessage(String message) { this.message = message; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }
    public void setQuoteStatus(String quoteStatus) { this.quoteStatus = quoteStatus; }
    public void setStatus(String status) { this.status = status; }
    public void setLanguage(String language) { this.language = language; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}