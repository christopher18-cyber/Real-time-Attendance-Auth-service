package com.genius.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Profile("!local")
public class EmailService {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    @Value("${GMAIL_CLIENT_ID}")
    private String clientId;

    @Value("${GMAIL_CLIENT_SECRET}")
    private String clientSecret;

    @Value("${GMAIL_REFRESH_TOKEN}")
    private String refreshToken;

    @Value("${GMAIL_SENDER}")
    private String senderEmail;

    private final RestTemplate restTemplate;

    private String cachedAccessToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public EmailService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        this.restTemplate = new RestTemplate(factory);
    }

    public void sendVerification(String toEmail, String token) {
        if (toEmail == null || toEmail.contains("\r") || toEmail.contains("\n")) {
            throw new IllegalArgumentException("Invalid recipient email");
        }

        String html = "<p>Your verification code is: <b>" + token + "</b></p>";
        String mime = buildMime(toEmail, "Email Verification Code", html);
        String raw = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mime.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAccessToken());

        Map<String, String> body = new HashMap<>();
        body.put("raw", raw);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(SEND_URL, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to send email: " + response.getStatusCode());
            }
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Gmail API Error: " + e.getStatusCode()
                    + " " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException("Gmail API Error: " + e.getMessage());
        }
    }

    private String buildMime(String to, String subject, String html) {
        String encodedSubject = "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
        return "From: Smart Attendance App <" + senderEmail + ">\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + encodedSubject + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "\r\n"
                + html;
    }

    private synchronized String getAccessToken() {
        // Reuse the cached token until shortly before it expires
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedAccessToken;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    TOKEN_URL, new HttpEntity<>(form, headers), Map.class);

            if (response == null || response.get("access_token") == null) {
                throw new RuntimeException("No access token returned by Google");
            }

            cachedAccessToken = (String) response.get("access_token");
            int expiresIn = ((Number) response.getOrDefault("expires_in", 3600)).intValue();
            tokenExpiry = Instant.now().plusSeconds(expiresIn);
            return cachedAccessToken;
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Google token error: " + e.getStatusCode()
                    + " " + e.getResponseBodyAsString());
        }
    }
}
