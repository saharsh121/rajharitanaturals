package com.rajharita.repositories;

import com.rajharita.documents.OdopPartner;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OdopPartnerRepository extends MongoRepository<OdopPartner, String> {

    // Find all submissions by status (NEW / CONTACTED / CLOSED)
    List<OdopPartner> findByStatus(String status);

    // Find all submissions from a specific district
    List<OdopPartner> findByDistrict(String district);

    // Find by district (case-insensitive partial match)
    List<OdopPartner> findByDistrictContainingIgnoreCase(String district);

    // Find by proposed role
    List<OdopPartner> findByRole(String role);

    // Find by ODOP product (case-insensitive partial match)
    List<OdopPartner> findByOdopProductContainingIgnoreCase(String odopProduct);

    // Count by status (dashboard counters)
    long countByStatus(String status);

    // All submissions newest first
    List<OdopPartner> findAllByOrderByCreatedAtDesc();
}