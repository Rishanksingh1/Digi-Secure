package com.rishank.securedigitallocker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.rishank.securedigitallocker.model.Document;
import com.rishank.securedigitallocker.service.ActivityLogService;
import com.rishank.securedigitallocker.service.DocumentPreviewService;
import com.rishank.securedigitallocker.service.DocumentRecognitionJobService;
import com.rishank.securedigitallocker.service.DocumentService;

class DocumentDeleteControllerTests {

    private final DocumentService documentService = mock(DocumentService.class);
    private final Authentication authentication = authenticatedOwner();
    private final DocumentController controller = new DocumentController(
            documentService,
            mock(ActivityLogService.class),
            mock(DocumentRecognitionJobService.class),
            new DocumentPreviewService());

    @Test
    void deletesDocumentAndReturnsUpdatedDashboardCounts() throws Exception {
        Document document = new Document("report.pdf", "encrypted.bin", "application/pdf", null);
        Document remainingDocument = new Document("another.pdf", "another.bin", "application/pdf", null);
        remainingDocument.setFolderKey("pdfs");
        remainingDocument.setFileSize(2048);
        when(documentService.findOwnedDocument(7L, "owner@example.com")).thenReturn(document);
        when(documentService.findByOwner("owner@example.com")).thenReturn(List.of(remainingDocument));

        ResponseEntity<Map<String, Object>> response = controller.deleteAsync(7L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Document deleted", response.getBody().get("message"));
        assertEquals(1, response.getBody().get("totalDocuments"));
        assertEquals(2048L, response.getBody().get("storageBytes"));
        assertEquals(Map.of("pdfs", 1L), response.getBody().get("folderCounts"));
        verify(documentService).delete(document);
    }

    @Test
    void returnsNotFoundWhenDocumentIsNotOwnedOrNoLongerExists() {
        when(documentService.findOwnedDocument(8L, "owner@example.com"))
                .thenThrow(new IllegalArgumentException("Document was not found"));

        ResponseEntity<Map<String, Object>> response = controller.deleteAsync(8L, authentication);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Document not found or no longer available.", response.getBody().get("message"));
    }

    @Test
    void returnsAnExplicitErrorWhenStorageDeletionFails() throws Exception {
        Document document = new Document("report.pdf", "encrypted.bin", "application/pdf", null);
        when(documentService.findOwnedDocument(9L, "owner@example.com")).thenReturn(document);
        doThrow(new IOException("storage unavailable")).when(documentService).delete(document);

        ResponseEntity<Map<String, Object>> response = controller.deleteAsync(9L, authentication);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Could not delete the document. Please try again.", response.getBody().get("message"));
    }

    private static Authentication authenticatedOwner() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("owner@example.com");
        return authentication;
    }
}
