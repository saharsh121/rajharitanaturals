package com.rajharita.repositories;

import com.rajharita.documents.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByContactNumber(String contactNumber);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByContactNumber(String contactNumber);

    List<User> findAllByOrderByCreatedAtDesc();

    List<User> findByApprovedFalseOrderByCreatedAtDesc();
}