package com.rajharita.controller;

import com.rajharita.documents.Order;
import com.rajharita.services.OrderService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<Order> myOrders(Authentication authentication) {
        return orderService.userOrders(authentication.getName());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> myOrder(@PathVariable String id, Authentication authentication) {
        return execute(() -> orderService.userOrder(authentication.getName(), id));
    }

    @PostMapping("/quote")
    public ResponseEntity<?> quote(@RequestBody Map<String, Object> body, Authentication authentication) {
        String couponCode = body.get("couponCode") instanceof String value ? value : null;
        return execute(() -> orderService.quote(authentication.getName(), couponCode, coinsToUse(body)));
    }

    @PostMapping
    public ResponseEntity<?> checkout(@RequestBody Map<String, Object> body, Authentication authentication) {
        String addressId = body.get("addressId") instanceof String value ? value : null;
        String paymentMethod = body.get("paymentMethod") instanceof String value ? value : null;
        String couponCode = body.get("couponCode") instanceof String value ? value : null;
        return execute(() -> orderService.checkout(authentication.getName(), addressId,
                paymentMethod, couponCode, coinsToUse(body)));
    }

    private long coinsToUse(Map<String, Object> body) {
        Object value = body.get("coinsToUse");
        if (value == null) return 0;
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Enter a whole number of coins to use");
        }
        try {
            long coins = new BigDecimal(number.toString()).longValueExact();
            if (coins < 0) throw new IllegalArgumentException("Coins to use cannot be negative");
            return coins;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Enter a whole number of coins to use");
        }
    }

    @PostMapping("/{id}/payment/verify")
    public ResponseEntity<?> verifyPayment(@PathVariable String id, @RequestBody Map<String, String> body,
                                           Authentication authentication) {
        return execute(() -> orderService.verifyPayment(authentication.getName(), id,
                body.get("razorpay_order_id"), body.get("razorpay_payment_id"),
                body.get("razorpay_signature")));
    }

    @PostMapping("/{id}/payment/retry")
    public ResponseEntity<?> retryPayment(@PathVariable String id, Authentication authentication) {
        return execute(() -> orderService.retryPayment(authentication.getName(), id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String id, Authentication authentication) {
        return execute(() -> orderService.cancelByCustomer(authentication.getName(), id));
    }

    @PostMapping("/{id}/return")
    public ResponseEntity<?> requestReturn(@PathVariable String id, Authentication authentication) {
        return execute(() -> orderService.requestReturn(authentication.getName(), id));
    }

    @PostMapping("/{id}/reorder")
    public ResponseEntity<?> reorder(@PathVariable String id, Authentication authentication) {
        return execute(() -> orderService.reorder(authentication.getName(), id));
    }

    @GetMapping(value = "/{id}/invoice", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> invoice(@PathVariable String id, Authentication authentication) {
        try {
            Order order = orderService.userOrder(authentication.getName(), id);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"invoice-" + order.getOrderNumber() + ".html\"")
                    .contentType(MediaType.TEXT_HTML)
                    .body(orderService.invoiceHtml(authentication.getName(), id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> execute(java.util.function.Supplier<?> operation) {
        try {
            return ResponseEntity.ok(operation.get());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
