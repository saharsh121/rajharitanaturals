package com.rajharita.controller;

import com.rajharita.documents.InvoiceTemplateSettings;
import com.rajharita.services.InvoiceTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/invoice-template")
public class InvoiceTemplateController {

    private final InvoiceTemplateService invoiceTemplateService;

    public InvoiceTemplateController(InvoiceTemplateService invoiceTemplateService) {
        this.invoiceTemplateService = invoiceTemplateService;
    }

    @GetMapping
    public InvoiceTemplateSettings getTemplate() {
        return invoiceTemplateService.get();
    }

    @PutMapping
    public ResponseEntity<?> saveTemplate(@RequestBody InvoiceTemplateSettings settings) {
        try {
            return ResponseEntity.ok(invoiceTemplateService.save(settings));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}
