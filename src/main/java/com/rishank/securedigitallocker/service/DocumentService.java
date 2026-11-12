package com.rishank.securedigitallocker.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.rishank.securedigitallocker.model.Document;
import com.rishank.securedigitallocker.model.DocumentFolder;
import com.rishank.securedigitallocker.model.User;
import com.rishank.securedigitallocker.repository.DocumentRepository;
import com.rishank.securedigitallocker.repository.ActivityLogRepository;
import com.rishank.securedigitallocker.repository.ShareAccessLogRepository;
import com.rishank.securedigitallocker.repository.SharedLinkRepository;
import com.rishank.securedigitallocker.repository.UserRepository;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final EncryptedFileStorageService encryptedFileStorageService;
    private final SharedLinkRepository sharedLinkRepository;
    private final ShareAccessLogRepository shareAccessLogRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogService activityLogService;
    private final TransactionTemplate transactionTemplate;

    public DocumentService(DocumentRepository documentRepository,
                           UserRepository userRepository,
                           EncryptedFileStorageService encryptedFileStorageService,
                           SharedLinkRepository sharedLinkRepository,
                           ShareAccessLogRepository shareAccessLogRepository,
                           ActivityLogRepository activityLogRepository,
                           ActivityLogService activityLogService,
                           PlatformTransactionManager transactionManager) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.encryptedFileStorageService = encryptedFileStorageService;
        this.sharedLinkRepository = sharedLinkRepository;
        this.shareAccessLogRepository = shareAccessLogRepository;
        this.activityLogRepository = activityLogRepository;
        this.activityLogService = activityLogService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public List<Document> findByOwner(String email) {
        return documentRepository.findByOwnerEmailOrderByUploadDateDesc(email);
    }

    public List<Document> searchByOwner(String email, String query) {
        if (query == null || query.isBlank()) {
            return findByOwner(email);
        }
        return documentRepository.findByOwnerEmailAndFileNameContainingIgnoreCaseOrderByUploadDateDesc(
                email, query.trim());
    }

    @Transactional
    public Document upload(String email, MultipartFile file, String documentType, String category)
            throws IOException, GeneralSecurityException {
        return upload(email, file, file.getBytes(), documentType, category);
    }

    @Transactional
    public Document upload(String email, MultipartFile file, byte[] content,
                           String documentType, String category)
            throws IOException, GeneralSecurityException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a file to upload");
        }

        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User account was not found"));
        String encryptedPath = encryptedFileStorageService.store(content);

        try {
            Document document = new Document();
            document.setFileName(file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
            document.setEncryptedPath(encryptedPath);
            document.setFileType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
            document.setFileSize(file.getSize());
            document.setDocumentType(documentType);
            document.setCategory(category);
            document.setFolderKey(DocumentFolder.classify(document.getFileName(), document.getFileType()).getKey());
            document.setOwner(owner);
            return documentRepository.save(document);
        } catch (RuntimeException exception) {
            encryptedFileStorageService.delete(encryptedPath);
            throw exception;
        }
    }

    public Document findOwnedDocument(Long id, String email) {
        return documentRepository.findByIdAndOwnerEmail(id, email)
                .orElseThrow(() -> new IllegalArgumentException("Document was not found"));
    }

    public byte[] download(Document document) throws IOException, GeneralSecurityException {
        return encryptedFileStorageService.read(document.getEncryptedPath());
    }

    public boolean updateClassification(Long documentId, String ownerEmail,
                                        String documentType, String category) {
        return documentRepository.findByIdAndOwnerEmail(documentId, ownerEmail)
                .map(document -> {
                    document.setDocumentType(documentType);
                    document.setCategory(category);
                    documentRepository.save(document);
                    return true;
                })
                .orElse(false);
    }

    public void delete(Document document) throws IOException {
        String encryptedPath = document.getEncryptedPath();
        transactionTemplate.executeWithoutResult(status -> {
            shareAccessLogRepository.deleteBySharedLinkDocumentId(document.getId());
            sharedLinkRepository.deleteByDocumentId(document.getId());
            activityLogRepository.deleteByDocumentId(document.getId());
        });
        encryptedFileStorageService.delete(encryptedPath);
        transactionTemplate.executeWithoutResult(status -> {
            documentRepository.delete(document);
            activityLogService.record("DELETE", document.getOwner().getEmail(), null);
        });
    }
}
