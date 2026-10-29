package com.rajharita.controller;

import com.rajharita.documents.OdopPartner;
import com.rajharita.services.OdopPartnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Controller
public class OdopPartnerController {

    @Autowired
    private OdopPartnerService odopPartnerService;

    /* =====================================================
       SERVE PAGE
       GET /odop → odop.html
       ===================================================== */
    @GetMapping("/odop")
    public String odopPage() {
        return "odop";
    }

    /* =====================================================
       SUBMIT (multipart — file + fields)
       POST /api/odop/submit
       ===================================================== */
    @PostMapping(value = "/api/odop/submit", consumes = "multipart/form-data")
    @ResponseBody
    public ResponseEntity<?> submit(
            @RequestParam("name") String name,
            @RequestParam("district") String district,
            @RequestParam("contact") String contact,
            @RequestParam("odopProduct") String odopProduct,
            @RequestParam("role") String role,
            @RequestParam(value = "message", required = false, defaultValue = "") String message,
            @RequestParam(value = "language", required = false, defaultValue = "en") String language,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment
    ) {
        try {
            // Basic validation
            if (isBlank(name))        return err("Please enter your name");
            if (isBlank(district))    return err("Please enter your district");
            if (isBlank(contact))     return err("Please enter your phone or email");
            if (isBlank(odopProduct)) return err("Please enter your district's ODOP product");
            if (isBlank(role))        return err("Please select a proposed role");

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

            OdopPartner partner = new OdopPartner(
                    name.trim(),
                    district.trim(),
                    contact.trim(),
                    odopProduct.trim(),
                    role.trim(),
                    message.trim(),
                    language
            );

            OdopPartner saved = odopPartnerService.submit(partner, attachment);

            Map<String, Object> response = new HashMap<>();
            response.put("message",
                    "Thank you! We received your partnership inquiry and will contact you soon.");
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