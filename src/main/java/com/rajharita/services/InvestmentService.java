package com.rajharita.services;

import com.rajharita.documents.Investment;
import com.rajharita.repositories.InvestmentRepository;
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
public class InvestmentService {

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String adminEmail;

    // Where uploaded files are stored inside the project
    private static final String UPLOAD_DIR = "src/main/resources/static/uploads/investments/";

    /* =====================================================
       SUBMIT — validate, save file, save to DB,
                send admin notification + investor auto-reply
       ===================================================== */
    public Investment submit(Investment investment, MultipartFile file) {

        // 1. Handle file upload (optional)
        if (file != null && !file.isEmpty()) {
            try {
                String stored = saveFile(file);
                investment.setAttachmentPath("/uploads/investments/" + stored);
                investment.setAttachmentName(file.getOriginalFilename());
            } catch (IOException e) {
                throw new RuntimeException("Failed to save attachment: " + e.getMessage());
            }
        }

        // 2. Save to DB
        investment.setStatus("NEW");
        investment.setCreatedAt(LocalDateTime.now());
        Investment saved = investmentRepository.save(investment);

        // 3. Send admin notification
        try {
            sendAdminEmail(saved);
        } catch (Exception e) {
            System.err.println("⚠️ Investment saved but admin email failed: " + e.getMessage());
        }

        // 4. Send investor auto-reply
        try {
            sendInvestorAutoReply(saved);
        } catch (Exception e) {
            System.err.println("⚠️ Investment saved but investor auto-reply failed: " + e.getMessage());
        }

        return saved;
    }

    /* =====================================================
       SAVE UPLOADED FILE → returns stored filename
       ===================================================== */
    private String saveFile(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Build unique filename: timestamp + random + original extension
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf("."))
                : "";
        String stored = System.currentTimeMillis() + "_" +
                        UUID.randomUUID().toString().substring(0, 8) + ext;

        Path target = uploadPath.resolve(stored);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("📎 Investment attachment saved: " + target.toAbsolutePath());
        return stored;
    }

    /* =====================================================
       ADMIN NOTIFICATION EMAIL
       ===================================================== */
    private void sendAdminEmail(Investment i) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(adminEmail, "Rajharita Naturals");
        helper.setTo(adminEmail);
        helper.setSubject("💰 New Investor Inquiry · " + i.getName() + " (" + i.getCompanyName() + ")");
        helper.setText(buildAdminEmailHtml(i), true);

        mailSender.send(message);
        System.out.println("📧 Investment admin notification sent");
    }

    private String buildAdminEmailHtml(Investment i) {
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
                    New Investor Inquiry
                </p>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 0 0 18px 0;" />

                <table style="width: 100%%; font-size: 15px; color: #1a1a1a;">
                    <tr><td style="padding: 6px 0;"><strong>Name:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Company:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Email:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Phone:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Location:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Investment Type:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Amount Range:</strong></td>
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
                    Please follow up with this investor.
                </p>
            </div>
            """.formatted(
                safe(i.getName()),
                safe(i.getCompanyName()),
                safe(i.getEmail()),
                safe(i.getPhone()),
                safe(i.getLocation()),
                safe(i.getInvestmentType()),
                safe(i.getAmountRange()),
                safe(i.getMessage()),
                safe(i.getAttachmentName()),
                i.getCreatedAt() != null ? i.getCreatedAt().toString() : "-"
            );
    }

    /* =====================================================
       INVESTOR AUTO-REPLY EMAIL
       ===================================================== */
    private void sendInvestorAutoReply(Investment i) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(adminEmail, "Rajharita Naturals");
        helper.setTo(i.getEmail());
        helper.setSubject("We received your inquiry · Rajharita Naturals");
        helper.setText(buildInvestorEmailHtml(i.getName()), true);

        mailSender.send(message);
        System.out.println("📧 Investor auto-reply sent to " + i.getEmail());
    }

    private String buildInvestorEmailHtml(String name) {
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
                    <strong>Rajharita Naturals</strong>. We have received
                    your investment inquiry successfully.
                </p>

                <p style="font-size: 15px; line-height: 1.75; color: #3a3a3a;">
                    Our team will review your details and get back to you
                    within <strong>2 business days</strong>.
                </p>

                <p style="font-size: 15px; line-height: 1.75; color: #3a3a3a;">
                    In the meantime, if you have any urgent questions,
                    feel free to reply to this email.
                </p>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 24px 0;" />

                <p style="font-size: 13px; color: #a8a291; text-align: center;
                          font-style: italic; margin: 0;">
                    Warm regards,<br/>Team Rajharita Naturals
                </p>
            </div>
            """.formatted(name != null ? name : "Investor");
    }

    /* =====================================================
       HELPERS
       ===================================================== */
    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    public List<Investment> getAll() {
        return investmentRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Investment> getByStatus(String status) {
        return investmentRepository.findByStatus(status);
    }

    public long countByStatus(String status) {
        return investmentRepository.countByStatus(status);
    }
}