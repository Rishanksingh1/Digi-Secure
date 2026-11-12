package com.rishank.securedigitallocker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "USER";

    @Column(length = 64)
    private String mfaSecret;

    @Column(nullable = false)
    private boolean mfaEnabled = false;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean emailVerified = true;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int mfaFailedAttempts;

    private java.time.LocalDateTime mfaLockedUntil;

    // Constructors
    public User() {}

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getMfaSecret() { return mfaSecret; }
    public void setMfaSecret(String mfaSecret) { this.mfaSecret = mfaSecret; }

    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public int getMfaFailedAttempts() { return mfaFailedAttempts; }
    public void setMfaFailedAttempts(int mfaFailedAttempts) { this.mfaFailedAttempts = mfaFailedAttempts; }

    public java.time.LocalDateTime getMfaLockedUntil() { return mfaLockedUntil; }
    public void setMfaLockedUntil(java.time.LocalDateTime mfaLockedUntil) { this.mfaLockedUntil = mfaLockedUntil; }
}