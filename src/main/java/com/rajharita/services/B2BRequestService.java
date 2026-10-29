package com.rajharita.services;

import com.rajharita.documents.B2BRequest;
import com.rajharita.repositories.B2BRequestRepository;
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
public class B2BRequestService {

    @Autowired
    private B2BRequestRepository b2bRequestRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String adminEmail;

    private static final String UPLOAD_DIR = "src/main/resources/static/uploads/b2b/";

    /* =====================================================
       SUBMIT — save file, save DB, send emails
       ===================================================== */
    public B2BRequest submit(B2BRequest request, MultipartFile file) {

        // 1. Handle file upload
        if (file != null && !file.isEmpty()) {
            try {
                String stored = saveFile(file);
                request.setAttachmentPath("/uploads/b2b/" + stored);
                request.setAttachmentName(file.getOriginalFilename());
            } catch (IOException e) {
                throw new RuntimeException("Failed to save attachment: " + e.getMessage());
            }
        }

        // 2. Save to DB
        request.setStatus("NEW");
        request.setQuoteStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());
        B2BRequest saved = b2bRequestRepository.save(request);

        // 3. Admin notification
        try {
            sendAdminEmail(saved);
        } catch (Exception e) {
            System.err.println("⚠️ B2B saved but admin email failed: " + e.getMessage());
        }

        // 4. Auto-reply to buyer
        try {
            sendBuyerAutoReply(saved);
        } catch (Exception e) {
            System.err.println("⚠️ B2B saved but buyer auto-reply failed: " + e.getMessage());
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

        System.out.println("📎 B2B attachment saved: " + target.toAbsolutePath());
        return stored;
    }

    /* =====================================================
       ADMIN EMAIL
       ===================================================== */
    private void sendAdminEmail(B2BRequest r) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(adminEmail, "Rajharita Naturals");
        helper.setTo(adminEmail);
        helper.setSubject("📦 New B2B Bulk Request · " + r.getCompanyName());
        helper.setText(buildAdminEmailHtml(r), true);

        mailSender.send(message);
        System.out.println("📧 B2B admin notification sent");
    }

    private String buildAdminEmailHtml(B2BRequest r) {
        return """
            <div style="font-family: Georgia, serif; max-width: 620px; margin: 0 auto;
                        padding: 28px; background: #f6fbf1; border-radius: 12px;
                        border: 1px solid #cfe0c0;">

                <h2 style="text-align: center; color: #2e440e;
                           letter-spacing: 3px; margin: 0 0 4px 0;">
                    RAJHARITA NATURALS
                </h2>
                <p style="text-align: center; font-style: italic;
                          color: #6b8e23; margin: 0 0 20px 0;">
                    New B2B Bulk Sourcing Request
                </p>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 0 0 18px 0;" />

                <table style="width: 100%%; font-size: 15px; color: #1a1a1a;">
                    <tr><td style="padding: 6px 0;"><strong>Company:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Contact Person:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Email:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Phone:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Material Required:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Quantity:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Frequency:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Delivery Location:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Message:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Attachment:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Quote Status:</strong></td>
                        <td><strong>PENDING</strong> — needs supply confirmation</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Submitted:</strong></td>
                        <td>%s</td></tr>
                </table>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 20px 0;" />

                <p style="font-size: 12px; color: #a8a291; text-align: center;
                          font-style: italic; margin: 0;">
                    Confirm supply first, then send a quote.
                </p>
            </div>
            """.formatted(
                safe(r.getCompanyName()),
                safe(r.getContactPerson()),
                safe(r.getEmail()),
                safe(r.getPhone()),
                safe(r.getMaterialRequired()),
                safe(r.getQuantity()),
                safe(r.getFrequency()),
                safe(r.getDeliveryLocation()),
                safe(r.getMessage()),
                safe(r.getAttachmentName()),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : "-"
            );
    }

    /* =====================================================
       BUYER AUTO-REPLY
       ===================================================== */
    private void sendBuyerAutoReply(B2BRequest r) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(adminEmail, "Rajharita Naturals");
        helper.setTo(r.getEmail());
        helper.setSubject("We received your B2B sourcing request · Rajharita Naturals");
        helper.setText(buildBuyerEmailHtml(r.getContactPerson(), r.getCompanyName()), true);

        mailSender.send(message);
        System.out.println("📧 B2B buyer auto-reply sent to " + r.getEmail());
    }

    private String buildBuyerEmailHtml(String contactPerson, String companyName) {
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
                    Thank you for your bulk sourcing request from
                    <strong>%s</strong>. We have received your requirements
                    successfully.
                </p>

                <p style="font-size: 15px; line-height: 1.75; color: #3a3a3a;">
                    Our supply team will review the material, quantity and
                    delivery details, and share a formal quote with you
                    within <strong>3 business days</strong>.
                </p>

                <p style="font-size: 15px; line-height: 1.75; color: #3a3a3a;">
                    If your requirement is urgent, feel free to reply to
                    this email — we'll prioritize it.
                </p>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 24px 0;" />

                <p style="font-size: 13px; color: #a8a291; text-align: center;
                          font-style: italic; margin: 0;">
                    Warm regards,<br/>Team Rajharita Naturals
                </p>
            </div>
            """.formatted(
                contactPerson != null ? contactPerson : "Sir/Madam",
                companyName != null ? companyName : "your company"
            );
    }

    /* =====================================================
       HELPERS
       ===================================================== */
    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    public List<B2BRequest> getAll() {
        return b2bRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<B2BRequest> getByStatus(String status) {
        return b2bRequestRepository.findByStatus(status);
    }

    public List<B2BRequest> getByQuoteStatus(String quoteStatus) {
        return b2bRequestRepository.findByQuoteStatus(quoteStatus);
    }

    public long countByStatus(String status) {
        return b2bRequestRepository.countByStatus(status);
    }

    public long countByQuoteStatus(String quoteStatus) {
        return b2bRequestRepository.countByQuoteStatus(quoteStatus);
    }
}