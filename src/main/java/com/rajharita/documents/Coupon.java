package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "coupons")
public class Coupon {
    @Id
    private String id;
    @Indexed(unique = true)
    private String code;
    private String title;
    private String description;
    private Integer targetRating;
    private String discountType;
    private BigDecimal discountValue;
    private BigDecimal minimumOrder;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private int usageLimit;
    private int usageCount;
    private boolean active;

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Integer getTargetRating() { return targetRating; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMinimumOrder() { return minimumOrder; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public int getUsageLimit() { return usageLimit; }
    public int getUsageCount() { return usageCount; }
    public boolean isActive() { return active; }

    public void setId(String id) { this.id = id; }
    public void setCode(String code) { this.code = code; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setTargetRating(Integer targetRating) { this.targetRating = targetRating; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public void setMinimumOrder(BigDecimal minimumOrder) { this.minimumOrder = minimumOrder; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setUsageLimit(int usageLimit) { this.usageLimit = usageLimit; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }
    public void setActive(boolean active) { this.active = active; }
}
