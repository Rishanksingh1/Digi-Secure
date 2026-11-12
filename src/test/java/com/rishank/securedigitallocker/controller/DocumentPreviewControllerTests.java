package com.rishank.securedigitallocker.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.rishank.securedigitallocker.model.Document;
import com.rishank.securedigitallocker.service.ActivityLogService;
import com.rishank.securedigitallocker.service.DocumentPreviewService;
import com.rishank.securedigitallocker.service.DocumentRecognitionJobService;
import com.rishank.securedigitallocker.service.DocumentService;

class DocumentPreviewControllerTests {

    private final DocumentService documentService = mock(DocumentService.class);
    private final Authentication authentication = authenticatedOwner();
    private final DocumentController controller = new DocumentController(
            documentService,
            mock(ActivityLogService.class),
            mock(DocumentRecognitionJobService.class),
            new DocumentPreviewService());

    private static Authentication authenticatedOwner() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("owner@example.com");
        return authentication;
    }

    @Test
    void servesOwnedPdfInlineForBrowserPreview() throws Exception {
        Document document = new Document("report.pdf", "encrypted.bin", "application/pdf", null);
        byte[] pdf = "%PDF-1.7 preview".getBytes(StandardCharsets.US_ASCII);
        when(documentService.findOwnedDocument(7L, "owner@example.com")).thenReturn(document);
        when(documentService.download(document)).thenReturn(pdf);

        ResponseEntity<ByteArrayResource> response = controller.preview(7L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("inline"));
        assertArrayEquals(pdf, Objects.requireNonNull(response.getBody()).getByteArray());
    }

    @Test
    void servesImageAsSafePngPreview() throws Exception {
        Document document = new Document("photo.jpg", "encrypted.bin", "image/jpeg", null);
        BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream source = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", source);
        when(documentService.findOwnedDocument(8L, "owner@example.com")).thenReturn(document);
        when(documentService.download(document)).thenReturn(source.toByteArray());

        ResponseEntity<ByteArrayResource> response = controller.preview(8L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        byte[] preview = Objects.requireNonNull(response.getBody()).getByteArray();
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                new byte[] {preview[0], preview[1], preview[2], preview[3]});
    }

    @Test
    void explainsWhenDocumentFormatCannotBePreviewed() throws Exception {
        Document document = new Document("archive.zip", "encrypted.bin", "application/zip", null);
        when(documentService.findOwnedDocument(9L, "owner@example.com")).thenReturn(document);
        when(documentService.download(document)).thenReturn(new byte[] {1, 2, 3});

        ResponseEntity<ByteArrayResource> response = controller.preview(9L, authentication);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals(MediaType.TEXT_PLAIN_VALUE, response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(new String(Objects.requireNonNull(response.getBody()).getByteArray(),
                StandardCharsets.UTF_8).contains("Download this file to open it"));
    }

    @Test
    void doesNotPreviewDocumentsNotOwnedByCurrentUser() {
        when(documentService.findOwnedDocument(10L, "owner@example.com"))
                .thenThrow(new IllegalArgumentException("Document was not found"));

        ResponseEntity<ByteArrayResource> response = controller.preview(10L, authentication);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void explainsWhenDocumentCannotBeDecrypted() throws Exception {
        Document document = new Document("report.pdf", "encrypted.bin", "application/pdf", null);
        when(documentService.findOwnedDocument(11L, "owner@example.com")).thenReturn(document);
        when(documentService.download(document))
                .thenThrow(new GeneralSecurityException("decryption failed"));

        ResponseEntity<ByteArrayResource> response = controller.preview(11L, authentication);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(new String(Objects.requireNonNull(response.getBody()).getByteArray(),
                StandardCharsets.UTF_8).contains("configured encryption key"));
    }

    @Test
    void explainsWhenEncryptedDocumentCannotBeRead() throws Exception {
        Document document = new Document("report.pdf", "encrypted.bin", "application/pdf", null);
        when(documentService.findOwnedDocument(12L, "owner@example.com")).thenReturn(document);
        when(documentService.download(document)).thenThrow(new IOException("file missing"));

        ResponseEntity<ByteArrayResource> response = controller.preview(12L, authentication);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(new String(Objects.requireNonNull(response.getBody()).getByteArray(),
                StandardCharsets.UTF_8).contains("missing, damaged"));
    }
}
