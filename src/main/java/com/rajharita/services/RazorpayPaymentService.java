package com.rajharita.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Service
public class RazorpayPaymentService {

    private final String keyId;
    private final String keySecret;
    private final RestTemplate restTemplate = new RestTemplate();

    public RazorpayPaymentService(@Value("${app.razorpay.key-id:}") String keyId,
                                 @Value("${app.razorpay.key-secret:}") String keySecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    public String createOrder(BigDecimal total, String receipt) {
        requireConfigured();
        long amountInPaise = total.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).longValueExact();
        HttpHeaders headers = headers();
        Map<String, Object> request = Map.of("amount", amountInPaise, "currency", "INR", "receipt", receipt);
        try {
            Map<?, ?> response = restTemplate.postForObject("https://api.razorpay.com/v1/orders",
                    new HttpEntity<>(request, headers), Map.class);
            if (response == null || !(response.get("id") instanceof String orderId)) {
                throw new IllegalStateException("Razorpay did not return a payment order");
            }
            return orderId;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Razorpay could not create a payment order (HTTP "
                    + e.getStatusCode().value() + ")");
        }
    }

    public String getKeyId() {
        requireConfigured();
        return keyId;
    }

    public boolean verifySignature(String orderId, String paymentId, String signature) {
        requireConfigured();
        if (orderId == null || paymentId == null || signature == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(
                    (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, signature.toLowerCase().getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to verify payment signature", e);
        }
    }

    public String refund(String paymentId, BigDecimal amount) {
        requireConfigured();
        long amountInPaise = amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        try {
            Map<?, ?> response = restTemplate.postForObject(
                    "https://api.razorpay.com/v1/payments/" + paymentId + "/refund",
                    new HttpEntity<>(Map.of("amount", amountInPaise), headers()), Map.class);
            if (response == null || !(response.get("id") instanceof String refundId)) {
                throw new IllegalStateException("Razorpay did not return a refund reference");
            }
            return refundId;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("Razorpay refund failed (HTTP " + e.getStatusCode().value() + ")");
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(keyId, keySecret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void requireConfigured() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new IllegalStateException("Razorpay credentials are not configured");
        }
    }
}
