package com.rajharita.repositories;

import com.rajharita.documents.InvoiceTemplateSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InvoiceTemplateSettingsRepository extends MongoRepository<InvoiceTemplateSettings, String> {
}
