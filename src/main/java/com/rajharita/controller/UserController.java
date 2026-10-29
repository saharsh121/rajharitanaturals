package com.rajharita.controller;

import com.rajharita.documents.User;
import com.rajharita.documents.Address;
import com.rajharita.services.CustomerEngagementService;
import com.rajharita.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private CustomerEngagementService customerEngagementService;

    @Autowired
    private SecurityContextRepository securityContextRepository;

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok(Map.of("token", token.getToken()));
    }

    @GetMapping("/session")
    public Map<String, Boolean> session(Authentication authentication) {
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return Map.of("authenticated", authenticated);
    }

    /* =====================================================
       REGISTER
       POST /api/auth/register
       Body: { username, password, name, contactNumber, gender, email }
       ===================================================== */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        try {
            User user = new User();
            user.setName(body.get("name"));
            user.setUsername(body.get("username"));
            user.setEmail(body.get("email"));
            user.setContactNumber(body.get("contactNumber"));
            user.setGender(body.get("gender"));
            user.setPassword(body.get("password"));
            UserService.RegistrationResult registration =
                    userService.registerWithEmailStatus(user, body.get("invitationCode"));
            User saved = registration.user();

            Map<String, Object> response = new HashMap<>();
            response.put("message", registration.emailSent()
                    ? "OTP sent to your email"
                    : "Your account was created, but the verification email could not be sent right now. Please try again later.");
            response.put("emailSent", registration.emailSent());
            response.put("email", saved.getEmail());
            response.put("username", saved.getUsername());

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /* =====================================================
       VERIFY OTP
       POST /api/auth/verify-otp
       Body: { email, otp }
       ===================================================== */
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            String otp = body.get("otp");

            if (email == null || otp == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing email or otp"));
            }

            User verified = userService.verifyOtp(email, otp);

            return ResponseEntity.ok(Map.of(
                "message", "Account verified successfully",
                "username", verified.getUsername()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody Map<String, String> body) {
        try {
            boolean emailSent = userService.resendVerificationOtp(body.get("email"));
            return ResponseEntity.ok(Map.of(
                    "emailSent", emailSent,
                    "message", emailSent
                            ? "A new verification code has been sent."
                            : "Your account is still saved. Email delivery is temporarily unavailable; please try again later."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /* =====================================================
       LOGIN
       POST /api/auth/login
       Body: { username, password }
       ===================================================== */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        try {
            String username = body.get("username");
            String password = body.get("password");

            if (username == null || password == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing username or password"));
            }

            User user = userService.login(username, password);
            request.getSession(true);
            request.changeSessionId();
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            user.getUsername(), null,
                            java.util.List.of(new SimpleGrantedAuthority(
                                    "ROLE_" + ("STAFF".equals(user.getRole()) ? "STAFF" : "CUSTOMER"))));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            return ResponseEntity.ok(userSummary(user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName())
                .map(user -> ResponseEntity.ok(userSummary(user)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> body, Authentication authentication) {
        try {
            User user = userService.updateProfile(authentication.getName(), body.get("name"),
                    body.get("contactNumber"), body.get("gender"));
            return ResponseEntity.ok(userSummary(user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/feedback")
    public ResponseEntity<?> customerFeedback(Authentication authentication) {
        return ResponseEntity.ok(customerEngagementService.feedbackForCustomer(authentication.getName()));
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> submitFeedback(@RequestBody Map<String, Object> body,
                                            Authentication authentication) {
        try {
            Object ratingValue = body.get("rating");
            if (!(ratingValue instanceof Number rating)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Choose a rating from 1 to 5 stars."));
            }
            return ResponseEntity.ok(customerEngagementService.submitFeedback(authentication.getName(),
                    rating.intValue(), body.get("title") instanceof String title ? title : null,
                    body.get("message") instanceof String message ? message : null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/support-requests")
    public ResponseEntity<?> customerSupportRequests(Authentication authentication) {
        return ResponseEntity.ok(customerEngagementService.supportRequestsForCustomer(authentication.getName()));
    }

    @PostMapping("/support-requests")
    public ResponseEntity<?> submitSupportRequest(@RequestBody Map<String, String> body,
                                                  Authentication authentication) {
        try {
            return ResponseEntity.ok(customerEngagementService.submitSupportRequest(authentication.getName(),
                    body.get("category"), body.get("subject"), body.get("message")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body, Authentication authentication) {
        try {
            userService.changePassword(authentication.getName(), body.get("currentPassword"),
                    body.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/account/password-reset/request")
    public ResponseEntity<?> requestAccountPasswordReset(Authentication authentication) {
        try {
            userService.requestPasswordResetForUser(authentication.getName());
            return ResponseEntity.ok(Map.of("message", "A verification code has been sent to your account email."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/account/password-reset/complete")
    public ResponseEntity<?> completeAccountPasswordReset(@RequestBody Map<String, String> body,
                                                           Authentication authentication) {
        try {
            userService.resetPasswordForUser(authentication.getName(), body.get("otp"), body.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/account")
    public ResponseEntity<?> deleteAccount(@RequestBody Map<String, String> body,
                                           Authentication authentication,
                                           HttpServletRequest request) {
        try {
            userService.deleteAccount(authentication.getName(), body.get("currentPassword"));
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            return ResponseEntity.ok(Map.of("message", "Your account and associated data have been permanently deleted."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/addresses")
    public ResponseEntity<List<Address>> addresses(Authentication authentication) {
        return ResponseEntity.ok(userService.addresses(authentication.getName()));
    }

    @PostMapping("/addresses")
    public ResponseEntity<?> addAddress(@RequestBody Address address, Authentication authentication) {
        return saveAddress(() -> userService.saveAddress(authentication.getName(), address));
    }

    @PutMapping("/addresses/{id}")
    public ResponseEntity<?> updateAddress(@PathVariable String id, @RequestBody Address address,
                                           Authentication authentication) {
        address.setId(id);
        return saveAddress(() -> userService.saveAddress(authentication.getName(), address));
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<?> deleteAddress(@PathVariable String id, Authentication authentication) {
        return saveAddress(() -> userService.deleteAddress(authentication.getName(), id));
    }

    @PostMapping("/request-password-reset")
    public ResponseEntity<?> requestPasswordReset(@RequestBody Map<String, String> body) {
        try {
            boolean emailSent = userService.requestPasswordReset(body.get("email"));
            return ResponseEntity.ok(Map.of(
                    "message", emailSent
                            ? "If the account is eligible, an OTP has been sent."
                            : "If the account is eligible, the reset code is ready, but email delivery is temporarily unavailable. Please try again later.",
                    "emailSent", emailSent));
        } catch (RuntimeException e) {
            if ("No account found for this email".equals(e.getMessage())
                    || "Account is not eligible for password reset".equals(e.getMessage())) {
                return ResponseEntity.ok(Map.of("message", "If the account is eligible, an OTP has been sent."));
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        try {
            userService.resetPassword(body.get("email"), body.get("otp"), body.get("newPassword"));
            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    private Map<String, Object> userSummary(User user) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("message", "Login successful");
        summary.put("id", user.getId());
        summary.put("username", user.getUsername());
        summary.put("name", user.getName());
        summary.put("email", user.getEmail());
        summary.put("contactNumber", user.getContactNumber());
        summary.put("gender", user.getGender());
        summary.put("approved", user.isApproved());
        summary.put("active", user.isActive());
        summary.put("role", user.getRole());
        summary.put("loyaltyCoins", Math.max(0, user.getLoyaltyCoins()));
        return summary;
    }

    private ResponseEntity<?> saveAddress(java.util.function.Supplier<List<Address>> operation) {
        try {
            return ResponseEntity.ok(operation.get());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}