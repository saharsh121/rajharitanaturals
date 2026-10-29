package com.rajharita.repositories;

import com.rajharita.documents.Farmer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FarmerRepository extends MongoRepository<Farmer, String> {

    // Find all farmers with a given status (NEW / CONTACTED / CLOSED)
    List<Farmer> findByStatus(String status);

    // Find submissions by phone number
    List<Farmer> findByPhone(String phone);

    // Find submissions by preferred contact method
    List<Farmer> findByPreferredContact(String preferredContact);

    // Find submissions by crop type
    List<Farmer> findByCropType(String cropType);

    // Count how many are in a specific status
    long countByStatus(String status);

    // Get all submissions newest first
    List<Farmer> findAllByOrderByCreatedAtDesc();
}