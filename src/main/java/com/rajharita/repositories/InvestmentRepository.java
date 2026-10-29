package com.rajharita.repositories;

import com.rajharita.documents.Investment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestmentRepository extends MongoRepository<Investment, String> {

    // Find by status (NEW / CONTACTED / CLOSED)
    List<Investment> findByStatus(String status);

    // Find by email
    List<Investment> findByEmail(String email);

    // Find by investment type
    List<Investment> findByInvestmentType(String investmentType);

    // Find by company name (contains, case-insensitive)
    List<Investment> findByCompanyNameContainingIgnoreCase(String companyName);

    // Count by status (for dashboard counters)
    long countByStatus(String status);

    // All submissions newest first
    List<Investment> findAllByOrderByCreatedAtDesc();
}