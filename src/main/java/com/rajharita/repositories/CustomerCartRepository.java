package com.rajharita.repositories;

import com.rajharita.documents.CustomerCart;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CustomerCartRepository extends MongoRepository<CustomerCart, String> {
}
