package com.rajharita.services;

import com.rajharita.documents.Coupon;
import com.rajharita.repositories.CouponRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final MongoTemplate mongoTemplate;
    private final CustomerRatingService customerRatingService;

    public CouponService(CouponRepository couponRepository, MongoTemplate mongoTemplate,
                         CustomerRatingService customerRatingService) {
        this.couponRepository = couponRepository;
        this.mongoTemplate = mongoTemplate;
        this.customerRatingService = customerRatingService;
    }

    public List<Coupon> all() {
        return couponRepository.findAllByOrderByCodeAsc();
    }

    public List<Coupon> availableOffers(String username) {
        LocalDateTime now = LocalDateTime.now();
        Integer customerRating = username == null ? null : customerRatingService.ratingForUsername(username);
        return couponRepository.findAllByOrderByCodeAsc().stream()
                .filter(Coupon::isActive)
                .filter(coupon -> coupon.getStartsAt() == null || !now.isBefore(coupon.getStartsAt()))
                .filter(coupon -> coupon.getExpiresAt() == null || !now.isAfter(coupon.getExpiresAt()))
                .filter(coupon -> coupon.getUsageLimit() == 0 || coupon.getUsageCount() < coupon.getUsageLimit())
                .filter(coupon -> coupon.getTargetRating() == null
                        || customerRating != null && coupon.getTargetRating().equals(customerRating))
                .toList();
    }

    public Coupon save(Coupon submitted) {
        validate(submitted);
        String code = submitted.getCode().trim().toUpperCase(Locale.ROOT);
        couponRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(submitted.getId()))
                .ifPresent(existing -> { throw new IllegalArgumentException("Coupon code already exists"); });
        Coupon coupon = submitted.getId() == null ? new Coupon()
                : couponRepository.findById(submitted.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));
        coupon.setCode(code);
        coupon.setTitle(submitted.getTitle() == null ? null : submitted.getTitle().trim());
        coupon.setDescription(submitted.getDescription() == null ? null : submitted.getDescription().trim());
        coupon.setTargetRating(submitted.getTargetRating());
        coupon.setDiscountType(submitted.getDiscountType().toUpperCase(Locale.ROOT));
        coupon.setDiscountValue(submitted.getDiscountValue());
        coupon.setMinimumOrder(submitted.getMinimumOrder() == null ? BigDecimal.ZERO : submitted.getMinimumOrder());
        coupon.setStartsAt(submitted.getStartsAt());
        coupon.setExpiresAt(submitted.getExpiresAt());
        coupon.setUsageLimit(submitted.getUsageLimit());
        coupon.setActive(submitted.isActive());
        return couponRepository.save(coupon);
    }

    public Coupon setActive(String id, boolean active) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));
        coupon.setActive(active);
        return couponRepository.save(coupon);
    }

    public void delete(String id) {
        if (!couponRepository.existsById(id)) {
            throw new IllegalArgumentException("Coupon not found");
        }
        couponRepository.deleteById(id);
    }

    public BigDecimal discount(String code, BigDecimal subtotal, String username) {
        if (code == null || code.isBlank()) return BigDecimal.ZERO;
        Coupon coupon = validCoupon(code, subtotal, username);
        return calculateDiscount(coupon, subtotal);
    }

    public Coupon reserve(String code, BigDecimal subtotal, String username) {
        if (code == null || code.isBlank()) return null;
        Coupon coupon = validCoupon(code, subtotal, username);
        LocalDateTime now = LocalDateTime.now();
        List<Criteria> filters = new java.util.ArrayList<>(List.of(
                Criteria.where("code").is(coupon.getCode()),
                Criteria.where("active").is(true),
                coupon.getTargetRating() == null
                        ? Criteria.where("targetRating").is(null)
                        : Criteria.where("targetRating").is(coupon.getTargetRating()),
                new Criteria().orOperator(Criteria.where("startsAt").is(null), Criteria.where("startsAt").lte(now)),
                new Criteria().orOperator(Criteria.where("expiresAt").is(null), Criteria.where("expiresAt").gte(now)),
                new Criteria().orOperator(Criteria.where("minimumOrder").lte(subtotal),
                        Criteria.where("minimumOrder").is(null))));
        if (coupon.getUsageLimit() > 0) {
            filters.add(Criteria.where("usageCount").lt(coupon.getUsageLimit()));
        }
        Coupon reserved = mongoTemplate.findAndModify(new Query(new Criteria().andOperator(filters.toArray(Criteria[]::new))),
                new Update().inc("usageCount", 1),
                FindAndModifyOptions.options().returnNew(true), Coupon.class);
        if (reserved == null) throw new IllegalArgumentException("Coupon is no longer valid or has reached its usage limit");
        return reserved;
    }

    public void release(Coupon coupon) {
        if (coupon == null || coupon.getUsageLimit() == 0 && coupon.getUsageCount() == 0) return;
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(coupon.getId())
                        .and("usageCount").gt(0)),
                new Update().inc("usageCount", -1), Coupon.class);
    }

    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal subtotal) {
        BigDecimal amount = "PERCENT".equals(coupon.getDiscountType())
                ? subtotal.multiply(coupon.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : coupon.getDiscountValue().setScale(2, RoundingMode.HALF_UP);
        return amount.min(subtotal);
    }

    private Coupon validCoupon(String code, BigDecimal subtotal, String username) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new IllegalArgumentException("Coupon code is invalid"));
        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isActive()
                || coupon.getStartsAt() != null && now.isBefore(coupon.getStartsAt())
                || coupon.getExpiresAt() != null && now.isAfter(coupon.getExpiresAt())
                || coupon.getUsageLimit() > 0 && coupon.getUsageCount() >= coupon.getUsageLimit()
                || coupon.getMinimumOrder() != null && subtotal.compareTo(coupon.getMinimumOrder()) < 0) {
            throw new IllegalArgumentException("Coupon is inactive, expired, or does not meet its minimum purchase");
        }
        if (coupon.getTargetRating() != null) {
            if (username == null || coupon.getTargetRating() != customerRatingService.ratingForUsername(username)) {
                throw new IllegalArgumentException("This special coupon is reserved for customers with a "
                        + coupon.getTargetRating() + "-star purchase rating");
            }
        }
        return coupon;
    }

    private void validate(Coupon coupon) {
        if (coupon == null || coupon.getCode() == null || coupon.getCode().isBlank()
                || coupon.getTitle() == null || coupon.getTitle().isBlank() || coupon.getTitle().length() > 100
                || coupon.getDescription() == null || coupon.getDescription().isBlank()
                || coupon.getDescription().length() > 400
                || coupon.getTargetRating() != null
                        && (coupon.getTargetRating() < 0 || coupon.getTargetRating() > 5)
                || coupon.getDiscountType() == null || coupon.getDiscountValue() == null
                || coupon.getDiscountValue().signum() <= 0 || coupon.getUsageLimit() < 0
                || coupon.getMinimumOrder() != null && coupon.getMinimumOrder().signum() < 0) {
            throw new IllegalArgumentException("Enter a code, offer heading and description, positive discount, and valid usage/minimum values");
        }
        if (!"PERCENT".equalsIgnoreCase(coupon.getDiscountType())
                && !"FIXED".equalsIgnoreCase(coupon.getDiscountType())) {
            throw new IllegalArgumentException("Discount type must be PERCENT or FIXED");
        }
        if ("PERCENT".equalsIgnoreCase(coupon.getDiscountType())
                && coupon.getDiscountValue().compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100");
        }
        if (coupon.getStartsAt() != null && coupon.getExpiresAt() != null
                && !coupon.getExpiresAt().isAfter(coupon.getStartsAt())) {
            throw new IllegalArgumentException("Coupon expiry must follow its start time");
        }
    }
}
