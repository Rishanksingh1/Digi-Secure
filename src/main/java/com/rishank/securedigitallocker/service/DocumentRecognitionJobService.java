package com.rishank.securedigitallocker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class DocumentRecognitionJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentRecognitionJobService.class);

    private final TaskExecutor executor;
    private final DocumentRecognitionService recognitionService;
    private final DocumentService documentService;

    public DocumentRecognitionJobService(
            @Qualifier("documentRecognitionExecutor") TaskExecutor executor,
            DocumentRecognitionService recognitionService,
            DocumentService documentService) {
        this.executor = executor;
        this.recognitionService = recognitionService;
        this.documentService = documentService;
    }

    public boolean supports(String fileName, String contentType) {
        return DocumentRecognitionService.supports(fileName, contentType);
    }

    public void recognize(Long documentId, String ownerEmail, String fileName,
                          String contentType, byte[] content) {
        executor.execute(() -> {
            try {
                DocumentRecognitionResult result = recognitionService.recognize(fileName, contentType, content);
                if (!result.recognized()) {
                    LOGGER.info("Recognition did not produce a suggestion for document {}: {}",
                            documentId, result.notice());
                    return;
                }

                if (!documentService.updateClassification(documentId, ownerEmail,
                        result.documentType(), result.category())) {
                    LOGGER.info("Skipping recognition result for document {} because it no longer exists",
                            documentId);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Unable to apply recognition result for document {}", documentId, exception);
            }
        });
    }
}
