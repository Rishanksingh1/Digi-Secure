package com.rishank.securedigitallocker.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import com.rishank.securedigitallocker.model.EmailVerificationToken;
import com.rishank.securedigitallocker.model.User;
import com.rishank.securedigitallocker.repository.EmailVerificationTokenRepository;
import com.rishank.securedigitallocker.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserService(UserRepository userRepository,
                       EmailVerificationTokenRepository verificationTokenRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public User registerUser(String name, String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User();
        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole("USER");
        user.setEmailVerified(false);
        User savedUser = userRepository.save(user);
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        verificationTokenRepository.save(new EmailVerificationToken(
                token, savedUser, LocalDateTime.now().plusHours(24)));
        emailService.sendVerification(normalizedEmail, token);
        return savedUser;
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Verification link is invalid or expired"));
        if (!verificationToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            verificationTokenRepository.delete(verificationToken);
            throw new IllegalArgumentException("Verification link is invalid or expired");
        }
        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        verificationTokenRepository.delete(verificationToken);
    }
}