package com.rajharita.controller;

import com.rajharita.documents.Category;
import com.rajharita.documents.Product;
import com.rajharita.services.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "newest") String sort) {
        if (minPrice != null && minPrice.signum() < 0
                || maxPrice != null && maxPrice.signum() < 0
                || minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Invalid price range");
        }
        return productService.search(q, category, brand, minPrice, maxPrice, inStock, sort);
    }

    @GetMapping("/featured")
    public List<Product> featured() {
        return productService.findHomeFeaturedProducts();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> product(@PathVariable String id) {
        try {
            return ResponseEntity.ok(productService.findPublishedProduct(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return productService.categories();
    }
}
