package com.rajharita.controller;

import com.rajharita.documents.Coupon;
import com.rajharita.services.CouponService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/available")
    public List<CouponOffer> availableOffers(Authentication authentication) {
        String username = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                ? authentication.getName() : null;
        return couponService.availableOffers(username).stream()
                .map(coupon -> new CouponOffer(
                        coupon.getCode(),
                        coupon.getTitle(),
                        coupon.getDescription(),
                        coupon.getDiscountType(),
                        coupon.getDiscountValue(),
                        coupon.getMinimumOrder(),
                        coupon.getExpiresAt(),
                        coupon.getTargetRating()))
                .toList();
    }

    public record CouponOffer(
            String code,
            String title,
            String description,
            String discountType,
            BigDecimal discountValue,
            BigDecimal minimumOrder,
            LocalDateTime expiresAt,
            Integer targetRating) {
    }
}
