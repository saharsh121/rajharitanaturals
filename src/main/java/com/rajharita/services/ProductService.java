package com.rajharita.services;

import com.rajharita.documents.Category;
import com.rajharita.documents.Product;
import com.rajharita.repositories.CategoryRepository;
import com.rajharita.repositories.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final MongoTemplate mongoTemplate;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          MongoTemplate mongoTemplate) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Product> search(String keyword, String category, String brand,
                                BigDecimal minPrice, BigDecimal maxPrice,
                                Boolean inStock, String sortBy) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("published").is(true));
        filters.add(Criteria.where("deleted").is(false));
        if (hasText(keyword)) {
            String expression = Pattern.quote(keyword.trim());
            filters.add(new Criteria().orOperator(
                    Criteria.where("name").regex(expression, "i"),
                    Criteria.where("description").regex(expression, "i"),
                    Criteria.where("brand").regex(expression, "i")));
        }
        if (hasText(category)) filters.add(Criteria.where("category").regex("^" + Pattern.quote(category.trim()) + "$", "i"));
        if (hasText(brand)) filters.add(Criteria.where("brand").regex("^" + Pattern.quote(brand.trim()) + "$", "i"));
        if (minPrice != null || maxPrice != null) {
            Criteria price = Criteria.where("price");
            if (minPrice != null) price.gte(minPrice);
            if (maxPrice != null) price.lte(maxPrice);
            filters.add(price);
        }
        if (Boolean.TRUE.equals(inStock)) filters.add(Criteria.where("stock").gt(0));
        if (Boolean.FALSE.equals(inStock)) filters.add(Criteria.where("stock").is(0));

        Query query = new Query(new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        query.with(sort(sortBy));
        return mongoTemplate.find(query, Product.class);
    }

    public List<Product> findHomeFeaturedProducts() {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("published").is(true),
                Criteria.where("deleted").is(false),
                Criteria.where("homeFeatured").is(true)));
        query.with(Sort.by(Sort.Direction.DESC, "popularity")
                .and(Sort.by(Sort.Direction.DESC, "createdAt")));
        return mongoTemplate.find(query, Product.class);
    }

    public Product findPublishedProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (!product.isPublished() || product.isDeleted()) {
            throw new IllegalArgumentException("Product not found");
        }
        product.setPopularity(product.getPopularity() + 1);
        return productRepository.save(product);
    }

    public List<Product> findAdminProducts() {
        return productRepository.findByDeletedFalseOrderByCreatedAtDesc();
    }

    public List<Product> lowStockProducts() {
        return productRepository.findByDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(product -> product.getStock() <= product.getLowStockThreshold())
                .toList();
    }

    public Product save(Product submitted) {
        validate(submitted);
        Product product = submitted.getId() == null ? new Product()
                : productRepository.findById(submitted.getId())
                    .filter(existing -> !existing.isDeleted())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (product.getCreatedAt() == null) product.setCreatedAt(LocalDateTime.now());
        product.setName(submitted.getName().trim());
        product.setDescription(submitted.getDescription());
        product.setCategory(submitted.getCategory().trim());
        categoryRepository.findByNameIgnoreCase(product.getCategory())
                .filter(Category::isActive)
                .orElseThrow(() -> new IllegalArgumentException("Select an active product category"));
        product.setBrand(submitted.getBrand() == null ? "" : submitted.getBrand().trim());
        product.setPrice(submitted.getPrice());
        product.setWeightKg(submitted.getWeightKg());
        product.setSpecifications(submitted.getSpecifications() == null ? Map.of() : submitted.getSpecifications());
        product.setImageUrls(submitted.getImageUrls() == null ? List.of() : submitted.getImageUrls());
        List<String> homeImageUrls = submitted.getHomeImageUrls() == null
                ? List.of() : submitted.getHomeImageUrls().stream().filter(this::hasText).limit(2).toList();
        if (submitted.isHomeFeatured() && homeImageUrls.isEmpty() && product.getImageUrls().isEmpty()) {
            throw new IllegalArgumentException("Add at least one product image before featuring it on the home page.");
        }
        product.setHomeImageUrls(homeImageUrls);
        product.setHomeFeatured(submitted.isHomeFeatured());
        product.setStock(submitted.getStock());
        product.setLowStockThreshold(submitted.getLowStockThreshold());
        product.setPublished(submitted.isPublished());
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    public Product setPublished(String id, boolean published) {
        Product product = findAdminProduct(id);
        product.setPublished(published);
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    public void delete(String id) {
        Product product = findAdminProduct(id);
        product.setDeleted(true);
        product.setPublished(false);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    public List<Category> categories() {
        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    public List<Category> adminCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    public Category saveCategory(Category category) {
        if (!hasText(category.getName())) throw new IllegalArgumentException("Category name is required");
        category.setName(category.getName().trim());
        categoryRepository.findByNameIgnoreCase(category.getName())
                .filter(existing -> !existing.getId().equals(category.getId()))
                .ifPresent(existing -> { throw new IllegalArgumentException("Category already exists"); });
        if (category.getId() == null) category.setCreatedAt(LocalDateTime.now());
        return categoryRepository.save(category);
    }

    public Category setCategoryActive(String id, boolean active) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        category.setActive(active);
        return categoryRepository.save(category);
    }

    private Product findAdminProduct(String id) {
        return productRepository.findById(id)
                .filter(product -> !product.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }

    private Sort sort(String sortBy) {
        if ("price-asc".equals(sortBy)) return Sort.by(Sort.Direction.ASC, "price");
        if ("price-desc".equals(sortBy)) return Sort.by(Sort.Direction.DESC, "price");
        if ("popular".equals(sortBy)) return Sort.by(Sort.Direction.DESC, "popularity");
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private void validate(Product product) {
        if (product == null || !hasText(product.getName()) || !hasText(product.getCategory())
                || product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0
                || product.getWeightKg() == null || product.getWeightKg().compareTo(BigDecimal.ZERO) < 0
                || product.getStock() < 0 || product.getLowStockThreshold() < 0) {
            throw new IllegalArgumentException("Product name, category, non-negative price, weight, and stock are required");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
