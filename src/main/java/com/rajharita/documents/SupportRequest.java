package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "support_requests")
public class SupportRequest {
    @Id
    private String id;
    private String userId;
    private String username;
    private String customerName;
    private String customerEmail;
    private String category;
    private String subject;
    private String message;
    private String status;
    private String adminResponse;
    private String respondedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCategory() { return category; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public String getAdminResponse() { return adminResponse; }
    public String getRespondedBy() { return respondedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public void setCategory(String category) { this.category = category; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setMessage(String message) { this.message = message; }
    public void setStatus(String status) { this.status = status; }
    public void setAdminResponse(String adminResponse) { this.adminResponse = adminResponse; }
    public void setRespondedBy(String respondedBy) { this.respondedBy = respondedBy; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
