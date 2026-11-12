package com.rishank.securedigitallocker.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EncryptedFileStorageService {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 32;
    private static final int MAGIC_LENGTH = 4;
    private static final byte VERSION = 1;
    private static final byte[] MAGIC = {'D', 'S', 'L', 'F'};
    private static final byte[] WRAP_AAD = "DSLF:wrapped-key:v1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CONTENT_AAD = "DSLF:content:v1".getBytes(StandardCharsets.US_ASCII);
    private static final int WRAPPED_KEY_LENGTH = KEY_LENGTH + TAG_LENGTH / 8;
    private static final int HEADER_LENGTH = MAGIC_LENGTH + 1 + IV_LENGTH + WRAPPED_KEY_LENGTH + IV_LENGTH;

    private final Path storageDirectory;
    private final byte[] masterKey;
    private final byte[] legacyKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedFileStorageService(
            @Value("${app.storage.location:storage/encrypted}") String storageLocation,
            @Value("${app.encryption.master-key}") String encodedMasterKey,
            @Value("${app.encryption.legacy-secret:}") String legacySecret) {
        this.storageDirectory = Paths.get(storageLocation).toAbsolutePath().normalize();
        this.masterKey = decodeMasterKey(encodedMasterKey);
        this.legacyKey = legacySecret.isBlank() ? null : deriveLegacyKey(legacySecret);
    }

    public String store(MultipartFile file) throws IOException, GeneralSecurityException {
        return store(file.getBytes());
    }

    public String store(byte[] data) throws IOException, GeneralSecurityException {
        Files.createDirectories(storageDirectory);

        byte[] dataKey = new byte[KEY_LENGTH];
        secureRandom.nextBytes(dataKey);
        byte[] wrapIv = randomIv();
        byte[] contentIv = randomIv();

        Cipher wrapCipher = Cipher.getInstance(CIPHER);
        wrapCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"),
                new GCMParameterSpec(TAG_LENGTH, wrapIv));
        wrapCipher.updateAAD(WRAP_AAD);
        byte[] wrappedKey = wrapCipher.doFinal(dataKey);

        Cipher contentCipher = Cipher.getInstance(CIPHER);
        contentCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(dataKey, "AES"),
                new GCMParameterSpec(TAG_LENGTH, contentIv));
        contentCipher.updateAAD(CONTENT_AAD);
        byte[] encryptedContent = contentCipher.doFinal(data);

        String storedFileName = UUID.randomUUID() + ".bin";
        Path destination = storageDirectory.resolve(storedFileName).normalize();
        if (!destination.startsWith(storageDirectory)) {
            throw new IOException("Invalid storage path");
        }

        ByteBuffer payload = ByteBuffer.allocate(HEADER_LENGTH + encryptedContent.length);
        payload.put(MAGIC).put(VERSION).put(wrapIv).put(wrappedKey).put(contentIv).put(encryptedContent);
        Files.write(destination, payload.array());
        return storedFileName;
    }

    public byte[] read(String storedFileName) throws IOException, GeneralSecurityException {
        Path source = storageDirectory.resolve(storedFileName).normalize();
        if (!source.startsWith(storageDirectory) || !Files.isRegularFile(source)) {
            throw new IOException("Encrypted file was not found");
        }

        byte[] payload = Files.readAllBytes(source);
        if (payload.length < MAGIC_LENGTH || !hasMagic(payload)) {
            return readLegacy(payload);
        }
        if (payload.length < HEADER_LENGTH + TAG_LENGTH / 8) {
            throw new IOException("Encrypted file is invalid");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.position(MAGIC_LENGTH);
        byte version = buffer.get();
        if (version != VERSION) {
            throw new IOException("Encrypted file version is not supported");
        }

        byte[] wrapIv = new byte[IV_LENGTH];
        buffer.get(wrapIv);
        byte[] wrappedKey = new byte[WRAPPED_KEY_LENGTH];
        buffer.get(wrappedKey);
        byte[] contentIv = new byte[IV_LENGTH];
        buffer.get(contentIv);
        byte[] encryptedContent = new byte[buffer.remaining()];
        buffer.get(encryptedContent);

        Cipher wrapCipher = Cipher.getInstance(CIPHER);
        wrapCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"),
                new GCMParameterSpec(TAG_LENGTH, wrapIv));
        wrapCipher.updateAAD(WRAP_AAD);
        byte[] dataKey = wrapCipher.doFinal(wrappedKey);

        Cipher contentCipher = Cipher.getInstance(CIPHER);
        contentCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey, "AES"),
                new GCMParameterSpec(TAG_LENGTH, contentIv));
        contentCipher.updateAAD(CONTENT_AAD);
        return contentCipher.doFinal(encryptedContent);
    }

    public void delete(String storedFileName) throws IOException {
        Path source = storageDirectory.resolve(storedFileName).normalize();
        if (!source.startsWith(storageDirectory)) {
            throw new IOException("Invalid storage path");
        }
        Files.deleteIfExists(source);
    }

    private byte[] readLegacy(byte[] payload) throws IOException, GeneralSecurityException {
        if (legacyKey == null) {
            throw new GeneralSecurityException(
                    "This file uses legacy encryption; configure LEGACY_DOCUMENT_ENCRYPTION_SECRET to read it");
        }
        if (payload.length <= IV_LENGTH) {
            throw new IOException("Encrypted file is invalid");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] iv = new byte[IV_LENGTH];
        buffer.get(iv);
        byte[] encryptedContent = new byte[buffer.remaining()];
        buffer.get(encryptedContent);

        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(legacyKey, "AES"),
                new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher.doFinal(encryptedContent);
    }

    private byte[] randomIv() {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        return iv;
    }

    private boolean hasMagic(byte[] payload) {
        return payload.length >= MAGIC_LENGTH
                && Arrays.equals(Arrays.copyOf(payload, MAGIC_LENGTH), MAGIC);
    }

    private byte[] decodeMasterKey(String encodedMasterKey) {
        try {
            byte[] key = Base64.getDecoder().decode(encodedMasterKey);
            if (key.length != KEY_LENGTH) {
                throw new IllegalArgumentException("Expected a Base64-encoded 32-byte AES master key");
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "DOCUMENT_ENCRYPTION_MASTER_KEY must be valid Base64 encoding of exactly 32 bytes",
                    exception);
        }
    }

    private byte[] deriveLegacyKey(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize encryption", exception);
        }
    }
}
