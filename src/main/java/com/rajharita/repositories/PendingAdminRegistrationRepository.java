package com.rajharita.repositories;

import com.rajharita.documents.PendingAdminRegistration;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;

public interface PendingAdminRegistrationRepository extends MongoRepository<PendingAdminRegistration, String> {
    boolean existsByRequestedFromAndCreatedAtAfter(String requestedFrom, LocalDateTime createdAt);
}
