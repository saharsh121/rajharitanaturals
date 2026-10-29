package com.rajharita.repositories;

import com.rajharita.documents.Invitation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface InvitationRepository extends MongoRepository<Invitation, String> {
    Optional<Invitation> findTopByEmailAndActiveTrueOrderByCreatedAtDesc(String email);
}
