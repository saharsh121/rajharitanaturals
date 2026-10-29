package com.rajharita.repositories;

import com.rajharita.documents.B2BRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface B2BRequestRepository extends MongoRepository<B2BRequest, String> {

    // Find by general status (NEW / CONTACTED / CLOSED)
    List<B2BRequest> findByStatus(String status);

    // Find by quote status (PENDING / SUPPLY_CONFIRMED / QUOTE_SENT / CLOSED)
    List<B2BRequest> findByQuoteStatus(String quoteStatus);

    // Find by email
    List<B2BRequest> findByEmail(String email);

    // Find by company name (contains, case-insensitive)
    List<B2BRequest> findByCompanyNameContainingIgnoreCase(String companyName);

    // Find by frequency (One-time / Recurring)
    List<B2BRequest> findByFrequency(String frequency);

    // Counts (for dashboard)
    long countByStatus(String status);
    long countByQuoteStatus(String quoteStatus);

    // All submissions newest first
    List<B2BRequest> findAllByOrderByCreatedAtDesc();
}