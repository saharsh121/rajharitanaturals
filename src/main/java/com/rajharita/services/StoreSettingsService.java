package com.rajharita.services;

import com.rajharita.documents.StoreSettings;
import com.rajharita.repositories.StoreSettingsRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class StoreSettingsService {

    private final StoreSettingsRepository repository;

    public StoreSettingsService(StoreSettingsRepository repository) {
        this.repository = repository;
    }

    public StoreSettings get() {
        return repository.findById("store").orElseGet(() -> repository.save(new StoreSettings()));
    }

    public StoreSettings save(StoreSettings submitted) {
        if (submitted == null || submitted.getDeliveryCharge() == null
                || submitted.getDeliveryChargePerKg() == null
                || submitted.getFreeDeliveryThreshold() == null
                || submitted.getDeliveryCharge().compareTo(BigDecimal.ZERO) < 0
                || submitted.getDeliveryChargePerKg().compareTo(BigDecimal.ZERO) < 0
                || submitted.getFreeDeliveryThreshold().compareTo(BigDecimal.ZERO) < 0
                || submitted.getReturnWindowDays() < 0 || submitted.getReturnWindowDays() > 90) {
            throw new IllegalArgumentException("Delivery charges and threshold must be non-negative and return window 0-90 days");
        }
        submitted.setId("store");
        return repository.save(submitted);
    }
}
