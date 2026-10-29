package com.rajharita.repositories;

import com.rajharita.documents.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);
    void deleteByUserId(String userId);
    List<Order> findAllByOrderByCreatedAtDesc();
    List<Order> findByPaymentStatusAndStatusAndPaymentExpiresAtBefore(
            String paymentStatus, String status, LocalDateTime expiration);
}
