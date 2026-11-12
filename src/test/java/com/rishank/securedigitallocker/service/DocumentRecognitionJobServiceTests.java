package com.rishank.securedigitallocker.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

class DocumentRecognitionJobServiceTests {

    @Test
    void appliesSuccessfulRecognitionToTheUploadedDocument() {
        TaskExecutor executor = new SyncTaskExecutor();
        DocumentRecognitionService recognitionService = mock(DocumentRecognitionService.class);
        DocumentService documentService = mock(DocumentService.class);
        DocumentRecognitionJobService jobService = new DocumentRecognitionJobService(
                executor, recognitionService, documentService);
        byte[] content = {1, 2, 3};
        DocumentRecognitionResult result = new DocumentRecognitionResult(
                "Identity document", "Identity", java.util.Map.of(), "Recognized", true);
        when(recognitionService.recognize("identity.pdf", "application/pdf", content)).thenReturn(result);
        when(documentService.updateClassification(7L, "owner@example.com",
                "Identity document", "Identity")).thenReturn(true);

        jobService.recognize(7L, "owner@example.com", "identity.pdf", "application/pdf", content);

        verify(recognitionService).recognize("identity.pdf", "application/pdf", content);
        verify(documentService).updateClassification(7L, "owner@example.com",
                "Identity document", "Identity");
    }
}
