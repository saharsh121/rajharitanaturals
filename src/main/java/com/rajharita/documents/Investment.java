package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "investments")
public class Investment {

    @Id
    private String id;

    private String name;
    private String companyName;
    private String email;
    private String phone;
    private String location;
    private String investmentType;    // Equity / Loan / Partnership
    private String amountRange;       // dropdown value
    private String message;

    private String attachmentPath;    // server file path (nullable)
    private String attachmentName;    // original file name (nullable)

    private String status;            // NEW / CONTACTED / CLOSED
    private String language;          // en / hi
    private LocalDateTime createdAt;

    public Investment() {
    }

    public Investment(String name, String companyName, String email,
                      String phone, String location,
                      String investmentType, String amountRange,
                      String message, String language) {
        this.name = name;
        this.companyName = companyName;
        this.email = email;
        this.phone = phone;
        this.location = location;
        this.investmentType = investmentType;
        this.amountRange = amountRange;
        this.message = message;
        this.language = language;
        this.status = "NEW";
        this.createdAt = LocalDateTime.now();
    }

    // ---------- Getters ----------
    public String getId() { return id; }
    public String getName() { return name; }
    public String getCompanyName() { return companyName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getLocation() { return location; }
    public String getInvestmentType() { return investmentType; }
    public String getAmountRange() { return amountRange; }
    public String getMessage() { return message; }
    public String getAttachmentPath() { return attachmentPath; }
    public String getAttachmentName() { return attachmentName; }
    public String getStatus() { return status; }
    public String getLanguage() { return language; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // ---------- Setters ----------
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setLocation(String location) { this.location = location; }
    public void setInvestmentType(String investmentType) { this.investmentType = investmentType; }
    public void setAmountRange(String amountRange) { this.amountRange = amountRange; }
    public void setMessage(String message) { this.message = message; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }
    public void setStatus(String status) { this.status = status; }
    public void setLanguage(String language) { this.language = language; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}