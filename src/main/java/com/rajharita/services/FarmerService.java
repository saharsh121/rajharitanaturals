package com.rajharita.services;

import com.rajharita.documents.Farmer;
import com.rajharita.repositories.FarmerRepository;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FarmerService {

    @Autowired
    private FarmerRepository farmerRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String adminEmail;

    /* =====================================================
       SAVE a new farmer submission + notify admin by email
       ===================================================== */
    public Farmer submit(Farmer farmer) {

        farmer.setStatus("NEW");
        farmer.setCreatedAt(LocalDateTime.now());

        Farmer saved = farmerRepository.save(farmer);

        // Send notification email to admin
        try {
            sendAdminNotification(saved);
        } catch (Exception e) {
            System.err.println("⚠️ Farmer saved but admin email failed: " + e.getMessage());
        }

        return saved;
    }

    /* =====================================================
       ADMIN NOTIFICATION EMAIL
       ===================================================== */
    private void sendAdminNotification(Farmer f) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(adminEmail, "Rajharita Naturals");
        helper.setTo(adminEmail);
        helper.setSubject("🌾 New Farmer Submission · " + f.getName());
        helper.setText(buildFarmerEmailHtml(f), true);

        mailSender.send(message);
        System.out.println("📧 Farmer notification sent to admin");
    }

    private String buildFarmerEmailHtml(Farmer f) {
        return """
            <div style="font-family: Georgia, serif; max-width: 560px; margin: 0 auto;
                        padding: 28px; background: #f6fbf1; border-radius: 12px;
                        border: 1px solid #cfe0c0;">

                <h2 style="text-align: center; color: #2e440e;
                           letter-spacing: 3px; margin: 0 0 4px 0;">
                    RAJHARITA NATURALS
                </h2>
                <p style="text-align: center; font-style: italic;
                          color: #6b8e23; margin: 0 0 20px 0;">
                    New Farmer / Kisan Supply Submission
                </p>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 0 0 18px 0;" />

                <table style="width: 100%%; font-size: 15px; color: #1a1a1a;">
                    <tr><td style="padding: 6px 0;"><strong>Name:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Village / District:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Phone:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Crop / Product:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Quantity:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Preferred Contact:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Message:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Language:</strong></td>
                        <td>%s</td></tr>
                    <tr><td style="padding: 6px 0;"><strong>Submitted:</strong></td>
                        <td>%s</td></tr>
                </table>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 20px 0;" />

                <p style="font-size: 12px; color: #a8a291; text-align: center;
                          font-style: italic; margin: 0;">
                    Please follow up with this farmer.
                </p>
            </div>
            """.formatted(
                safe(f.getName()),
                safe(f.getVillage()),
                safe(f.getPhone()),
                safe(f.getCropType()),
                safe(f.getQuantity()),
                safe(f.getPreferredContact()),
                safe(f.getMessage()),
                safe(f.getLanguage()),
                f.getCreatedAt() != null ? f.getCreatedAt().toString() : "-"
            );
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    /* =====================================================
       QUERY HELPERS
       ===================================================== */
    public List<Farmer> getAll() {
        return farmerRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Farmer> getByStatus(String status) {
        return farmerRepository.findByStatus(status);
    }

    public long countByStatus(String status) {
        return farmerRepository.countByStatus(status);
    }
}