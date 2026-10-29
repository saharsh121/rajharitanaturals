package com.rajharita.controller;

import com.rajharita.documents.Product;
import com.rajharita.services.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<?> cart(Authentication authentication) {
        return execute(() -> cartService.getCart(authentication.getName()));
    }

    @PostMapping("/items")
    public ResponseEntity<?> addItem(@RequestBody Map<String, Object> body, Authentication authentication) {
        try {
            String productId = String.valueOf(body.get("productId"));
            int quantity = Integer.parseInt(String.valueOf(body.getOrDefault("quantity", 1)));
            return ResponseEntity.ok(cartService.addItem(authentication.getName(), productId, quantity));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<?> setQuantity(@PathVariable String productId,
                                         @RequestBody Map<String, Integer> body,
                                         Authentication authentication) {
        try {
            return ResponseEntity.ok(cartService.setQuantity(authentication.getName(), productId,
                    body.getOrDefault("quantity", 0)));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<?> removeItem(@PathVariable String productId, Authentication authentication) {
        return execute(() -> cartService.removeItem(authentication.getName(), productId));
    }

    @GetMapping("/wishlist")
    public ResponseEntity<List<Product>> wishlist(Authentication authentication) {
        return ResponseEntity.ok(cartService.wishlist(authentication.getName()));
    }

    @PostMapping("/wishlist/{productId}")
    public ResponseEntity<?> addWishlist(@PathVariable String productId, Authentication authentication) {
        try {
            cartService.addWishlist(authentication.getName(), productId);
            return ResponseEntity.ok(Map.of("message", "Product saved to wishlist"));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @DeleteMapping("/wishlist/{productId}")
    public ResponseEntity<?> removeWishlist(@PathVariable String productId, Authentication authentication) {
        try {
            cartService.removeWishlist(authentication.getName(), productId);
            return ResponseEntity.ok(Map.of("message", "Product removed from wishlist"));
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    @PostMapping("/wishlist/{productId}/move-to-cart")
    public ResponseEntity<?> moveToCart(@PathVariable String productId, Authentication authentication) {
        return execute(() -> cartService.moveWishlistToCart(authentication.getName(), productId));
    }

    private ResponseEntity<?> execute(java.util.function.Supplier<?> operation) {
        try {
            return ResponseEntity.ok(operation.get());
        } catch (RuntimeException e) {
            return error(e);
        }
    }

    private ResponseEntity<?> error(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
