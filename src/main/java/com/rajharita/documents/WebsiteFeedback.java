package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "website_feedback")
public class WebsiteFeedback {
    @Id
    private String id;
    private String userId;
    private String username;
    private String customerName;
    private String customerEmail;
    private int rating;
    private String title;
    private String message;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public int getRating() { return rating; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public void setRating(int rating) { this.rating = rating; }
    public void setTitle(String title) { this.title = title; }
    public void setMessage(String message) { this.message = message; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
