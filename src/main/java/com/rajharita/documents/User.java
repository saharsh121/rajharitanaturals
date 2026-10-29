package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String password;    // BCrypt hashed

    private String name;

    @Indexed(unique = true)
    private String contactNumber;

    private String gender;      // Male / Female / Other

    @Indexed(unique = true)
    private String email;

    private LocalDateTime createdAt;

    private String otp;         // hashed OTP (nullable)
    private LocalDateTime otpExpiry;

    private boolean verified;

    private boolean approved = true;

    private boolean active = true;

    private String role = "CUSTOMER";

    private List<Address> addresses = new ArrayList<>();
    private long loyaltyCoins;
    private Set<String> loyaltyOperations = new HashSet<>();

    public User() {
    }

    public User(String username, String password, String name,
                String contactNumber, String gender, String email) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.contactNumber = contactNumber;
        this.gender = gender;
        this.email = email;
        this.createdAt = LocalDateTime.now();
        this.verified = false;
    }

    // ---------- Getters ----------
    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public String getContactNumber() { return contactNumber; }
    public String getGender() { return gender; }
    public String getEmail() { return email; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getOtp() { return otp; }
    public LocalDateTime getOtpExpiry() { return otpExpiry; }
    public boolean isVerified() { return verified; }
    public boolean isApproved() { return approved; }
    public boolean isActive() { return active; }
    public String getRole() { return role; }
    public List<Address> getAddresses() { return addresses; }
    public long getLoyaltyCoins() { return loyaltyCoins; }
    public Set<String> getLoyaltyOperations() { return loyaltyOperations; }

    // ---------- Setters ----------
    public void setId(String id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setName(String name) { this.name = name; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public void setGender(String gender) { this.gender = gender; }
    public void setEmail(String email) { this.email = email; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setOtp(String otp) { this.otp = otp; }
    public void setOtpExpiry(LocalDateTime otpExpiry) { this.otpExpiry = otpExpiry; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public void setActive(boolean active) { this.active = active; }
    public void setRole(String role) { this.role = role; }
    public void setAddresses(List<Address> addresses) { this.addresses = addresses; }
    public void setLoyaltyCoins(long loyaltyCoins) { this.loyaltyCoins = loyaltyCoins; }
    public void setLoyaltyOperations(Set<String> loyaltyOperations) { this.loyaltyOperations = loyaltyOperations; }
}