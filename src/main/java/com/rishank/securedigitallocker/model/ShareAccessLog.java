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
@Table(name = "share_access_logs")
public class ShareAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "shared_link_id", nullable = false)
    private SharedLink sharedLink;

    @Column(nullable = false, length = 16)
    private String action;

    @Column(nullable = false)
    private LocalDateTime accessedAt = LocalDateTime.now();

    @Column(nullable = false, length = 64)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    public ShareAccessLog() {}

    public ShareAccessLog(SharedLink sharedLink, String action, String ipAddress, String userAgent) {
        this.sharedLink = sharedLink;
        this.action = action;
        this.ipAddress = ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress.substring(0, Math.min(64, ipAddress.length()));
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(512, userAgent.length()));
    }

    public Long getId() { return id; }
    public SharedLink getSharedLink() { return sharedLink; }
    public String getAction() { return action; }
    public LocalDateTime getAccessedAt() { return accessedAt; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public ClientDetails getClientDetails() { return ClientDetails.fromUserAgent(userAgent); }
}
