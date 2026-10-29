package com.rajharita.services;

import com.rajharita.documents.SupportRequest;
import com.rajharita.documents.User;
import com.rajharita.documents.WebsiteFeedback;
import com.rajharita.repositories.AdminRepository;
import com.rajharita.repositories.SupportRequestRepository;
import com.rajharita.repositories.UserRepository;
import com.rajharita.repositories.WebsiteFeedbackRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CustomerEngagementService {
    private static final Set<String> SUPPORT_CATEGORIES =
            Set.of("ORDER", "PAYMENT", "DELIVERY", "ACCOUNT", "PRODUCT", "OTHER");
    private static final Set<String> SUPPORT_STATUSES =
            Set.of("OPEN", "IN_PROGRESS", "RESOLVED");

    private final UserRepository userRepository;
    private final WebsiteFeedbackRepository feedbackRepository;
    private final SupportRequestRepository supportRepository;
    private final AdminRepository adminRepository;

    public CustomerEngagementService(UserRepository userRepository,
                                     WebsiteFeedbackRepository feedbackRepository,
                                     SupportRequestRepository supportRepository,
                                     AdminRepository adminRepository) {
        this.userRepository = userRepository;
        this.feedbackRepository = feedbackRepository;
        this.supportRepository = supportRepository;
        this.adminRepository = adminRepository;
    }

    public WebsiteFeedback submitFeedback(String username, int rating, String title, String message) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Choose a rating from 1 to 5 stars.");
        }
        String safeTitle = requiredText(title, "A review title is required.", 100);
        String safeMessage = requiredText(message, "Please enter your feedback.", 2000);
        User user = findUser(username);
        WebsiteFeedback feedback = new WebsiteFeedback();
        feedback.setUserId(user.getId());
        feedback.setUsername(user.getUsername());
        feedback.setCustomerName(user.getName());
        feedback.setCustomerEmail(user.getEmail());
        feedback.setRating(rating);
        feedback.setTitle(safeTitle);
        feedback.setMessage(safeMessage);
        feedback.setCreatedAt(LocalDateTime.now());
        return feedbackRepository.save(feedback);
    }

    public List<WebsiteFeedback> feedbackForCustomer(String username) {
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(findUser(username).getId());
    }

    public List<WebsiteFeedback> allFeedback() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc();
    }

    public SupportRequest submitSupportRequest(String username, String category,
                                               String subject, String message) {
        String safeCategory = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORT_CATEGORIES.contains(safeCategory)) {
            throw new IllegalArgumentException("Choose a valid support topic.");
        }
        String safeSubject = requiredText(subject, "A support request subject is required.", 120);
        String safeMessage = requiredText(message, "Please describe how we can help.", 3000);
        User user = findUser(username);
        LocalDateTime now = LocalDateTime.now();
        SupportRequest request = new SupportRequest();
        request.setUserId(user.getId());
        request.setUsername(user.getUsername());
        request.setCustomerName(user.getName());
        request.setCustomerEmail(user.getEmail());
        request.setCategory(safeCategory);
        request.setSubject(safeSubject);
        request.setMessage(safeMessage);
        request.setStatus("OPEN");
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        return supportRepository.save(request);
    }

    public List<SupportRequest> supportRequestsForCustomer(String username) {
        return supportRepository.findByUserIdOrderByCreatedAtDesc(findUser(username).getId());
    }

    public List<SupportRequest> allSupportRequests() {
        return supportRepository.findAllByOrderByCreatedAtDesc();
    }

    public SupportRequest respondToSupportRequest(String id, String status,
                                                  String adminResponse, String respondedBy) {
        SupportRequest request = supportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Support request not found."));
        String safeStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORT_STATUSES.contains(safeStatus)) {
            throw new IllegalArgumentException("Choose a valid support request status.");
        }
        if (adminResponse != null && adminResponse.length() > 3000) {
            throw new IllegalArgumentException("The response must be 3000 characters or fewer.");
        }
        if (adminResponse != null && !adminResponse.isBlank()) {
            request.setAdminResponse(adminResponse.trim());
            String sender = adminRepository.findByUsername(respondedBy)
                    .map(admin -> admin.getName() == null || admin.getName().isBlank()
                            ? "@" + admin.getUsername()
                            : admin.getName().trim() + " (@" + admin.getUsername() + ")")
                    .orElse("@" + respondedBy);
            request.setRespondedBy(sender);
        }
        request.setStatus(safeStatus);
        request.setUpdatedAt(LocalDateTime.now());
        return supportRepository.save(request);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
    }

    private String requiredText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("Text must be " + maxLength + " characters or fewer.");
        }
        return trimmed;
    }
}
