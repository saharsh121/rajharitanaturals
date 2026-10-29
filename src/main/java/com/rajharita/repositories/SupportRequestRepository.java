package com.rajharita.repositories;

import com.rajharita.documents.SupportRequest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SupportRequestRepository extends MongoRepository<SupportRequest, String> {
    List<SupportRequest> findByUserIdOrderByCreatedAtDesc(String userId);
    List<SupportRequest> findAllByOrderByCreatedAtDesc();
}
