package com.rishank.securedigitallocker.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailServiceTests {

    @Test
    void reportsAuthenticatedSmtpUnavailableUntilCredentialsAreConfigured() {
        EmailService emailService = new EmailService(
                mock(JavaMailSender.class), "sender@example.com", "",
                "smtp.gmail.com", "", "", true);

        assertFalse(emailService.isConfigured());
        MailSendException exception = assertThrows(MailSendException.class,
                () -> emailService.sendShareCode("recipient@example.com", "123456"));
        assertTrue(exception.getMessage().contains("SMTP is not configured"));
    }

    @Test
    void acceptsAnUnauthenticatedRelayWhenAHostIsConfigured() {
        EmailService emailService = new EmailService(
                mock(JavaMailSender.class), "sender@example.com", "",
                "mail.example.com", "", "", false);

        assertTrue(emailService.isConfigured());
    }

    @Test
    void sendsShareCodeWhenSmtpIsConfigured() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailService emailService = new EmailService(
                mailSender, "sender@example.com", "",
                "smtp.gmail.com", "sender@example.com", "app-password", true);

        emailService.sendShareCode("recipient@example.com", "123456");

        verify(mailSender).send(org.mockito.ArgumentMatchers.argThat((SimpleMailMessage message) ->
                message.getTo()[0].equals("recipient@example.com")
                        && message.getText().contains("123456")));
    }
}
