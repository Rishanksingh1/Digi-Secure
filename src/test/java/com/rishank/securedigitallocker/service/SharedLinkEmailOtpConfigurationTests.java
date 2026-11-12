package com.rishank.securedigitallocker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.rishank.securedigitallocker.repository.ShareAccessLogRepository;
import com.rishank.securedigitallocker.repository.SharedLinkRepository;

class SharedLinkEmailOtpConfigurationTests {

    @Test
    void refusesToCreateEmailOtpLinkWhenSmtpIsNotConfigured() {
        SharedLinkRepository linkRepository = mock(SharedLinkRepository.class);
        DocumentService documentService = mock(DocumentService.class);
        EmailService emailService = mock(EmailService.class);
        when(emailService.isConfigured()).thenReturn(false);
        SharedLinkService sharedLinkService = new SharedLinkService(
                linkRepository,
                mock(ShareAccessLogRepository.class),
                documentService,
                mock(ActivityLogService.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                emailService,
                5);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> sharedLinkService.createWithOptions(
                        "owner@example.com", 7L, true, 60, null,
                        "EMAIL_OTP", null, "recipient@example.com"));

        assertEquals(
                "Email one-time codes are unavailable until the application owner configures SMTP.",
                exception.getMessage());
        verify(linkRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(documentService, never()).findOwnedDocument(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
