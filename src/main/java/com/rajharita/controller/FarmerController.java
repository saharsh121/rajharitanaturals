package com.rajharita.controller;

import com.rajharita.documents.Farmer;
import com.rajharita.services.FarmerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class FarmerController {

    @Autowired
    private FarmerService farmerService;

    /* =====================================================
       SERVE THE PAGE
       GET /farmer  →  farmer.html
       ===================================================== */
    @GetMapping("/farmer")
    public String farmerPage() {
        return "farmer";
    }

    /* =====================================================
       SUBMIT FORM
       POST /api/farmer/submit
       ===================================================== */
    @PostMapping("/api/farmer/submit")
    @ResponseBody
    public ResponseEntity<?> submit(@RequestBody Farmer farmer) {
        try {
            // Basic validation
            if (isBlank(farmer.getName()))       return err("Please enter your name");
            if (isBlank(farmer.getVillage()))    return err("Please enter your village or district");
            if (isBlank(farmer.getPhone()))      return err("Please enter your phone number");
            if (isBlank(farmer.getCropType()))   return err("Please select a crop / product");
            if (isBlank(farmer.getQuantity()))   return err("Please enter quantity available");
            if (isBlank(farmer.getPreferredContact())) return err("Please choose a preferred contact method");

            Farmer saved = farmerService.submit(farmer);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Thank you! We received your details and will contact you soon.");
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