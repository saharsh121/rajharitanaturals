package com.rajharita.repositories;

import com.rajharita.documents.StoreSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StoreSettingsRepository extends MongoRepository<StoreSettings, String> {
}
