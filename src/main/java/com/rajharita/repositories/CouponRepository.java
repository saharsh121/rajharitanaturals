package com.rajharita.repositories;

import com.rajharita.documents.Coupon;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends MongoRepository<Coupon, String> {
    Optional<Coupon> findByCodeIgnoreCase(String code);
    List<Coupon> findAllByOrderByCodeAsc();
}
