package com.rajharita.controller;

import com.rajharita.documents.B2BRequest;
import com.rajharita.services.B2BRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Controller
public class B2BRequestController {

    @Autowired
    private B2BRequestService b2bRequestService;

    /* =====================================================
       SERVE PAGE
       GET /b2b → b2b.html
       ===================================================== */
    @GetMapping("/b2b")
    public String b2bPage() {
        return "b2b";
    }

    /* =====================================================
       SUBMIT (multipart — file + fields)
       POST /api/b2b/submit
       ===================================================== */
    @PostMapping(value = "/api/b2b/submit", consumes = "multipart/form-data")
    @ResponseBody
    public ResponseEntity<?> submit(
            @RequestParam("companyName") String companyName,
            @RequestParam("contactPerson") String contactPerson,
            @RequestParam("email") String email,
            @RequestParam("phone") String phone,
            @RequestParam("materialRequired") String materialRequired,
            @RequestParam("quantity") String quantity,
            @RequestParam("frequency") String frequency,
            @RequestParam("deliveryLocation") String deliveryLocation,
            @RequestParam(value = "message", required = false, defaultValue = "") String message,
            @RequestParam(value = "language", required = false, defaultValue = "en") String language,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment
    ) {
        try {
            // Basic validation
            if (isBlank(companyName))      return err("Please enter your company name");
            if (isBlank(contactPerson))    return err("Please enter the contact person's name");
            if (isBlank(email))            return err("Please enter your email");
            if (isBlank(phone))            return err("Please enter your phone number");
            if (isBlank(materialRequired)) return err("Please enter the material required");
            if (isBlank(quantity))         return err("Please enter the quantity");
            if (isBlank(frequency))        return err("Please select frequency");
            if (isBlank(deliveryLocation)) return err("Please enter the delivery location");

            if (!email.matches("^.+@.+\\..+$")) {
                return err("Please enter a valid email address");
            }

            if (!frequency.equals("One-time") && !frequency.equals("Recurring")) {
                return err("Frequency must be One-time or Recurring");
            }

            // Attachment validation (if any)
            if (attachment != null && !attachment.isEmpty()) {
                String fn = attachment.getOriginalFilename();
                String lower = (fn == null) ? "" : fn.toLowerCase();
                if (!(lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx"))) {
                    return err("Attachment must be PDF, DOC or DOCX");
                }
                if (attachment.getSize() > 10L * 1024 * 1024) {
                    return err("Attachment too large (max 10 MB)");
                }
            }

            B2BRequest request = new B2BRequest(
                    companyName.trim(),
                    contactPerson.trim(),
                    email.trim(),
                    phone.trim(),
                    materialRequired.trim(),
                    quantity.trim(),
                    frequency.trim(),
                    deliveryLocation.trim(),
                    message.trim(),
                    language
            );

            B2BRequest saved = b2bRequestService.submit(request, attachment);

            Map<String, Object> response = new HashMap<>();
            response.put("message",
                    "Thank you! We received your bulk sourcing request and will send a quote soon.");
            response.put("id", saved.getId());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /* =====================================================
       HELPERS
       ===================================================== */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private ResponseEntity<?> err(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}