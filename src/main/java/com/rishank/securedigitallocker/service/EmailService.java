package com.rishank.securedigitallocker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String publicBaseUrl;
    private final String smtpHost;
    private final String smtpUsername;
    private final String smtpPassword;
    private final boolean smtpAuth;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from:no-reply@digi-secure.local}") String fromAddress,
                        @Value("${app.public-base-url:}") String publicBaseUrl,
                        @Value("${spring.mail.host:}") String smtpHost,
                        @Value("${spring.mail.username:}") String smtpUsername,
                        @Value("${spring.mail.password:}") String smtpPassword,
                        @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean smtpAuth) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.publicBaseUrl = publicBaseUrl.trim().replaceAll("/+$", "");
        this.smtpHost = smtpHost;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.smtpAuth = smtpAuth;
    }

    public boolean isConfigured() {
        return !smtpHost.isBlank()
                && (!smtpAuth || (!smtpUsername.isBlank() && !smtpPassword.isBlank()));
    }

    public void sendVerification(String email, String token) {
        send(email, "Verify your Digi Secure account",
                "Verify your email address by opening this link:\n"
                        + baseUrl() + "/verify-email?token=" + token
                        + "\nThis link expires in 24 hours.");
    }

    public void sendShareCode(String email, String code) {
        send(email, "Your Digi Secure share code",
                "Your one-time access code is " + code + ". It expires in 10 minutes.");
    }

    public void sendShareAlert(String email, String fileName, String ipAddress, String reason) {
        send(email, "Digi Secure share link activity",
                "The share link for \"" + fileName + "\" was accessed from " + ipAddress
                        + ". Alert reason: " + reason + ".");
    }

    private void send(String recipient, String subject, String body) {
        if (!isConfigured()) {
            throw new org.springframework.mail.MailSendException(
                    "SMTP is not configured. Set SMTP_HOST and, when authentication is enabled, "
                            + "SMTP_USERNAME and SMTP_PASSWORD.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private String baseUrl() {
        return publicBaseUrl.isBlank() ? "http://localhost:8080" : publicBaseUrl;
    }
}
