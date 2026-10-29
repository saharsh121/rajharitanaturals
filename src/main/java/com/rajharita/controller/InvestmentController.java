package com.rajharita.controller;

import com.rajharita.documents.Investment;
import com.rajharita.services.InvestmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Controller
public class InvestmentController {

    @Autowired
    private InvestmentService investmentService;

    /* =====================================================
       SERVE PAGE
       GET /investment → investment.html
       ===================================================== */
    @GetMapping("/investment")
    public String investmentPage() {
        return "investment";
    }

    /* =====================================================
       SUBMIT FORM (multipart — handles file + fields)
       POST /api/investment/submit
       ===================================================== */
    @PostMapping(value = "/api/investment/submit", consumes = "multipart/form-data")
    @ResponseBody
    public ResponseEntity<?> submit(
            @RequestParam("name") String name,
            @RequestParam("companyName") String companyName,
            @RequestParam("email") String email,
            @RequestParam("phone") String phone,
            @RequestParam("location") String location,
            @RequestParam("investmentType") String investmentType,
            @RequestParam("amountRange") String amountRange,
            @RequestParam(value = "message", required = false, defaultValue = "") String message,
            @RequestParam(value = "language", required = false, defaultValue = "en") String language,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment
    ) {
        try {
            // Basic validation
            if (isBlank(name))           return err("Please enter your name");
            if (isBlank(companyName))    return err("Please enter your company name");
            if (isBlank(email))          return err("Please enter your email");
            if (isBlank(phone))          return err("Please enter your phone number");
            if (isBlank(location))       return err("Please enter your location");
            if (isBlank(investmentType)) return err("Please select investment type");
            if (isBlank(amountRange))    return err("Please select investment amount range");

            if (!email.matches("^.+@.+\\..+$")) {
                return err("Please enter a valid email address");
            }

            // Validate attachment type (if any)
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

            // Build Investment object
            Investment inv = new Investment(
                    name.trim(),
                    companyName.trim(),
                    email.trim(),
                    phone.trim(),
                    location.trim(),
                    investmentType.trim(),
                    amountRange.trim(),
                    message.trim(),
                    language
            );

            Investment saved = investmentService.submit(inv, attachment);

            Map<String, Object> response = new HashMap<>();
            response.put("message",
                    "Thank you! We received your inquiry and will contact you soon.");
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