package com.rishank.securedigitallocker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.rishank.securedigitallocker.model.Document;
import com.rishank.securedigitallocker.service.ActivityLogService;
import com.rishank.securedigitallocker.service.DocumentPreviewService;
import com.rishank.securedigitallocker.service.DocumentRecognitionJobService;
import com.rishank.securedigitallocker.service.DocumentService;

class DocumentUploadControllerTests {

    private final DocumentService documentService = mock(DocumentService.class);
    private final DocumentRecognitionJobService recognitionJobService =
            mock(DocumentRecognitionJobService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final DocumentController controller = new DocumentController(
            documentService,
            activityLogService,
            recognitionJobService,
            new DocumentPreviewService());

    @Test
    void storesTheFileAndSchedulesRecognitionInTheBackground() throws Exception {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("owner@example.com");
        MockMultipartFile file = new MockMultipartFile(
                "file", "identity.png", "image/png", new byte[] {1, 2, 3});
        Document document = new Document("identity.png", "encrypted.bin", "image/png", null);
        document.setId(7L);
        when(documentService.upload(eq("owner@example.com"), eq(file), any(byte[].class),
                eq("Unclassified"), eq("General"))).thenReturn(document);
        when(recognitionJobService.supports("identity.png", "image/png")).thenReturn(true);
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        String result = controller.upload(file, authentication, redirectAttributes);

        assertEquals("redirect:/dashboard", result);
        assertEquals("Document recognition is processing in the background. Refresh later to see its suggestions.",
                redirectAttributes.getFlashAttributes().get("recognitionNotice"));
        verify(recognitionJobService).recognize(
                eq(7L), eq("owner@example.com"), eq("identity.png"), eq("image/png"), any(byte[].class));
        verify(activityLogService).record("UPLOAD", "owner@example.com", document);
    }

    @Test
    void doesNotQueueRecognitionForUnsupportedFileTypes() throws Exception {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("owner@example.com");
        MockMultipartFile file = new MockMultipartFile(
                "file", "archive.zip", "application/zip", new byte[] {1, 2, 3});
        Document document = new Document("archive.zip", "encrypted.bin", "application/zip", null);
        when(documentService.upload(eq("owner@example.com"), eq(file), any(byte[].class),
                eq("Unclassified"), eq("General"))).thenReturn(document);
        when(recognitionJobService.supports("archive.zip", "application/zip")).thenReturn(false);
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        controller.upload(file, authentication, redirectAttributes);

        assertEquals("OCR is available for image and PDF documents. The file was still uploaded.",
                redirectAttributes.getFlashAttributes().get("recognitionNotice"));
        verify(recognitionJobService, never()).recognize(
                any(Long.class), any(String.class), any(String.class), any(String.class), any(byte[].class));
    }
}
