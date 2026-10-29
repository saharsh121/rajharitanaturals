package com.rajharita.services;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;

@Service
public class OtpService {

    private static final SecureRandom random = new SecureRandom();
    private static final int OTP_LENGTH = 6;
    private static final List<String> ADMIN_APPROVER_EMAILS = List.of(
            "ojhasaharsh@gmail.com",
            "harshrajput0x@gmail.com",
            "sanchitasingh0211@gmail.com");

    private final NotificationEmailService emailService;

    public OtpService(NotificationEmailService emailService) {
        this.emailService = emailService;
    }

    /* =====================================================
       Generate a random 6-digit OTP
       ===================================================== */
    public String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }

    /* =====================================================
       Send OTP via EMAIL (HTML formatted)
       ===================================================== */
    public boolean sendOtp(String toEmail, String name, String otp) {
        return emailService.sendHtml(toEmail, "Your Rajharita Naturals Verification Code",
                buildOtpEmailHtml(name, otp));
    }

    public boolean sendPasswordResetOtp(String toEmail, String name, String otp) {
        return emailService.sendHtml(toEmail, "Reset your Rajharita Naturals password", """
                    <div style="font-family: Arial, sans-serif; max-width: 520px; margin: auto; padding: 28px; color: #263522;">
                        <h2>Reset your password</h2>
                        <p>Hello %s,</p>
                        <p>Use this one-time code to set a new password for your Rajharita Naturals account:</p>
                        <p style="font-size: 28px; font-weight: bold; letter-spacing: 6px;">%s</p>
                        <p>This code expires in 5 minutes. If you did not request a password reset, you can ignore this email.</p>
                    </div>
                    """.formatted(escapeHtml(name), escapeHtml(otp)));
    }

    public boolean sendInvitation(String toEmail, String code) {
        return emailService.sendHtml(toEmail, "Your Rajharita Naturals invitation", """
                    <div style="font-family: Georgia, serif; max-width: 520px; margin: auto; padding: 28px;">
                        <h2>Rajharita Naturals invitation</h2>
                        <p>Use this one-time invitation code when creating your account:</p>
                        <p style="font-size: 24px; font-weight: bold; letter-spacing: 4px;">%s</p>
                        <p>This code expires in 7 days.</p>
                    </div>
                    """.formatted(escapeHtml(code)));
    }

    public boolean sendAdminRegistrationOtp(String adminName, String otp) {
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:520px;margin:auto;padding:28px;color:#263522;background:#f7faf4;border:1px solid #dce8d4;border-radius:14px">
                    <h2 style="color:#285b34">New administrator approval</h2>
                    <p>A request was made to create a new Rajharita Naturals administrator account for <strong>%s</strong>.</p>
                    <p>Share this one-time approval code only with a trusted main administrator:</p>
                    <p style="font-size:30px;font-weight:bold;letter-spacing:8px">%s</p>
                    <p>This code expires in 10 minutes and can be used once. If you did not expect this request, ignore this email.</p>
                </div>
                """.formatted(escapeHtml(adminName), escapeHtml(otp));
        boolean sentToAllApprovers = true;
        for (String email : ADMIN_APPROVER_EMAILS) {
            boolean sent = emailService.sendHtml(email,
                    "Approve a Rajharita Naturals admin account", html);
            sentToAllApprovers = sent && sentToAllApprovers;
        }
        return sentToAllApprovers;
    }

    /* =====================================================
       HTML email template
       ===================================================== */
    private String buildOtpEmailHtml(String name, String otp) {
        return """
            <div style="font-family: 'Georgia', serif; max-width: 520px; margin: 0 auto;
                        padding: 30px; background: #f6fbf1; border-radius: 14px;
                        border: 1px solid #cfe0c0;">

                <h2 style="text-align: center; color: #2e440e;
                           font-family: 'Georgia', serif;
                           letter-spacing: 4px; margin: 0 0 6px 0;">
                    RAJHARITA NATURALS
                </h2>
                <p style="text-align: center; font-style: italic;
                          color: #6b8e23; margin: 0 0 24px 0;">
                    Pure by Nature. Made for Wellness.
                </p>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 0 0 24px 0;" />

                <p style="font-size: 16px; color: #1a1a1a;">Hi %s,</p>

                <p style="font-size: 15px; color: #3a3a3a; line-height: 1.7;">
                    Thank you for joining Rajharita Naturals.
                    Use the verification code below to activate your account.
                </p>

                <div style="text-align: center; margin: 28px 0;">
                    <span style="display: inline-block;
                                 font-size: 32px;
                                 letter-spacing: 10px;
                                 font-weight: bold;
                                 color: #1a1a1a;
                                 background: #ffffff;
                                 padding: 16px 28px;
                                 border-radius: 10px;
                                 border: 1.5px solid #4d6b1a;">
                        %s
                    </span>
                </div>

                <p style="font-size: 13px; color: #7a7a7a; text-align: center;">
                    This code is valid for <strong>5 minutes</strong>.
                </p>

                <p style="font-size: 13px; color: #7a7a7a; text-align: center; margin-bottom: 20px;">
                    If you did not request this, please ignore this email.
                </p>

                <hr style="border: none; border-top: 1px solid #cfe0c0; margin: 20px 0;" />

                <p style="font-size: 12px; color: #a8a291; text-align: center;
                          font-style: italic; margin: 0;">
                    © 2026 Rajharita Naturals
                </p>
            </div>
            """.formatted(escapeHtml(name), escapeHtml(otp));
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}