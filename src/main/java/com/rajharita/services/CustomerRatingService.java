package com.rajharita.services;

import com.rajharita.documents.Order;
import com.rajharita.documents.OrderLine;
import com.rajharita.documents.Address;
import com.rajharita.documents.User;
import com.rajharita.repositories.OrderRepository;
import com.rajharita.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CustomerRatingService {
    private static final int RATING_WINDOW_MONTHS = 3;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public CustomerRatingService(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    public List<CustomerRating> customerRatings() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(RATING_WINDOW_MONTHS);
        Map<String, CustomerOrderStats> statsByUser = new HashMap<>();
        for (Order order : orderRepository.findAllByOrderByCreatedAtDesc()) {
            if (!"DELIVERED".equals(order.getStatus())
                    || !"PAID".equals(order.getPaymentStatus())
                    || order.getCreatedAt() == null
                    || order.getCreatedAt().isBefore(cutoff)
                    || order.getUserId() == null) {
                continue;
            }
            statsByUser.computeIfAbsent(order.getUserId(), ignored -> new CustomerOrderStats())
                    .include(order);
        }

        List<CustomerRating> ratings = new ArrayList<>();
        for (User user : userRepository.findAllByOrderByCreatedAtDesc()) {
            if (!"CUSTOMER".equals(user.getRole())) continue;
            CustomerOrderStats stats = statsByUser.getOrDefault(user.getId(), new CustomerOrderStats());
            int rating = ratingFor(stats.orderCount);
            ratings.add(new CustomerRating(
                    user.getId(),
                    user.getUsername(),
                    user.getName(),
                    user.getEmail(),
                    user.getContactNumber(),
                    user.getGender(),
                    user.getCreatedAt(),
                    user.isVerified(),
                    user.isApproved(),
                    user.isActive(),
                    user.getAddresses() == null ? List.of() : user.getAddresses().stream()
                            .filter(Objects::nonNull)
                            .map(address -> new CustomerAddress(
                                    address.getRecipientName(),
                                    address.getPhone(),
                                    address.getLine1(),
                                    address.getLine2(),
                                    address.getCity(),
                                    address.getState(),
                                    address.getPostalCode(),
                                    address.isDefaultAddress()))
                            .toList(),
                    rating,
                    ratingLabel(rating),
                    stats.orderCount,
                    stats.productUnits,
                    stats.spend,
                    stats.lastOrderAt
            ));
        }
        ratings.sort(Comparator.comparingInt(CustomerRating::rating).reversed()
                .thenComparing(Comparator.comparingLong(CustomerRating::deliveredOrders).reversed())
                .thenComparing(rating -> rating.name() == null ? "" : rating.name(),
                        String.CASE_INSENSITIVE_ORDER));
        return ratings;
    }

    public int ratingForUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!"CUSTOMER".equals(user.getRole())) {
            return -1;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(RATING_WINDOW_MONTHS);
        CustomerOrderStats stats = new CustomerOrderStats();
        for (Order order : orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            if ("DELIVERED".equals(order.getStatus())
                    && "PAID".equals(order.getPaymentStatus())
                    && order.getCreatedAt() != null
                    && !order.getCreatedAt().isBefore(cutoff)) {
                stats.include(order);
            }
        }
        return ratingFor(stats.orderCount);
    }

    private int ratingFor(long deliveredOrders) {
        if (deliveredOrders == 0) return 0;
        if (deliveredOrders <= 2) return 1;
        if (deliveredOrders <= 5) return 2;
        if (deliveredOrders <= 11) return 3;
        if (deliveredOrders <= 25) return 4;
        return 5;
    }

    private String ratingLabel(int rating) {
        return switch (rating) {
            case 0 -> "No qualifying orders";
            case 1 -> "Occasional";
            case 2 -> "About monthly";
            case 3 -> "Multiple monthly";
            case 4 -> "About weekly";
            default -> "More than weekly";
        };
    }

    public record CustomerRating(
            String userId,
            String username,
            String name,
            String email,
            String contactNumber,
            String gender,
            LocalDateTime createdAt,
            boolean verified,
            boolean approved,
            boolean active,
            List<CustomerAddress> addresses,
            int rating,
            String ratingLabel,
            long deliveredOrders,
            long productUnits,
            BigDecimal spend,
            LocalDateTime lastOrderAt
    ) { }

    public record CustomerAddress(
            String recipientName,
            String phone,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            boolean defaultAddress
    ) { }

    private static class CustomerOrderStats {
        private long orderCount;
        private long productUnits;
        private BigDecimal spend = BigDecimal.ZERO;
        private LocalDateTime lastOrderAt;

        private void include(Order order) {
            orderCount++;
            if (order.getTotal() != null) spend = spend.add(order.getTotal());
            if (order.getItems() != null) {
                for (OrderLine item : order.getItems()) {
                    if (item != null && item.getQuantity() > 0) productUnits += item.getQuantity();
                }
            }
            if (lastOrderAt == null || order.getCreatedAt().isAfter(lastOrderAt)) {
                lastOrderAt = order.getCreatedAt();
            }
        }
    }
}
