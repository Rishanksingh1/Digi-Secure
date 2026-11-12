package com.rishank.securedigitallocker.service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.rishank.securedigitallocker.model.User;
import com.rishank.securedigitallocker.repository.UserRepository;

@Service
public class MfaService {

    private static final int SECRET_LENGTH = 20;
    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;

    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User account was not found"));
    }

    public String prepareSetup(User user) {
        if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            byte[] secret = new byte[SECRET_LENGTH];
            secureRandom.nextBytes(secret);
            user.setMfaSecret(encodeBase32(secret));
            userRepository.save(user);
        }
        return user.getMfaSecret();
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void enable(User user, String code) {
        User currentUser = userRepository.findByEmailForUpdate(user.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User account was not found"));
        LocalDateTime now = LocalDateTime.now();
        if (currentUser.getMfaLockedUntil() != null && currentUser.getMfaLockedUntil().isAfter(now)) {
            copyMfaAttemptState(currentUser, user);
            throw new IllegalArgumentException("Too many failed codes. MFA is locked for 15 minutes.");
        }
        if (!verifyCode(currentUser.getMfaSecret(), code)) {
            recordFailedAttempt(currentUser);
            copyMfaAttemptState(currentUser, user);
            if (currentUser.getMfaLockedUntil() != null
                    && currentUser.getMfaLockedUntil().isAfter(now)) {
                throw new IllegalArgumentException("Too many failed codes. MFA is locked for 15 minutes.");
            }
            throw new IllegalArgumentException("The verification code is incorrect or expired");
        }
        currentUser.setMfaEnabled(true);
        currentUser.setMfaFailedAttempts(0);
        currentUser.setMfaLockedUntil(null);
        userRepository.save(currentUser);
        copyMfaAttemptState(currentUser, user);
    }

    @Transactional
    public boolean verify(User user, String code) {
        User currentUser = userRepository.findByEmailForUpdate(user.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User account was not found"));
        if (!currentUser.isMfaEnabled()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (currentUser.getMfaLockedUntil() != null && currentUser.getMfaLockedUntil().isAfter(now)) {
            copyMfaAttemptState(currentUser, user);
            return false;
        }
        if (verifyCode(currentUser.getMfaSecret(), code)) {
            currentUser.setMfaFailedAttempts(0);
            currentUser.setMfaLockedUntil(null);
            userRepository.save(currentUser);
            copyMfaAttemptState(currentUser, user);
            return true;
        }
        recordFailedAttempt(currentUser);
        copyMfaAttemptState(currentUser, user);
        return false;
    }

    private void recordFailedAttempt(User user) {
        int failures = user.getMfaFailedAttempts() + 1;
        user.setMfaFailedAttempts(failures);
        if (failures >= 5) {
            user.setMfaFailedAttempts(0);
            user.setMfaLockedUntil(LocalDateTime.now().plusMinutes(15));
        }
        userRepository.save(user);
    }

    private void copyMfaAttemptState(User source, User target) {
        target.setMfaFailedAttempts(source.getMfaFailedAttempts());
        target.setMfaLockedUntil(source.getMfaLockedUntil());
    }

    public String provisioningUri(User user, String secret) {
        String label = "Digi Secure:" + user.getEmail();
        return "otpauth://totp/" + urlEncode(label)
                + "?secret=" + secret
                + "&issuer=" + urlEncode("Digi Secure")
                + "&algorithm=SHA1&digits=6&period=30";
    }

    public String qrCodeDataUri(String provisioningUri) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(provisioningUri, BarcodeFormat.QR_CODE, 280, 280);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (WriterException | java.io.IOException exception) {
            throw new IllegalStateException("Unable to create MFA QR code", exception);
        }
    }

    private boolean verifyCode(String secret, String suppliedCode) {
        if (secret == null || suppliedCode == null || !suppliedCode.matches("\\d{" + CODE_DIGITS + "}")) {
            return false;
        }
        long currentCounter = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            if (generateCode(secret, currentCounter + offset).equals(suppliedCode)) {
                return true;
            }
        }
        return false;
    }

    private String generateCode(String secret, long counter) {
        try {
            byte[] key = decodeBase32(secret);
            byte[] message = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to verify MFA code", exception);
        }
    }

    private byte[] decodeBase32(String value) {
        String normalized = value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z2-7]", "");
        int outputLength = normalized.length() * 5 / 8;
        byte[] output = new byte[outputLength];
        int buffer = 0;
        int bitsInBuffer = 0;
        int index = 0;
        for (char character : normalized.toCharArray()) {
            buffer = (buffer << 5) | "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(character);
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                output[index++] = (byte) (buffer >> bitsInBuffer);
            }
        }
        return output;
    }

    private String encodeBase32(byte[] value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder encoded = new StringBuilder((value.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsInBuffer = 0;
        for (byte current : value) {
            buffer = (buffer << 8) | (current & 0xff);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5;
                encoded.append(alphabet.charAt((buffer >> bitsInBuffer) & 0x1f));
            }
        }
        if (bitsInBuffer > 0) {
            encoded.append(alphabet.charAt((buffer << (5 - bitsInBuffer)) & 0x1f));
        }
        return encoded.toString();
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
