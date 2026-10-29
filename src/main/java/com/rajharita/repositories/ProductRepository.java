package com.rajharita.repositories;

import com.rajharita.documents.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByDeletedFalseOrderByCreatedAtDesc();
    List<Product> findByCategoryAndPublishedTrueAndDeletedFalse(String category);
    List<Product> findByStockLessThanEqualAndDeletedFalse(int threshold);
}
