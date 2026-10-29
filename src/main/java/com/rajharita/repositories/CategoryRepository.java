package com.rajharita.repositories;

import com.rajharita.documents.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends MongoRepository<Category, String> {
    List<Category> findByActiveTrueOrderByNameAsc();
    List<Category> findAllByOrderByNameAsc();
    Optional<Category> findByNameIgnoreCase(String name);
}
