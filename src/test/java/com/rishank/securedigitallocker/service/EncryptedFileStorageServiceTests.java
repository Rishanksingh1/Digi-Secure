package com.rishank.securedigitallocker.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class EncryptedFileStorageServiceTests {

    private static final String LEGACY_SECRET = "test-only-legacy-secret";

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesEachFileUsingEnvelopeEncryptionAndReadsItBack() throws Exception {
        byte[] masterKey = new byte[32];
        new SecureRandom().nextBytes(masterKey);
        EncryptedFileStorageService storage = new EncryptedFileStorageService(
                temporaryDirectory.toString(), Base64.getEncoder().encodeToString(masterKey), "");
        byte[] content = "confidential document".getBytes();

        String firstFile = storage.store(new MockMultipartFile("file", "one.txt", "text/plain", content));
        String secondFile = storage.store(new MockMultipartFile("file", "two.txt", "text/plain", content));
        byte[] firstPayload = Files.readAllBytes(temporaryDirectory.resolve(firstFile));
        byte[] secondPayload = Files.readAllBytes(temporaryDirectory.resolve(secondFile));

        assertArrayEquals(content, storage.read(firstFile));
        assertArrayEquals(content, storage.read(secondFile));
        assertFalse(Arrays.equals(firstPayload, secondPayload));
        assertArrayEquals(new byte[] {'D', 'S', 'L', 'F'}, Arrays.copyOf(firstPayload, 4));
    }

    @Test
    void readsLegacyGcmFilesWhenLegacySecretIsConfigured() throws Exception {
        byte[] masterKey = new byte[32];
        new SecureRandom().nextBytes(masterKey);
        EncryptedFileStorageService storage = new EncryptedFileStorageService(
                temporaryDirectory.toString(), Base64.getEncoder().encodeToString(masterKey), LEGACY_SECRET);
        byte[] content = "legacy document".getBytes();
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        byte[] legacyKey = MessageDigest.getInstance("SHA-256")
                .digest(LEGACY_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(legacyKey, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(content);
        byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        Files.write(temporaryDirectory.resolve("legacy.bin"), payload);

        assertArrayEquals(content, storage.read("legacy.bin"));
    }

    @Test
    void rejectsTamperedEnvelopeEncryptedFiles() throws Exception {
        byte[] masterKey = new byte[32];
        new SecureRandom().nextBytes(masterKey);
        EncryptedFileStorageService storage = new EncryptedFileStorageService(
                temporaryDirectory.toString(), Base64.getEncoder().encodeToString(masterKey), "");
        String storedFile = storage.store(
                new MockMultipartFile("file", "document.txt", "text/plain", "sensitive".getBytes()));
        Path storedPath = temporaryDirectory.resolve(storedFile);
        byte[] payload = Files.readAllBytes(storedPath);
        payload[payload.length - 1] ^= 1;
        Files.write(storedPath, payload);

        assertThrows(GeneralSecurityException.class, () -> storage.read(storedFile));
    }

    @Test
    void rejectsInvalidMasterKeyAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptedFileStorageService(
                temporaryDirectory.toString(), "not-a-valid-32-byte-key", ""));
    }
}
