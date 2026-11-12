package com.rishank.securedigitallocker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.rishank.securedigitallocker.model.Document;
import com.rishank.securedigitallocker.model.User;
import com.rishank.securedigitallocker.repository.ActivityLogRepository;
import com.rishank.securedigitallocker.repository.DocumentRepository;
import com.rishank.securedigitallocker.repository.ShareAccessLogRepository;
import com.rishank.securedigitallocker.repository.SharedLinkRepository;
import com.rishank.securedigitallocker.repository.UserRepository;

class DocumentServiceTests {

    @Test
    void removesDatabaseReferencesBeforeDeletingFileAndDocument() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        SharedLinkRepository sharedLinkRepository = mock(SharedLinkRepository.class);
        ShareAccessLogRepository accessLogRepository = mock(ShareAccessLogRepository.class);
        ActivityLogRepository activityLogRepository = mock(ActivityLogRepository.class);
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        EncryptedFileStorageService storage = mock(EncryptedFileStorageService.class);
        PlatformTransactionManager transactions = transactionManager();
        DocumentService service = service(documentRepository, sharedLinkRepository, accessLogRepository,
                activityLogRepository, activityLogService, storage, transactions);
        Document document = document();

        service.delete(document);

        InOrder order = inOrder(accessLogRepository, sharedLinkRepository, activityLogRepository,
                storage, documentRepository, activityLogService);
        order.verify(accessLogRepository).deleteBySharedLinkDocumentId(7L);
        order.verify(sharedLinkRepository).deleteByDocumentId(7L);
        order.verify(activityLogRepository).deleteByDocumentId(7L);
        order.verify(storage).delete("encrypted.bin");
        order.verify(documentRepository).delete(document);
        order.verify(activityLogService).record("DELETE", "owner@example.com", null);
    }

    @Test
    void keepsDocumentRowWhenFileDeletionFails() throws Exception {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        SharedLinkRepository sharedLinkRepository = mock(SharedLinkRepository.class);
        ShareAccessLogRepository accessLogRepository = mock(ShareAccessLogRepository.class);
        ActivityLogRepository activityLogRepository = mock(ActivityLogRepository.class);
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        EncryptedFileStorageService storage = mock(EncryptedFileStorageService.class);
        DocumentService service = service(documentRepository, sharedLinkRepository, accessLogRepository,
                activityLogRepository, activityLogService, storage, transactionManager());
        Document document = document();
        doThrow(new IOException("storage unavailable")).when(storage).delete("encrypted.bin");

        IOException exception = assertThrows(IOException.class, () -> service.delete(document));
        assertEquals("storage unavailable", exception.getMessage());

        verify(accessLogRepository).deleteBySharedLinkDocumentId(7L);
        verify(sharedLinkRepository).deleteByDocumentId(7L);
        verify(activityLogRepository).deleteByDocumentId(7L);
        verify(documentRepository, never()).delete(any(Document.class));
    }

    private DocumentService service(DocumentRepository documentRepository,
                                    SharedLinkRepository sharedLinkRepository,
                                    ShareAccessLogRepository accessLogRepository,
                                    ActivityLogRepository activityLogRepository,
                                    ActivityLogService activityLogService,
                                    EncryptedFileStorageService storage,
                                    PlatformTransactionManager transactions) {
        return new DocumentService(documentRepository, mock(UserRepository.class), storage,
                sharedLinkRepository, accessLogRepository, activityLogRepository,
                activityLogService, transactions);
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
        return transactions;
    }

    private Document document() {
        User owner = new User();
        owner.setEmail("owner@example.com");
        Document document = new Document();
        document.setId(7L);
        document.setEncryptedPath("encrypted.bin");
        document.setOwner(owner);
        return document;
    }
}
