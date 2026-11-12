package com.rishank.securedigitallocker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rishank.securedigitallocker.model.EmailVerificationToken;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);
}
