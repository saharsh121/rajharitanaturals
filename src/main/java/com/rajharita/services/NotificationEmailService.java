package com.rajharita.services;

import com.rajharita.documents.Order;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.stream.Collectors;

@Service
public class NotificationEmailService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public NotificationEmailService(JavaMailSender mailSender,
                                    @Value("${spring.mail.username}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    public boolean sendHtml(String toEmail, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "Rajharita Naturals");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            return true;
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            logger.warn("Unable to send email with subject '{}'; the primary operation will continue.", subject, e);
            return false;
        }
    }

    public void sendOrderConfirmation(Order order) {
        String items = order.getItems() == null ? "" : order.getItems().stream()
                .map(item -> "<li>" + escapeHtml(item.getName()) + " × " + item.getQuantity()
                        + " — ₹" + item.getLineTotal() + "</li>")
                .collect(Collectors.joining());
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:28px;color:#263522;background:#f7faf4;border:1px solid #dce8d4;border-radius:14px">
                    <h1 style="color:#285b34">Thank you for your order!</h1>
                    <p>Hello %s,</p>
                    <p>Your order has been received. We’ll keep you updated as it moves through fulfilment.</p>
                    <p><strong>Order number:</strong> %s<br><strong>Payment:</strong> %s (%s)</p>
                    <h2>Order summary</h2><ul>%s</ul>
                    <p><strong>Total: ₹%s</strong></p>
                    <p>Thank you for choosing Rajharita Naturals.</p>
                </div>
                """.formatted(
                escapeHtml(order.getUsername()),
                escapeHtml(order.getOrderNumber()),
                escapeHtml(order.getPaymentMethod()),
                escapeHtml(order.getPaymentStatus()),
                items,
                order.getTotal());
        sendHtml(order.getCustomerEmail(), "Order confirmation · " + order.getOrderNumber(), html);
    }

    public void sendDeliveryThankYou(Order order) {
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:28px;color:#263522;background:#f7faf4;border:1px solid #dce8d4;border-radius:14px">
                    <h1 style="color:#285b34">Your order has been delivered!</h1>
                    <p>Hello %s,</p>
                    <p>We hope you enjoy your order <strong>%s</strong>.</p>
                    <p>Thank you for choosing Rajharita Naturals. We truly appreciate your support and hope to serve you again.</p>
                    <p>With gratitude,<br><strong>Rajharita Naturals</strong></p>
                </div>
                """.formatted(escapeHtml(order.getUsername()), escapeHtml(order.getOrderNumber()));
        sendHtml(order.getCustomerEmail(), "Thank you for choosing Rajharita Naturals", html);
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
