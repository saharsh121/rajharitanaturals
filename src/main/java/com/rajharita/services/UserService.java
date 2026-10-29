package com.rajharita.services;

import com.rajharita.documents.Address;
import com.rajharita.documents.User;
import com.rajharita.repositories.UserRepository;
import com.rajharita.repositories.CustomerCartRepository;
import com.rajharita.repositories.OrderRepository;
import com.rajharita.documents.Invitation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerCartRepository customerCartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OtpService otpService;

    @Autowired
    private AdminService adminService;

    private static final int OTP_VALID_MINUTES = 5;

    @Value("${app.auth.require-admin-approval:false}")
    private boolean requireAdminApproval;

    @Value("${app.auth.require-invitation:false}")
    private boolean requireInvitation;

    @Value("${app.auth.approved-emails:}")
    private String approvedEmails;

    /* =====================================================
       REGISTER — creates a new user with OTP verification pending
       ===================================================== */
    public User register(User user) {
        return register(user, null);
    }

    public User register(User user, String invitationCode) {
        return registerWithEmailStatus(user, invitationCode).user();
    }

    public RegistrationResult registerWithEmailStatus(User user, String invitationCode) {
        if (user == null || isBlank(user.getName()) || isBlank(user.getUsername())
                || isBlank(user.getEmail()) || isBlank(user.getContactNumber())
                || isBlank(user.getPassword()) || user.getPassword().length() < 8) {
            throw new IllegalArgumentException("Complete all required fields and use a password of at least 8 characters");
        }
        String email = user.getEmail().trim().toLowerCase(Locale.ROOT);
        String username = user.getUsername().trim();
        String contactNumber = user.getContactNumber().trim();
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        boolean approvedEmail = getApprovedEmailSet().contains(email);
        Invitation invitation = null;
        if (requireInvitation && !approvedEmail) {
            if (isBlank(invitationCode)) {
                throw new IllegalArgumentException("An administrator invitation code is required");
            }
            invitation = adminService.findActiveInvitation(email);
            if (!adminService.matchesInvitation(invitation, invitationCode)
                    || invitation.getExpiresAt() == null
                    || LocalDateTime.now().isAfter(invitation.getExpiresAt())) {
                throw new IllegalArgumentException("Invalid or expired invitation code");
            }
        }

        // 1. Check uniqueness
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already registered");
        }
        if (userRepository.existsByContactNumber(contactNumber)) {
            throw new RuntimeException("Contact number already registered");
        }

        // 2. Hash the password with BCrypt
        User account = new User();
        account.setName(user.getName().trim());
        account.setUsername(username);
        account.setEmail(email);
        account.setContactNumber(contactNumber);
        account.setGender(user.getGender());
        account.setPassword(passwordEncoder.encode(user.getPassword()));

        // 3. Generate OTP + expiry
        String otp = otpService.generateOtp();
        account.setOtp(passwordEncoder.encode(otp));
        account.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_VALID_MINUTES));
        account.setVerified(false);
        account.setApproved(!requireAdminApproval);
        account.setActive(true);
        account.setRole("CUSTOMER");
        account.setCreatedAt(LocalDateTime.now());

        // 4. Save user to MongoDB
        User savedUser = userRepository.save(account);
        if (invitation != null) {
            adminService.consumeInvitation(invitation);
        }

        // 5. Send OTP to EMAIL (not mobile)
        boolean emailSent = otpService.sendOtp(savedUser.getEmail(), savedUser.getName(), otp);

        return new RegistrationResult(savedUser, emailSent);
    }

    public record RegistrationResult(User user, boolean emailSent) { }

    /* =====================================================
       VERIFY OTP — activates the account (by email)
       ===================================================== */
    public User verifyOtp(String email, String enteredOtp) {

        User user = findByEmail(email);

        if (user.isVerified()) {
            throw new RuntimeException("Account already verified");
        }

        if (user.getOtp() == null || user.getOtpExpiry() == null) {
            throw new RuntimeException("No OTP found. Please register again.");
        }

        if (LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            throw new RuntimeException("OTP expired. Please request a new one.");
        }

        if (!passwordEncoder.matches(enteredOtp, user.getOtp())
                && !user.getOtp().equals(enteredOtp)) {
            throw new RuntimeException("Invalid OTP");
        }

        // ✅ Mark verified + clear OTP
        user.setVerified(true);
        user.setOtp(null);
        user.setOtpExpiry(null);

        return userRepository.save(user);
    }

    public boolean resendVerificationOtp(String email) {
        User user = findByEmail(email);
        if (user.isVerified() || !user.isActive()) {
            throw new IllegalArgumentException("Account is not eligible for verification");
        }
        String otp = otpService.generateOtp();
        user.setOtp(passwordEncoder.encode(otp));
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_VALID_MINUTES));
        userRepository.save(user);
        return otpService.sendOtp(user.getEmail(), user.getName(), otp);
    }

    /* =====================================================
       LOGIN — accepts username, email, or contact number
       ===================================================== */
    public User login(String identifier, String rawPassword) {
        if (isBlank(identifier) || isBlank(rawPassword)) {
            throw new RuntimeException("Invalid username or password");
        }

        String loginIdentifier = identifier.trim();
        User user = userRepository.findByUsername(loginIdentifier)
                .or(() -> userRepository.findByEmail(loginIdentifier.toLowerCase(Locale.ROOT)))
                .or(() -> userRepository.findByContactNumber(loginIdentifier))
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!user.isVerified()) {
            throw new RuntimeException("Account not verified. Please verify OTP first.");
        }

        if (!user.isActive()) {
            throw new RuntimeException("This account has been deactivated. Contact the administrator.");
        }

        if (requireAdminApproval && !user.isApproved()) {
            throw new RuntimeException("Your account is awaiting administrator approval.");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new RuntimeException("Invalid username or password");
        }

        return user;
    }

    /* =====================================================
       Helper — fetch by username
       ===================================================== */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public List<User> findAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<User> findPendingUsers() {
        return userRepository.findByApprovedFalseOrderByCreatedAtDesc();
    }

    public User setApproval(String id, boolean approved) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setApproved(approved);
        user.setActive(approved);
        return userRepository.save(user);
    }

    public User setActive(String id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(active);
        return userRepository.save(user);
    }

    public void deleteUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        customerCartRepository.deleteById(id);
        userRepository.delete(user);
    }

    public User setRole(String id, String role) {
        if (!"CUSTOMER".equals(role) && !"STAFF".equals(role)) {
            throw new IllegalArgumentException("Role must be CUSTOMER or STAFF");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(role);
        return userRepository.save(user);
    }

    public User updateProfile(String username, String name, String contactNumber, String gender) {
        if (isBlank(name) || isBlank(contactNumber) || isBlank(gender)
                || !Set.of("Male", "Female", "Other").contains(gender)) {
            throw new IllegalArgumentException("Enter a name, mobile number, and valid gender");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        contactNumber = contactNumber.trim();
        userRepository.findByContactNumber(contactNumber)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> { throw new IllegalArgumentException("Contact number already registered"); });
        user.setName(name);
        user.setContactNumber(contactNumber);
        user.setGender(gender);
        return userRepository.save(user);
    }

    public void changePassword(String username, String currentPassword, String newPassword) {
        if (isBlank(currentPassword) || isBlank(newPassword) || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void requestPasswordResetForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        if (!user.isVerified() || !user.isActive()) {
            throw new IllegalArgumentException("Account is not eligible for password reset");
        }
        String otp = otpService.generateOtp();
        user.setOtp(passwordEncoder.encode(otp));
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_VALID_MINUTES));
        userRepository.save(user);
        otpService.sendPasswordResetOtp(user.getEmail(), user.getName(), otp);
    }

    public void resetPasswordForUser(String username, String otp, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        resetPassword(user.getEmail(), otp, newPassword);
    }

    public void deleteAccount(String username, String currentPassword) {
        if (isBlank(currentPassword)) {
            throw new IllegalArgumentException("Enter your password to confirm account deletion");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Password is incorrect");
        }
        boolean hasOpenOrder = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .anyMatch(order -> !List.of("CANCELLED", "DELIVERED", "RETURNED").contains(order.getStatus())
                        || "REFUND_PENDING".equals(order.getPaymentStatus()));
        if (hasOpenOrder) {
            throw new IllegalArgumentException("Complete or cancel open orders and resolve pending refunds before deleting your account");
        }
        orderRepository.deleteByUserId(user.getId());
        customerCartRepository.deleteById(user.getId());
        userRepository.delete(user);
    }

    public List<Address> addresses(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        return user.getAddresses() == null ? List.of() : user.getAddresses();
    }

    public List<Address> saveAddress(String username, Address submitted) {
        if (submitted == null || isBlank(submitted.getRecipientName()) || isBlank(submitted.getPhone())
                || isBlank(submitted.getLine1()) || isBlank(submitted.getCity())
                || isBlank(submitted.getState()) || isBlank(submitted.getPostalCode())
                || !submitted.getPhone().matches("^[+0-9()\\-\\s]{7,20}$")
                || !submitted.getPostalCode().matches("^[A-Za-z0-9\\s-]{4,12}$")) {
            throw new IllegalArgumentException("Enter valid recipient, phone, address, city, state, and postal code");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        if (user.getAddresses() == null) user.setAddresses(new java.util.ArrayList<>());
        Address existing = user.getAddresses().stream()
                .filter(address -> address.getId().equals(submitted.getId()))
                .findFirst().orElse(null);
        if (existing == null) {
            existing = submitted;
            if (existing.getId() == null || existing.getId().isBlank()) {
                existing.setId(java.util.UUID.randomUUID().toString());
            }
            user.getAddresses().add(existing);
        } else {
            existing.setRecipientName(submitted.getRecipientName());
            existing.setPhone(submitted.getPhone());
            existing.setLine1(submitted.getLine1());
            existing.setLine2(submitted.getLine2());
            existing.setCity(submitted.getCity());
            existing.setState(submitted.getState());
            existing.setPostalCode(submitted.getPostalCode());
            existing.setDefaultAddress(submitted.isDefaultAddress());
        }
        if (existing.isDefaultAddress() || user.getAddresses().size() == 1) {
            String selectedAddressId = existing.getId();
            user.getAddresses().forEach(address ->
                    address.setDefaultAddress(java.util.Objects.equals(address.getId(), selectedAddressId)));
        }
        return userRepository.save(user).getAddresses();
    }

    public List<Address> deleteAddress(String username, String addressId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        if (user.getAddresses() == null) return List.of();
        user.getAddresses().removeIf(address -> address.getId().equals(addressId));
        if (!user.getAddresses().isEmpty() && user.getAddresses().stream().noneMatch(Address::isDefaultAddress)) {
            user.getAddresses().get(0).setDefaultAddress(true);
        }
        return userRepository.save(user).getAddresses();
    }

    public User findByEmail(String email) {
        if (isBlank(email)) {
            throw new IllegalArgumentException("Email is required");
        }
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new RuntimeException("No account found for this email"));
    }

    public boolean requestPasswordReset(String email) {
        User user = findByEmail(email);
        if (!user.isVerified() || !user.isActive()) {
            throw new RuntimeException("Account is not eligible for password reset");
        }
        String otp = otpService.generateOtp();
        user.setOtp(passwordEncoder.encode(otp));
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_VALID_MINUTES));
        userRepository.save(user);
        return otpService.sendPasswordResetOtp(user.getEmail(), user.getName(), otp);
    }

    public void resetPassword(String email, String otp, String newPassword) {
        if (isBlank(email) || isBlank(otp) || isBlank(newPassword) || newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        User user = findByEmail(email);
        if (user.getOtp() == null || user.getOtpExpiry() == null
                || LocalDateTime.now().isAfter(user.getOtpExpiry())
                || (!passwordEncoder.matches(otp, user.getOtp()) && !user.getOtp().equals(otp))) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);
    }

    private Set<String> getApprovedEmailSet() {
        return Arrays.stream((approvedEmails == null ? "" : approvedEmails).split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}