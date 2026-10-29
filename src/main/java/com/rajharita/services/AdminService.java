package com.rajharita.services;

import com.rajharita.documents.Admin;
import com.rajharita.repositories.AdminRepository;
import com.rajharita.documents.Invitation;
import com.rajharita.repositories.InvitationRepository;
import com.rajharita.documents.PendingAdminRegistration;
import com.rajharita.repositories.PendingAdminRegistrationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminService {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private PendingAdminRegistrationRepository pendingAdminRegistrationRepository;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVITATION_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MAX_ADMIN_REGISTRATION_OTP_ATTEMPTS = 5;

    /* =====================================================
       LOGIN — supports existing plaintext records and upgrades
       them to BCrypt after a successful login.
       ===================================================== */
    public Admin login(String username, String rawPassword) {

        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Invalid admin username or password"));

        if (!admin.isActive()) {
            throw new RuntimeException("Admin account is inactive");
        }

        String storedPassword = admin.getPassword();
        boolean matches = storedPassword != null
                && (storedPassword.startsWith("$2")
                ? passwordEncoder.matches(rawPassword, storedPassword)
                : storedPassword.equals(rawPassword));
        if (!matches) {
            throw new RuntimeException("Invalid admin username or password");
        }
        if (!storedPassword.startsWith("$2")) {
            admin.setPassword(passwordEncoder.encode(rawPassword));
            adminRepository.save(admin);
        }

        return admin;
    }

    public String requestAdminRegistration(String name, String username, String email,
                                           String password, String requestedFrom) {
        String safeName = requiredValue(name, "Enter the administrator's full name.", 100);
        String safeUsername = requiredValue(username, "Enter an administrator username.", 40);
        String safeEmail = requiredValue(email, "Enter an administrator email address.", 254)
                .toLowerCase(Locale.ROOT);
        if (!safeUsername.matches("[A-Za-z0-9._-]{3,40}")) {
            throw new IllegalArgumentException("Username must be 3–40 characters using letters, numbers, dots, underscores, or hyphens.");
        }
        if (!safeEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Enter a valid administrator email address.");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("Password must be between 8 and 72 characters.");
        }
        if (adminRepository.existsByUsernameIgnoreCase(safeUsername)) {
            throw new IllegalArgumentException("That administrator username is already in use.");
        }
        if (adminRepository.existsByEmailIgnoreCase(safeEmail)) {
            throw new IllegalArgumentException("That administrator email is already in use.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (requestedFrom != null && pendingAdminRegistrationRepository
                .existsByRequestedFromAndCreatedAtAfter(requestedFrom, now.minusMinutes(1))) {
            throw new IllegalArgumentException("Please wait a minute before requesting another admin approval code.");
        }

        String otp = otpService.generateOtp();
        PendingAdminRegistration pending = new PendingAdminRegistration();
        pending.setId(UUID.randomUUID().toString());
        pending.setName(safeName);
        pending.setUsername(safeUsername);
        pending.setEmail(safeEmail);
        pending.setPasswordHash(passwordEncoder.encode(password));
        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setRequestedFrom(requestedFrom);
        pending.setAttempts(0);
        pending.setCreatedAt(now);
        pending.setExpiresAt(now.plusMinutes(10));
        pendingAdminRegistrationRepository.save(pending);

        if (!otpService.sendAdminRegistrationOtp(safeName, otp)) {
            pendingAdminRegistrationRepository.deleteById(pending.getId());
            throw new IllegalStateException("Could not send the approval code to all main admin email addresses. Please try again later.");
        }
        return pending.getId();
    }

    public Admin verifyAdminRegistration(String registrationId, String enteredOtp) {
        PendingAdminRegistration pending = pendingAdminRegistrationRepository.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Admin registration request was not found or has expired."));
        if (pending.getExpiresAt() == null || LocalDateTime.now().isAfter(pending.getExpiresAt())) {
            pendingAdminRegistrationRepository.delete(pending);
            throw new IllegalArgumentException("Approval code has expired. Start the admin registration again.");
        }
        if (pending.getAttempts() >= MAX_ADMIN_REGISTRATION_OTP_ATTEMPTS) {
            pendingAdminRegistrationRepository.delete(pending);
            throw new IllegalArgumentException("Too many incorrect approval codes. Start the admin registration again.");
        }
        if (enteredOtp == null || !passwordEncoder.matches(enteredOtp, pending.getOtpHash())) {
            pending.setAttempts(pending.getAttempts() + 1);
            if (pending.getAttempts() >= MAX_ADMIN_REGISTRATION_OTP_ATTEMPTS) {
                pendingAdminRegistrationRepository.delete(pending);
                throw new IllegalArgumentException("Too many incorrect approval codes. Start the admin registration again.");
            }
            pendingAdminRegistrationRepository.save(pending);
            throw new IllegalArgumentException("Incorrect approval code. Please check the main admin inboxes and try again.");
        }
        if (adminRepository.existsByUsernameIgnoreCase(pending.getUsername())
                || adminRepository.existsByEmailIgnoreCase(pending.getEmail())) {
            pendingAdminRegistrationRepository.delete(pending);
            throw new IllegalArgumentException("That administrator username or email has already been registered.");
        }

        Admin admin = new Admin();
        admin.setName(pending.getName());
        admin.setUsername(pending.getUsername());
        admin.setEmail(pending.getEmail());
        admin.setPassword(pending.getPasswordHash());
        admin.setRole("ADMIN");
        admin.setActive(true);
        admin.setCreatedAt(LocalDateTime.now());
        Admin saved = adminRepository.save(admin);
        pendingAdminRegistrationRepository.delete(pending);
        return saved;
    }

    public void inviteUser(String email) {
        if (email == null || email.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String code = generateInvitationCode();
        Invitation invitation = new Invitation();
        invitation.setEmail(normalizedEmail);
        invitation.setCodeHash(passwordEncoder.encode(code));
        invitation.setCreatedAt(LocalDateTime.now());
        invitation.setExpiresAt(LocalDateTime.now().plusDays(7));
        invitation.setActive(true);
        Invitation saved = invitationRepository.save(invitation);
        try {
            otpService.sendInvitation(normalizedEmail, code);
        } catch (RuntimeException e) {
            saved.setActive(false);
            invitationRepository.save(saved);
            throw e;
        }
    }

    public Invitation findActiveInvitation(String email) {
        return invitationRepository.findTopByEmailAndActiveTrueOrderByCreatedAtDesc(
                email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation code or email"));
    }

    public boolean matchesInvitation(Invitation invitation, String code) {
        return code != null && passwordEncoder.matches(code, invitation.getCodeHash());
    }

    public void consumeInvitation(Invitation invitation) {
        invitation.setActive(false);
        invitationRepository.save(invitation);
    }

    private String generateInvitationCode() {
        StringBuilder code = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            code.append(INVITATION_ALPHABET.charAt(RANDOM.nextInt(INVITATION_ALPHABET.length())));
        }
        return code.toString();
    }

    private String requiredValue(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("Input must be " + maxLength + " characters or fewer.");
        }
        return trimmed;
    }
}