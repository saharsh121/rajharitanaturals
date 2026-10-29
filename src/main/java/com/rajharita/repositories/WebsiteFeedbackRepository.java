package com.rajharita.repositories;

import com.rajharita.documents.WebsiteFeedback;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface WebsiteFeedbackRepository extends MongoRepository<WebsiteFeedback, String> {
    List<WebsiteFeedback> findByUserIdOrderByCreatedAtDesc(String userId);
    List<WebsiteFeedback> findAllByOrderByCreatedAtDesc();
}
