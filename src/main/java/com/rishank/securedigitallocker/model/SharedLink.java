package com.rishank.securedigitallocker.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "shared_links")
public class SharedLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private boolean allowDownload = true;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Column(nullable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean revoked;

    private Integer maxViews;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long viewCount;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long downloadCount;

    @Column(nullable = false, length = 16, columnDefinition = "varchar(16) default 'NONE'")
    private String accessMode = "NONE";

    private String accessSecret;

    private String recipientEmail;

    private String accessOtpHash;

    private LocalDateTime accessOtpExpiresAt;

    private LocalDateTime accessOtpSentAt;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int accessFailedAttempts;

    private LocalDateTime accessLockedUntil;

    @ManyToOne
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    // Constructors
    public SharedLink() {}

    public SharedLink(String token, boolean allowDownload, LocalDateTime expiryDate, Document document) {
        this.token = token;
        this.allowDownload = allowDownload;
        this.expiryDate = expiryDate;
        this.document = document;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public boolean isAllowDownload() { return allowDownload; }
    public void setAllowDownload(boolean allowDownload) { this.allowDownload = allowDownload; }

    public LocalDateTime getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }

    public Integer getMaxViews() { return maxViews; }
    public void setMaxViews(Integer maxViews) { this.maxViews = maxViews; }

    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }

    public long getDownloadCount() { return downloadCount; }
    public void setDownloadCount(long downloadCount) { this.downloadCount = downloadCount; }

    public long getLimitedUseCount() {
        return allowDownload ? downloadCount : viewCount;
    }

    public boolean isUseLimitReached() {
        return maxViews != null && getLimitedUseCount() >= maxViews;
    }

    public String getAccessMode() { return accessMode; }
    public void setAccessMode(String accessMode) { this.accessMode = accessMode; }

    public String getAccessSecret() { return accessSecret; }
    public void setAccessSecret(String accessSecret) { this.accessSecret = accessSecret; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public String getAccessOtpHash() { return accessOtpHash; }
    public void setAccessOtpHash(String accessOtpHash) { this.accessOtpHash = accessOtpHash; }

    public LocalDateTime getAccessOtpExpiresAt() { return accessOtpExpiresAt; }
    public void setAccessOtpExpiresAt(LocalDateTime accessOtpExpiresAt) { this.accessOtpExpiresAt = accessOtpExpiresAt; }

    public LocalDateTime getAccessOtpSentAt() { return accessOtpSentAt; }
    public void setAccessOtpSentAt(LocalDateTime accessOtpSentAt) { this.accessOtpSentAt = accessOtpSentAt; }

    public int getAccessFailedAttempts() { return accessFailedAttempts; }
    public void setAccessFailedAttempts(int accessFailedAttempts) { this.accessFailedAttempts = accessFailedAttempts; }

    public LocalDateTime getAccessLockedUntil() { return accessLockedUntil; }
    public void setAccessLockedUntil(LocalDateTime accessLockedUntil) { this.accessLockedUntil = accessLockedUntil; }
}