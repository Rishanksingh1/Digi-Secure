package com.rishank.securedigitallocker.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, unique = true)
    private String encryptedPath;

    @Column(nullable = false)
    private String fileType;

    @Column(nullable = false)
    private long fileSize;

    @Column(length = 64)
    private String documentType = "Unclassified";

    @Column(length = 64)
    private String category = "General";

    @Column(length = 16)
    private String folderKey;

    @Column(nullable = false)
    private LocalDateTime uploadDate = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    // Constructors
    public Document() {}

    public Document(String fileName, String encryptedPath, String fileType, User owner) {
        this.fileName = fileName;
        this.encryptedPath = encryptedPath;
        this.fileType = fileType;
        this.owner = owner;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getEncryptedPath() { return encryptedPath; }
    public void setEncryptedPath(String encryptedPath) { this.encryptedPath = encryptedPath; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getDocumentType() { return documentType == null ? "Unclassified" : documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getCategory() { return category == null ? "General" : category; }
    public void setCategory(String category) { this.category = category; }

    public String getFolderKey() {
        return folderKey == null
                ? DocumentFolder.classify(fileName, fileType).getKey()
                : folderKey;
    }
    public void setFolderKey(String folderKey) { this.folderKey = folderKey; }

    public String getFolderLabel() {
        DocumentFolder folder = DocumentFolder.fromKey(getFolderKey())
                .orElse(DocumentFolder.OTHER);
        return folder.getLabel();
    }

    public LocalDateTime getUploadDate() { return uploadDate; }
    public void setUploadDate(LocalDateTime uploadDate) { this.uploadDate = uploadDate; }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
}