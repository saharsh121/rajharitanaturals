package com.rajharita.controller;

import com.rajharita.documents.StoreSettings;
import com.rajharita.services.StoreSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final StoreSettingsService settingsService;
    private final boolean onlinePaymentsEnabled;

    public StoreController(StoreSettingsService settingsService,
                           @Value("${app.payments.online-enabled:false}") boolean onlinePaymentsEnabled) {
        this.settingsService = settingsService;
        this.onlinePaymentsEnabled = onlinePaymentsEnabled;
    }

    @GetMapping("/settings")
    public CheckoutSettings settings() {
        StoreSettings settings = settingsService.get();
        return new CheckoutSettings(settings.getDeliveryCharge(), settings.getDeliveryChargePerKg(),
                settings.getFreeDeliveryThreshold(),
                settings.getReturnWindowDays(), settings.isCashOnDeliveryEnabled(),
                settings.getTermsAndConditions(), settings.getPrivacyPolicy(), settings.getReturnPolicy(),
                onlinePaymentsEnabled);
    }

    public record CheckoutSettings(BigDecimal deliveryCharge, BigDecimal deliveryChargePerKg,
                                   BigDecimal freeDeliveryThreshold,
                                   int returnWindowDays, boolean cashOnDeliveryEnabled,
                                   String termsAndConditions, String privacyPolicy, String returnPolicy,
                                   boolean onlinePaymentsEnabled) { }
}
