package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "carts")
public class CustomerCart {
    @Id
    private String userId;
    private List<CartLine> items = new ArrayList<>();
    private List<String> wishlistProductIds = new ArrayList<>();
    private LocalDateTime updatedAt;

    public String getUserId() { return userId; }
    public List<CartLine> getItems() { return items; }
    public List<String> getWishlistProductIds() { return wishlistProductIds; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setItems(List<CartLine> items) { this.items = items; }
    public void setWishlistProductIds(List<String> wishlistProductIds) { this.wishlistProductIds = wishlistProductIds; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
