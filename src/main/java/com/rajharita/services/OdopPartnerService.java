package com.rajharita.services;

import com.rajharita.documents.OdopPartner;
import com.rajharita.repositories.OdopPartnerRepository;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OdopPartnerService {

    @Autowired
    private OdopPartnerRepository odopPartnerRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String adminEmail;

    private static final String UPLOAD_DIR = "src/main/resources/static/uploads/odop/";

    /* =====================================================
       SUBMIT — save file (if any), save to DB, send emails
       ===================================================== */
    public OdopPartner submit(OdopPartner partner, MultipartFile file) {

        // 1. Handle file upload
        if (file != null && !file.isEmpty()) {
            try {
                String stored = saveFile(file);
                partner.setAttachmentPath("/uploads/odop/" + stored);
                partner.setAttachmentName(file.getOriginalFilename());
            } catch (IOException e) {
                throw new RuntimeException("Failed to save attachment: " + e.getMessage());
            }
        }

        // 2. Save to DB
        partner.setStatus("NEW");
        partner.setCreatedAt(LocalDateTime.now());
        OdopPartner saved = odopPartnerRepository.save(partner);

        // 3. Admin notification
        try {
            sendAdminEmail(saved);
        } catch (Exception e) {
            System.err.println("⚠️ ODOP partner saved but admin email failed: " + e.getMessage());
        }

        // 4. Auto-reply to partner (only if contact looks like an email)
        try {
            if (isEmail(saved.getContact())) {
                sendPartnerAutoReply(saved);
            }
        } catch (Exception e) {
            System.err.println("⚠️ ODOP partner saved but auto-reply failed: " + e.getMessage());
        }

        return saved;
    }

    /* =====================================================
       SAVE FILE
       ===================================================== */
    private String saveFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf("."))
                : "";
        String stored = System.currentTimeMillis() + "_" +
                        UUID.randomUUID().toString().substring(0, 8) + ext;

        Path target = uploadPath.resolve(stored);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("📎 ODOP attachment saved: " + target.toAbsolutePath());
        return stored;
    }

    /* =====================================================
       ADMIN EMAIL
       ===================================================== */
    private void sendAdminEmail(OdopPartner p) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(adminEmail, "Rajharita Naturals");
        helper.setTo(adminEmail);
        helper.setSubject("🏆 New ODOP Partner Inquiry · " + p.getName() + " (" + p.getDistrict() + ")");
        helper.setText(buildAdminEmailHtml(p), true);

        mailSender.send(message);
        System.out.println("📧 ODOP admin notification sent");
    }

    private String buildAdminEmailHtml(OdopPartner p) {
        return """
            <div style="font-family: Georgia, serif; max-width: 600px; margin: 0 auto;
                        padding: 28px; background: #f6fbf1; border-radius: 12px;
                        border: 1px solid #cfe0c0;">

                <h2 style="text-align: center; color: #2e440e;
                           letter-spacing: 3px; margin: 0 0 4px 0;">
                    RAJHARITA NATURALS
                </h2>
                <p style="text-align: center; font-style: italic;
                          color: #6b8e23; margin: 0 0 20px 0;">
                    New ODOP Partner Inquiry
                </p>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 0 0 18px 0;" />

                <table style="width: 100%%; font-size: 15px; color: #1a1a1a;">
                    <tr><td style="padding: 6px 0;"><strong>Name:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>District:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Contact:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>ODOP Product:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Proposed Role:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Message:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Attachment:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Submitted:</strong></td>
                        <td>%s</td></tr>
                </table>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 20px 0;" />

                <p style="font-size: 12px; color: #a8a291; text-align: center;
                          font-style: italic; margin: 0;">
                    Please follow up with this partner.
                </p>
            </div>
            """.formatted(
                safe(p.getName()),
                safe(p.getDistrict()),
                safe(p.getContact()),
                safe(p.getOdopProduct()),
                safe(p.getRole()),
                safe(p.getMessage()),
                safe(p.getAttachmentName()),
                p.getCreatedAt() != null ? p.getCreatedAt().toString() : "-"
            );
    }

    /* =====================================================
       PARTNER AUTO-REPLY
       ===================================================== */
    private void sendPartnerAutoReply(OdopPartner p) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(adminEmail, "Rajharita Naturals");
        helper.setTo(p.getContact());
        helper.setSubject("We received your ODOP partnership inquiry · Rajharita Naturals");
        helper.setText(buildPartnerEmailHtml(p.getName()), true);

        mailSender.send(message);
        System.out.println("📧 ODOP partner auto-reply sent to " + p.getContact());
    }

    private String buildPartnerEmailHtml(String name) {
        return """
            <div style="font-family: Georgia, serif; max-width: 560px; margin: 0 auto;
                        padding: 30px; background: #f6fbf1; border-radius: 12px;
                        border: 1px solid #cfe0c0;">

                <h2 style="text-align: center; color: #2e440e;
                           letter-spacing: 4px; margin: 0 0 6px 0;">
                    RAJHARITA NATURALS
                </h2>
                <p style="text-align: center; font-style: italic;
                          color: #6b8e23; margin: 0 0 26px 0;">
                    Pure by Nature. Made for Wellness.
                </p>

                <p style="font-size: 16px; color: #1a1a1a;">Dear %s,</p>

                <p style="font-size: 15px; line-height: 1.75; color: #3a3a3a;">
                    Thank you for your interest in partnering with
                    <strong>Rajharita Naturals</strong> on your district's
                    ODOP initiative. We have received your inquiry successfully.
                </p>

                <p style="font-size: 15px; line-height: 1.75; color: #3a3a3a;">
                    Our team will review your details and reach out within
                    <strong>2 business days</strong>.
                </p>

                <p style="font-size: 15px; line-height: 1.75; color: #3a3a3a;">
                    If you have any urgent questions, feel free to reply to
                    this email.
                </p>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 24px 0;" />

                <p style="font-size: 13px; color: #a8a291; text-align: center;
                          font-style: italic; margin: 0;">
                    Warm regards,<br/>Team Rajharita Naturals
                </p>
            </div>
            """.formatted(name != null ? name : "Partner");
    }

    /* =====================================================
       HELPERS
       ===================================================== */
    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    private boolean isEmail(String s) {
        return s != null && s.matches("^.+@.+\\..+$");
    }

    public List<OdopPartner> getAll() {
        return odopPartnerRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<OdopPartner> getByStatus(String status) {
        return odopPartnerRepository.findByStatus(status);
    }

    public long countByStatus(String status) {
        return odopPartnerRepository.countByStatus(status);
    }
}