package com.rajharita.services;

import com.rajharita.documents.CartLine;
import com.rajharita.documents.CustomerCart;
import com.rajharita.documents.Product;
import com.rajharita.documents.User;
import com.rajharita.repositories.CustomerCartRepository;
import com.rajharita.repositories.ProductRepository;
import com.rajharita.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    private final CustomerCartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartService(CustomerCartRepository cartRepository,
                       UserRepository userRepository,
                       ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    public CartView getCart(String username) {
        return view(getOrCreate(username));
    }

    public CartView addItem(String username, String productId, int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be at least 1");
        Product product = availableProduct(productId);
        CustomerCart cart = getOrCreate(username);
        CartLine line = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst().orElse(null);
        int updatedQuantity = quantity + (line == null ? 0 : line.getQuantity());
        ensureAvailableStock(product, updatedQuantity);
        if (line == null) cart.getItems().add(new CartLine(productId, quantity));
        else line.setQuantity(updatedQuantity);
        return view(save(cart));
    }

    public CartView setQuantity(String username, String productId, int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be at least 1");
        Product product = availableProduct(productId);
        ensureAvailableStock(product, quantity);
        CustomerCart cart = getOrCreate(username);
        CartLine line = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Product is not in the cart"));
        line.setQuantity(quantity);
        return view(save(cart));
    }

    public CartView removeItem(String username, String productId) {
        CustomerCart cart = getOrCreate(username);
        cart.getItems().removeIf(item -> item.getProductId().equals(productId));
        return view(save(cart));
    }

    public List<Product> wishlist(String username) {
        CustomerCart cart = getOrCreate(username);
        List<Product> products = new ArrayList<>();
        for (String id : cart.getWishlistProductIds()) {
            productRepository.findById(id)
                    .filter(product -> product.isPublished() && !product.isDeleted())
                    .ifPresent(products::add);
        }
        return products;
    }

    public void addWishlist(String username, String productId) {
        findListedProduct(productId);
        CustomerCart cart = getOrCreate(username);
        if (!cart.getWishlistProductIds().contains(productId)) cart.getWishlistProductIds().add(productId);
        save(cart);
    }

    public void removeWishlist(String username, String productId) {
        CustomerCart cart = getOrCreate(username);
        cart.getWishlistProductIds().remove(productId);
        save(cart);
    }

    public CartView moveWishlistToCart(String username, String productId) {
        CustomerCart cart = getOrCreate(username);
        Product product = availableProduct(productId);
        CartLine line = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst().orElse(null);
        int quantity = line == null ? 1 : line.getQuantity() + 1;
        ensureAvailableStock(product, quantity);
        if (line == null) cart.getItems().add(new CartLine(productId, 1));
        else line.setQuantity(quantity);
        cart.getWishlistProductIds().remove(productId);
        return view(save(cart));
    }

    private CustomerCart getOrCreate(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return cartRepository.findById(user.getId()).orElseGet(() -> {
            CustomerCart cart = new CustomerCart();
            cart.setUserId(user.getId());
            cart.setUpdatedAt(LocalDateTime.now());
            return cartRepository.save(cart);
        });
    }

    private CustomerCart save(CustomerCart cart) {
        cart.setUpdatedAt(LocalDateTime.now());
        return cartRepository.save(cart);
    }

    private Product availableProduct(String productId) {
        Product product = findListedProduct(productId);
        ensureAvailableStock(product, 1);
        return product;
    }

    private Product findListedProduct(String productId) {
        return productRepository.findById(productId)
                .filter(item -> item.isPublished() && !item.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Product is not available"));
    }

    private void ensureAvailableStock(Product product, int quantity) {
        if (quantity > product.getStock()) {
            throw new IllegalArgumentException("Only " + product.getStock() + " item(s) are currently available");
        }
    }

    private CartView view(CustomerCart cart) {
        List<CartItemView> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartLine line : cart.getItems()) {
            Product product = productRepository.findById(line.getProductId())
                    .filter(item -> item.isPublished() && !item.isDeleted())
                    .orElse(null);
            boolean available = product != null && product.getStock() >= line.getQuantity();
            BigDecimal price = product == null ? BigDecimal.ZERO : product.getPrice();
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(line.getQuantity()));
            if (available) subtotal = subtotal.add(lineTotal);
            items.add(new CartItemView(line.getProductId(), product == null ? "Unavailable product" : product.getName(),
                    price, line.getQuantity(), lineTotal, product == null ? 0 : product.getStock(),
                    available, product == null || product.getImageUrls().isEmpty()
                            ? null : product.getImageUrls().get(0)));
        }
        return new CartView(items, subtotal);
    }

    public record CartItemView(String productId, String name, BigDecimal unitPrice, int quantity,
                               BigDecimal lineTotal, int stock, boolean available, String imageUrl) { }
    public record CartView(List<CartItemView> items, BigDecimal subtotal) { }
}
