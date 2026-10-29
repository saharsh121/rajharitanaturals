package com.rajharita.repositories;

import com.rajharita.documents.HomeShowcaseSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HomeShowcaseSettingsRepository extends MongoRepository<HomeShowcaseSettings, String> {
}
