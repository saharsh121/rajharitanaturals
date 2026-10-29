package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "products")
public class Product {

    @Id
    private String id;
    private String name;
    private String description;
    private String category;
    private String brand;
    private BigDecimal price;
    private BigDecimal weightKg = BigDecimal.ZERO;
    private Map<String, String> specifications = new HashMap<>();
    private List<String> imageUrls = new ArrayList<>();
    private List<String> homeImageUrls = new ArrayList<>();
    private boolean homeFeatured;
    private int stock;
    private int lowStockThreshold = 5;
    private long popularity;
    private boolean published;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getBrand() { return brand; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getWeightKg() { return weightKg == null ? BigDecimal.ZERO : weightKg; }
    public Map<String, String> getSpecifications() { return specifications; }
    public List<String> getImageUrls() { return imageUrls; }
    public List<String> getHomeImageUrls() { return homeImageUrls; }
    public boolean isHomeFeatured() { return homeFeatured; }
    public int getStock() { return stock; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public long getPopularity() { return popularity; }
    public boolean isPublished() { return published; }
    public boolean isDeleted() { return deleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setBrand(String brand) { this.brand = brand; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public void setSpecifications(Map<String, String> specifications) { this.specifications = specifications; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public void setHomeImageUrls(List<String> homeImageUrls) { this.homeImageUrls = homeImageUrls; }
    public void setHomeFeatured(boolean homeFeatured) { this.homeFeatured = homeFeatured; }
    public void setStock(int stock) { this.stock = stock; }
    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public void setPopularity(long popularity) { this.popularity = popularity; }
    public void setPublished(boolean published) { this.published = published; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
