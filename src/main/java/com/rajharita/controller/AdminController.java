package com.rajharita.controller;

import com.rajharita.documents.Admin;
import com.rajharita.documents.B2BRequest;
import com.rajharita.documents.Category;
import com.rajharita.documents.Coupon;
import com.rajharita.documents.Farmer;
import com.rajharita.documents.Investment;
import com.rajharita.documents.OdopPartner;
import com.rajharita.documents.Order;
import com.rajharita.documents.Product;
import com.rajharita.documents.StoreSettings;
import com.rajharita.documents.SupportRequest;
import com.rajharita.documents.User;
import com.rajharita.documents.WebsiteFeedback;
import com.rajharita.services.B2BRequestService;
import com.rajharita.services.AdminService;
import com.rajharita.services.CouponService;
import com.rajharita.services.CustomerEngagementService;
import com.rajharita.services.CustomerRatingService;
import com.rajharita.services.FarmerService;
import com.rajharita.services.InvestmentService;
import com.rajharita.services.OdopPartnerService;
import com.rajharita.services.OrderService;
import com.rajharita.services.ProductService;
import com.rajharita.services.StoreSettingsService;
import com.rajharita.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private UserService userService;

    @Autowired
    private CustomerEngagementService customerEngagementService;

    @Autowired
    private CustomerRatingService customerRatingService;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private StoreSettingsService storeSettingsService;

    @Autowired
    private InvestmentService investmentService;

    @Autowired
    private B2BRequestService b2bRequestService;

    @Autowired
    private FarmerService farmerService;

    @Autowired
    private OdopPartnerService odopPartnerService;

    @Autowired
    private SecurityContextRepository securityContextRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDirectory;

    @PostMapping("/registration/request")
    public ResponseEntity<?> requestAdminRegistration(@RequestBody Map<String, String> body,
                                                       HttpServletRequest request) {
        try {
            String registrationId = adminService.requestAdminRegistration(
                    body.get("name"), body.get("username"), body.get("email"),
                    body.get("password"), request.getRemoteAddr());
            return ResponseEntity.ok(Map.of(
                    "registrationId", registrationId,
                    "message", "An approval code was sent to the main administrator email addresses. It expires in 10 minutes."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/registration/verify")
    public ResponseEntity<?> verifyAdminRegistration(@RequestBody Map<String, String> body) {
        try {
            Admin admin = adminService.verifyAdminRegistration(body.get("registrationId"), body.get("otp"));
            return ResponseEntity.ok(Map.of(
                    "message", "Admin account created. You can now sign in.",
                    "username", admin.getUsername()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /* =====================================================
       ADMIN LOGIN API
       POST /api/admin/login
       Body: { username, password }
       ===================================================== */
    @PostMapping("/login")
    public ResponseEntity<?> adminLogin(@RequestBody Map<String, String> body,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        try {
            String username = body.get("username");
            String password = body.get("password");

            if (username == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing username or password"));
            }

            Admin admin = adminService.login(username, password);
            request.getSession(true);
            request.changeSessionId();
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            admin.getUsername(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            Map<String, Object> payload = new HashMap<>();
            payload.put("message", "Admin login successful");
            payload.put("id", admin.getId());
            payload.put("username", admin.getUsername());
            payload.put("name", admin.getName());
            payload.put("email", admin.getEmail());
            payload.put("role", admin.getRole());

            return ResponseEntity.ok(payload);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return userService.findAllUsers().stream().map(this::userSummary).toList();
    }

    @GetMapping("/users/pending")
    public List<Map<String, Object>> pendingUsers() {
        return userService.findPendingUsers().stream().map(this::userSummary).toList();
    }

    @GetMapping("/customers/ratings")
    public List<CustomerRatingService.CustomerRating> customerRatings() {
        return customerRatingService.customerRatings();
    }

    @GetMapping("/users/{id}/orders")
    public ResponseEntity<?> userOrders(@PathVariable String id) {
        try {
            return ResponseEntity.ok(orderService.ordersForUser(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "User deleted; order history retained."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/inquiries/investments")
    public List<Investment> investmentInquiries() {
        return investmentService.getAll();
    }

    @GetMapping("/inquiries/b2b")
    public List<B2BRequest> b2bInquiries() {
        return b2bRequestService.getAll();
    }

    @GetMapping("/inquiries/farmers")
    public List<Farmer> farmerInquiries() {
        return farmerService.getAll();
    }

    @GetMapping("/inquiries/odop")
    public List<OdopPartner> odopInquiries() {
        return odopPartnerService.getAll();
    }

    @GetMapping("/feedback")
    public List<WebsiteFeedback> websiteFeedback() {
        return customerEngagementService.allFeedback();
    }

    @GetMapping("/support-requests")
    public List<SupportRequest> supportRequests() {
        return customerEngagementService.allSupportRequests();
    }

    @PatchMapping("/support-requests/{id}")
    public ResponseEntity<?> respondToSupportRequest(@PathVariable String id,
                                                     @RequestBody Map<String, String> body,
                                                     Authentication authentication) {
        try {
            return ResponseEntity.ok(customerEngagementService.respondToSupportRequest(id,
                    body.get("status"), body.get("adminResponse"), authentication.getName()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/invitations")
    public ResponseEntity<?> inviteUser(@RequestBody Map<String, String> body) {
        try {
            adminService.inviteUser(body.get("email"));
            return ResponseEntity.ok(Map.of("message", "Invitation sent"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/users/{id}/approve")
    public ResponseEntity<?> approveUser(@PathVariable String id) {
        return updateUserStatus(() -> userService.setApproval(id, true));
    }

    @PostMapping("/users/{id}/reject")
    public ResponseEntity<?> rejectUser(@PathVariable String id) {
        return updateUserStatus(() -> userService.setApproval(id, false));
    }

    @PostMapping("/users/{id}/block")
    public ResponseEntity<?> blockUser(@PathVariable String id) {
        return updateUserStatus(() -> userService.setActive(id, false));
    }

    @PostMapping("/users/{id}/activate")
    public ResponseEntity<?> activateUser(@PathVariable String id) {
        return updateUserStatus(() -> userService.setActive(id, true));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(userSummary(userService.setRole(id, body.get("role"))));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/products")
    public List<Product> products() {
        return productService.findAdminProducts();
    }

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(@RequestBody Product product) {
        return saveProduct(product);
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable String id, @RequestBody Product product) {
        product.setId(id);
        return saveProduct(product);
    }

    @PatchMapping("/products/{id}/published")
    public ResponseEntity<?> publishProduct(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        try {
            return ResponseEntity.ok(productService.setPublished(id, Boolean.TRUE.equals(body.get("published"))));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable String id) {
        try {
            productService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Product archived"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/products/images", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadProductImage(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "Choose an image up to 5 MB"));
        }
        String extension = switch (file.getContentType() == null ? "" : file.getContentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> null;
        };
        if (extension == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only JPEG, PNG, and WebP images are supported"));
        }
        String filename = UUID.randomUUID() + extension;
        Path directory = Path.of(uploadDirectory).toAbsolutePath().normalize().resolve("products");
        try {
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(filename));
            return ResponseEntity.ok(Map.of("url", "/products/" + filename));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Image upload failed"));
        }
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return productService.adminCategories();
    }

    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@RequestBody Category category) {
        return saveCategory(category);
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable String id, @RequestBody Category category) {
        category.setId(id);
        return saveCategory(category);
    }

    @PatchMapping("/categories/{id}/active")
    public ResponseEntity<?> setCategoryActive(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        try {
            return ResponseEntity.ok(productService.setCategoryActive(id, Boolean.TRUE.equals(body.get("active"))));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/inventory/low-stock")
    public List<Product> lowStockProducts() {
        return productService.lowStockProducts();
    }

    @GetMapping("/orders")
    public List<Order> orders() {
        return orderService.allOrders();
    }

    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(orderService.setStatus(id, body.get("status"), body.get("trackingNumber")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable String id) {
        try {
            return ResponseEntity.ok(orderService.cancelAsAdmin(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/orders/{id}/refund")
    public ResponseEntity<?> refundOrder(@PathVariable String id) {
        try {
            return ResponseEntity.ok(orderService.refundOnlinePayment(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/orders/{id}/confirm-manual-refund")
    public ResponseEntity<?> confirmManualRefund(@PathVariable String id) {
        try {
            return ResponseEntity.ok(orderService.confirmManualRefund(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/coupons")
    public List<Coupon> coupons() {
        return couponService.all();
    }

    @PostMapping("/coupons")
    public ResponseEntity<?> createCoupon(@RequestBody Coupon coupon) {
        return saveCoupon(coupon);
    }

    @PutMapping("/coupons/{id}")
    public ResponseEntity<?> updateCoupon(@PathVariable String id, @RequestBody Coupon coupon) {
        coupon.setId(id);
        return saveCoupon(coupon);
    }

    @PatchMapping("/coupons/{id}/active")
    public ResponseEntity<?> activateCoupon(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        try {
            return ResponseEntity.ok(couponService.setActive(id, Boolean.TRUE.equals(body.get("active"))));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/coupons/{id}")
    public ResponseEntity<?> deleteCoupon(@PathVariable String id) {
        try {
            couponService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/settings")
    public StoreSettings storeSettings() {
        return storeSettingsService.get();
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody StoreSettings settings) {
        try {
            return ResponseEntity.ok(storeSettingsService.save(settings));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/reports/sales")
    public ResponseEntity<?> salesReport(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        try {
            return ResponseEntity.ok(orderService.salesReport(startDate, endDate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> saveProduct(Product product) {
        try {
            return ResponseEntity.ok(productService.save(product));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> saveCategory(Category category) {
        try {
            return ResponseEntity.ok(productService.saveCategory(category));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> saveCoupon(Coupon coupon) {
        try {
            return ResponseEntity.ok(couponService.save(coupon));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> updateUserStatus(java.util.function.Supplier<User> operation) {
        try {
            return ResponseEntity.ok(userSummary(operation.get()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> userSummary(User user) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", user.getId());
        summary.put("username", user.getUsername());
        summary.put("name", user.getName());
        summary.put("email", user.getEmail());
        summary.put("contactNumber", user.getContactNumber());
        summary.put("gender", user.getGender());
        summary.put("verified", user.isVerified());
        summary.put("approved", user.isApproved());
        summary.put("active", user.isActive());
        summary.put("role", user.getRole());
        summary.put("createdAt", user.getCreatedAt());
        return summary;
    }
}