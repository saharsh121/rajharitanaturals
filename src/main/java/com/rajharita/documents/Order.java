package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "orders")
public class Order {
    @Id
    private String id;
    private String orderNumber;
    private String userId;
    private String username;
    private String customerEmail;
    private String customerPhone;
    private Address deliveryAddress;
    private List<OrderLine> items;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal deliveryCharge;
    private BigDecimal total;
    private long coinsRedeemed;
    private BigDecimal coinDiscount = BigDecimal.ZERO;
    private long coinsEarned;
    private boolean coinsAwarded;
    private String couponCode;
    private String status;
    private String paymentMethod;
    private String paymentStatus;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String refundReference;
    private String trackingNumber;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime paymentExpiresAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerPhone() { return customerPhone; }
    public Address getDeliveryAddress() { return deliveryAddress; }
    public List<OrderLine> getItems() { return items; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getDeliveryCharge() { return deliveryCharge; }
    public BigDecimal getTotal() { return total; }
    public long getCoinsRedeemed() { return coinsRedeemed; }
    public BigDecimal getCoinDiscount() { return coinDiscount == null ? BigDecimal.ZERO : coinDiscount; }
    public long getCoinsEarned() { return coinsEarned; }
    public boolean isCoinsAwarded() { return coinsAwarded; }
    public String getCouponCode() { return couponCode; }
    public String getStatus() { return status; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public String getRefundReference() { return refundReference; }
    public String getTrackingNumber() { return trackingNumber; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public LocalDateTime getPaymentExpiresAt() { return paymentExpiresAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public void setDeliveryAddress(Address deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public void setItems(List<OrderLine> items) { this.items = items; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public void setDeliveryCharge(BigDecimal deliveryCharge) { this.deliveryCharge = deliveryCharge; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public void setCoinsRedeemed(long coinsRedeemed) { this.coinsRedeemed = coinsRedeemed; }
    public void setCoinDiscount(BigDecimal coinDiscount) { this.coinDiscount = coinDiscount; }
    public void setCoinsEarned(long coinsEarned) { this.coinsEarned = coinsEarned; }
    public void setCoinsAwarded(boolean coinsAwarded) { this.coinsAwarded = coinsAwarded; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public void setStatus(String status) { this.status = status; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
    public void setRefundReference(String refundReference) { this.refundReference = refundReference; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public void setPaymentExpiresAt(LocalDateTime paymentExpiresAt) { this.paymentExpiresAt = paymentExpiresAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
