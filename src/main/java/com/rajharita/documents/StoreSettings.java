package com.rajharita.documents;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document(collection = "store_settings")
public class StoreSettings {
    @Id
    private String id = "store";
    private BigDecimal deliveryCharge = new BigDecimal("50.00");
    private BigDecimal deliveryChargePerKg = BigDecimal.ZERO;
    private BigDecimal freeDeliveryThreshold = new BigDecimal("1000.00");
    private int returnWindowDays = 7;
    private boolean cashOnDeliveryEnabled = true;
    private String termsAndConditions = "";
    private String privacyPolicy = "";
    private String returnPolicy = "";

    public String getId() { return id; }
    public BigDecimal getDeliveryCharge() { return deliveryCharge; }
    public BigDecimal getDeliveryChargePerKg() {
        return deliveryChargePerKg == null ? BigDecimal.ZERO : deliveryChargePerKg;
    }
    public BigDecimal getFreeDeliveryThreshold() { return freeDeliveryThreshold; }
    public int getReturnWindowDays() { return returnWindowDays; }
    public boolean isCashOnDeliveryEnabled() { return cashOnDeliveryEnabled; }
    public String getTermsAndConditions() { return termsAndConditions; }
    public String getPrivacyPolicy() { return privacyPolicy; }
    public String getReturnPolicy() { return returnPolicy; }

    public void setId(String id) { this.id = id; }
    public void setDeliveryCharge(BigDecimal deliveryCharge) { this.deliveryCharge = deliveryCharge; }
    public void setDeliveryChargePerKg(BigDecimal deliveryChargePerKg) { this.deliveryChargePerKg = deliveryChargePerKg; }
    public void setFreeDeliveryThreshold(BigDecimal freeDeliveryThreshold) { this.freeDeliveryThreshold = freeDeliveryThreshold; }
    public void setReturnWindowDays(int returnWindowDays) { this.returnWindowDays = returnWindowDays; }
    public void setCashOnDeliveryEnabled(boolean cashOnDeliveryEnabled) { this.cashOnDeliveryEnabled = cashOnDeliveryEnabled; }
    public void setTermsAndConditions(String termsAndConditions) { this.termsAndConditions = termsAndConditions; }
    public void setPrivacyPolicy(String privacyPolicy) { this.privacyPolicy = privacyPolicy; }
    public void setReturnPolicy(String returnPolicy) { this.returnPolicy = returnPolicy; }
}
