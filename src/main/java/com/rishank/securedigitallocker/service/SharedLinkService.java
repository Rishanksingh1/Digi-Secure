package com.rishank.securedigitallocker.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.rishank.securedigitallocker.model.Document;
import com.rishank.securedigitallocker.model.ShareAccessLog;
import com.rishank.securedigitallocker.model.SharedLink;
import com.rishank.securedigitallocker.repository.ShareAccessLogRepository;
import com.rishank.securedigitallocker.repository.SharedLinkRepository;

@Service
public class SharedLinkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SharedLinkService.class);
    private static final int MAX_LINK_USES = 1000;

    private final SharedLinkRepository sharedLinkRepository;
    private final ShareAccessLogRepository accessLogRepository;
    private final DocumentService documentService;
    private final ActivityLogService activityLogService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final int alertThreshold;
    private final SecureRandom secureRandom = new SecureRandom();

    public SharedLinkService(SharedLinkRepository sharedLinkRepository,
                             ShareAccessLogRepository accessLogRepository,
                             DocumentService documentService,
                             ActivityLogService activityLogService,
                             PasswordEncoder passwordEncoder,
                             EmailService emailService,
                             @Value("${app.share.alert-view-threshold:5}") int alertThreshold) {
        this.sharedLinkRepository = sharedLinkRepository;
        this.accessLogRepository = accessLogRepository;
        this.documentService = documentService;
        this.activityLogService = activityLogService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.alertThreshold = Math.max(1, alertThreshold);
    }

    @Transactional
    public SharedLink create(String email, Long documentId, boolean allowDownload, int expiryHours) {
        if (expiryHours < 1 || expiryHours > 24 * 30) {
            throw new IllegalArgumentException("Expiry must be between 1 hour and 30 days");
        }
        return createWithOptions(email, documentId, allowDownload, expiryHours * 60, null,
                "NONE", null, null);
    }

    @Transactional
    public SharedLink createWithExpiryMinutes(
            String email, Long documentId, boolean allowDownload, int expiryMinutes) {
        return createWithOptions(email, documentId, allowDownload, expiryMinutes, null,
                "NONE", null, null);
    }

    @Transactional
    public SharedLink createWithOptions(String email,
                                        Long documentId,
                                        boolean allowDownload,
                                        int expiryMinutes,
                                        Integer maxUses,
                                        String requestedAccessMode,
                                        String pin,
                                        String recipientEmail) {
        if (expiryMinutes < 1 || expiryMinutes > 24 * 60 * 30) {
            throw new IllegalArgumentException("Expiry must be between 1 minute and 30 days");
        }
        if (maxUses != null && (maxUses < 1 || maxUses > MAX_LINK_USES)) {
            throw new IllegalArgumentException("Maximum uses must be between 1 and 1000");
        }
        String accessMode = requestedAccessMode == null
                ? "NONE" : requestedAccessMode.trim().toUpperCase(Locale.ROOT);
        if (!List.of("NONE", "PIN", "EMAIL_OTP").contains(accessMode)) {
            throw new IllegalArgumentException("Choose no extra protection, PIN, or email code");
        }
        if ("EMAIL_OTP".equals(accessMode) && !emailService.isConfigured()) {
            throw new IllegalArgumentException(
                    "Email one-time codes are unavailable until the application owner configures SMTP.");
        }
        if ("PIN".equals(accessMode) && (pin == null || !pin.matches("[A-Za-z0-9!@#$%^&*()_+.-]{4,64}"))) {
            throw new IllegalArgumentException("PIN must be 4 to 64 supported characters");
        }
        if ("EMAIL_OTP".equals(accessMode)
                && (recipientEmail == null
                    || !recipientEmail.trim().matches("(?i)^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$"))) {
            throw new IllegalArgumentException("Enter a valid recipient email for email verification");
        }

        Document document = documentService.findOwnedDocument(documentId, email);
        SharedLink link = new SharedLink(
                UUID.randomUUID().toString().replace("-", ""),
                allowDownload,
                LocalDateTime.now().plusMinutes(expiryMinutes),
                document);
        link.setMaxViews(maxUses);
        link.setAccessMode(accessMode);
        if ("PIN".equals(accessMode)) {
            link.setAccessSecret(passwordEncoder.encode(pin));
        }
        if ("EMAIL_OTP".equals(accessMode)) {
            link.setRecipientEmail(recipientEmail.trim().toLowerCase(Locale.ROOT));
        }
        SharedLink savedLink = sharedLinkRepository.save(link);
        activityLogService.record("SHARE", email, document);
        return savedLink;
    }

    @Transactional(readOnly = true)
    public SharedLink findActive(String token) {
        SharedLink link = sharedLinkRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Share link was not found"));
        ensureActive(link);
        return link;
    }

    @Transactional(readOnly = true)
    public SharedLink findActiveForCurrentView(String token) {
        SharedLink link = sharedLinkRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Share link was not found"));
        ensureAvailableForCurrentView(link);
        return link;
    }

    @Transactional
    public boolean sendAccessCode(String token) {
        SharedLink link = requireLockedLink(token);
        if (!"EMAIL_OTP".equals(link.getAccessMode())) {
            throw new IllegalArgumentException("This share link does not use email verification");
        }
        if (!emailService.isConfigured()) {
            throw new org.springframework.mail.MailSendException(
                    "SMTP is not configured for email one-time codes.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (link.getAccessOtpSentAt() != null
                && link.getAccessOtpSentAt().isAfter(now.minusMinutes(1))) {
            return false;
        }

        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        link.setAccessOtpHash(passwordEncoder.encode(code));
        link.setAccessOtpExpiresAt(now.plusMinutes(10));
        link.setAccessOtpSentAt(now);
        link.setAccessFailedAttempts(0);
        sharedLinkRepository.save(link);
        emailService.sendShareCode(link.getRecipientEmail(), code);
        return true;
    }

    public boolean isEmailOtpAvailable() {
        return emailService.isConfigured();
    }

    @Transactional
    public boolean verifyAccess(String token, String suppliedCode) {
        SharedLink link = requireLockedLink(token);
        if ("NONE".equals(link.getAccessMode())) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        if (link.getAccessLockedUntil() != null && link.getAccessLockedUntil().isAfter(now)) {
            return false;
        }
        boolean valid;
        if ("PIN".equals(link.getAccessMode())) {
            valid = suppliedCode != null && passwordEncoder.matches(suppliedCode, link.getAccessSecret());
        } else {
            valid = suppliedCode != null
                    && link.getAccessOtpHash() != null
                    && link.getAccessOtpExpiresAt() != null
                    && link.getAccessOtpExpiresAt().isAfter(now)
                    && passwordEncoder.matches(suppliedCode, link.getAccessOtpHash());
        }
        if (!valid) {
            int failures = link.getAccessFailedAttempts() + 1;
            link.setAccessFailedAttempts(failures);
            if (failures >= 5) {
                link.setAccessFailedAttempts(0);
                link.setAccessLockedUntil(now.plusMinutes(15));
            }
            sharedLinkRepository.save(link);
            return false;
        }
        link.setAccessFailedAttempts(0);
        link.setAccessLockedUntil(null);
        if ("EMAIL_OTP".equals(link.getAccessMode())) {
            link.setAccessOtpHash(null);
            link.setAccessOtpExpiresAt(null);
        }
        sharedLinkRepository.save(link);
        return true;
    }

    @Transactional
    public void recordAccess(String token, String action, String ipAddress, String userAgent) {
        SharedLink link = requireLockedLink(token);
        if (!"VIEW".equals(action) && !"DOWNLOAD".equals(action)) {
            throw new IllegalArgumentException("Unsupported share access action");
        }
        if (link.isUseLimitReached()) {
            throw new IllegalArgumentException("This share link has reached its use limit");
        }

        boolean newIp = !accessLogRepository.existsBySharedLinkIdAndIpAddress(link.getId(), ipAddress);
        if ("VIEW".equals(action)) {
            link.setViewCount(link.getViewCount() + 1);
        } else {
            link.setDownloadCount(link.getDownloadCount() + 1);
        }
        sharedLinkRepository.save(link);
        accessLogRepository.save(new ShareAccessLog(link, action, ipAddress, userAgent));
        long totalAccesses = link.getViewCount() + link.getDownloadCount();
        boolean thresholdReached = totalAccesses >= alertThreshold
                && (totalAccesses - alertThreshold) % alertThreshold == 0;
        if (newIp || thresholdReached) {
            String reason = newIp ? "new IP address" : "access threshold reached (" + totalAccesses + ")";
            String ownerEmail = link.getDocument().getOwner().getEmail();
            String fileName = link.getDocument().getFileName();
            afterCommit(() -> {
                try {
                    emailService.sendShareAlert(ownerEmail, fileName, ipAddress, reason);
                } catch (org.springframework.mail.MailException exception) {
                    LOGGER.error("Unable to send share-access alert for link {}", link.getId(), exception);
                }
            });
        }
    }

    @Transactional
    public void revoke(String token, String ownerEmail) {
        SharedLink link = sharedLinkRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new IllegalArgumentException("Share link was not found"));
        if (!link.getDocument().getOwner().getEmail().equals(ownerEmail)) {
            throw new IllegalArgumentException("Share link was not found");
        }
        if (!link.isRevoked()) {
            link.setRevoked(true);
            sharedLinkRepository.save(link);
            activityLogService.record("REVOKE_SHARE", ownerEmail, link.getDocument());
        }
    }

    @Transactional(readOnly = true)
    public List<SharedLink> findByOwner(String email) {
        return sharedLinkRepository.findByDocumentOwnerEmailOrderByCreatedDateDesc(email);
    }

    @Transactional(readOnly = true)
    public long countActiveForOwner(String email) {
        return sharedLinkRepository.countByDocumentOwnerEmailAndRevokedFalseAndExpiryDateAfter(
                email, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<ShareAccessLog> recentAccessForOwner(String email) {
        return accessLogRepository.findTop100BySharedLinkDocumentOwnerEmailOrderByAccessedAtDesc(email);
    }

    private SharedLink requireLockedLink(String token) {
        SharedLink link = sharedLinkRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new IllegalArgumentException("Share link was not found"));
        ensureActive(link);
        return link;
    }

    private void ensureActive(SharedLink link) {
        if (link.isRevoked()) {
            throw new IllegalArgumentException("This share link has been revoked");
        }
        if (!link.getExpiryDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("This share link has expired");
        }
        if (link.isUseLimitReached()) {
            throw new IllegalArgumentException("This share link has reached its use limit");
        }
        if (link.getAccessLockedUntil() != null
                && link.getAccessLockedUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Access to this link is temporarily locked");
        }
    }

    private void ensureAvailableForCurrentView(SharedLink link) {
        if (link.isRevoked()) {
            throw new IllegalArgumentException("This share link has been revoked");
        }
        if (!link.getExpiryDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("This share link has expired");
        }
        if (link.getAccessLockedUntil() != null
                && link.getAccessLockedUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Access to this link is temporarily locked");
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
