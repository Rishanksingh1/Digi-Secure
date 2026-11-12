package com.rishank.securedigitallocker.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rishank.securedigitallocker.model.Document;
import com.rishank.securedigitallocker.service.ActivityLogService;
import com.rishank.securedigitallocker.service.DocumentPreviewService;
import com.rishank.securedigitallocker.service.DocumentRecognitionJobService;
import com.rishank.securedigitallocker.service.DocumentService;

@Controller
@RequestMapping("/documents")
public class DocumentController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentController.class);
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final DocumentService documentService;
    private final ActivityLogService activityLogService;
    private final DocumentRecognitionJobService documentRecognitionJobService;
    private final DocumentPreviewService documentPreviewService;

    public DocumentController(DocumentService documentService,
                              ActivityLogService activityLogService,
                              DocumentRecognitionJobService documentRecognitionJobService,
                              DocumentPreviewService documentPreviewService) {
        this.documentService = documentService;
        this.activityLogService = activityLogService;
        this.documentRecognitionJobService = documentRecognitionJobService;
        this.documentPreviewService = documentPreviewService;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        if (file.getSize() > MAX_FILE_SIZE) {
            redirectAttributes.addFlashAttribute("error", "Files must be 10 MB or smaller");
            return "redirect:/dashboard";
        }

        try {
            String ownerEmail = authentication.getName();
            String fileName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
            String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
            byte[] content = file.getBytes();
            Document document = documentService.upload(ownerEmail, file, content,
                    "Unclassified", "General");
            activityLogService.record("UPLOAD", authentication.getName(), document);
            redirectAttributes.addFlashAttribute("success",
                    "Document uploaded and encrypted in the " + document.getFolderLabel() + " folder");
            if (documentRecognitionJobService.supports(fileName, contentType)) {
                documentRecognitionJobService.recognize(
                        document.getId(), ownerEmail, fileName, contentType, content);
                redirectAttributes.addFlashAttribute("recognitionNotice",
                        "Document recognition is processing in the background. Refresh later to see its suggestions.");
            } else {
                redirectAttributes.addFlashAttribute("recognitionNotice",
                        "OCR is available for image and PDF documents. The file was still uploaded.");
            }
        } catch (IOException | GeneralSecurityException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ByteArrayResource> download(@PathVariable Long id,
                                                       Authentication authentication) {
        try {
            Document document = documentService.findOwnedDocument(id, authentication.getName());
            byte[] content = documentService.download(document);
            activityLogService.record("DOWNLOAD", authentication.getName(), document);
            MediaType mediaType = MediaType.parseMediaType(document.getFileType());
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(document.getFileName())
                    .build();
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(content.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(new ByteArrayResource(content));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        } catch (IOException | GeneralSecurityException exception) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<ByteArrayResource> preview(@PathVariable Long id,
                                                      Authentication authentication) {
        try {
            Document document = documentService.findOwnedDocument(id, authentication.getName());
            byte[] content = documentService.download(document);
            byte[] previewContent;
            MediaType contentType;

            if (documentPreviewService.isPdf(content)) {
                previewContent = content;
                contentType = MediaType.APPLICATION_PDF;
            } else if (documentPreviewService.isImage(content)) {
                previewContent = documentPreviewService.renderImagePreview(content);
                contentType = MediaType.IMAGE_PNG;
            } else {
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                        .body(new ByteArrayResource(
                                "Preview is available for PDF and image files only. Download this file to open it."
                                        .getBytes(StandardCharsets.UTF_8)));
            }

            ContentDisposition disposition = ContentDisposition.inline()
                    .filename(document.getFileName())
                    .build();
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(previewContent.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(new ByteArrayResource(previewContent));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        } catch (GeneralSecurityException exception) {
            LOGGER.error("Unable to decrypt preview for document {}", id, exception);
            return previewError(HttpStatus.INTERNAL_SERVER_ERROR,
                    "This document could not be decrypted. The configured encryption key may not match the key used when it was uploaded.");
        } catch (IOException exception) {
            LOGGER.error("Unable to read or render preview for document {}", id, exception);
            return previewError(HttpStatus.INTERNAL_SERVER_ERROR,
                    "This document could not be read or previewed. It may be missing, damaged, or in an unsupported format.");
        }
    }

    private ResponseEntity<ByteArrayResource> previewError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ByteArrayResource(message.getBytes(StandardCharsets.UTF_8)));
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            deleteOwnedDocument(id, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Document deleted");
        } catch (IOException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/{id}/delete-async")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAsync(@PathVariable Long id,
                                                           Authentication authentication) {
        String owner = authentication.getName();
        try {
            deleteOwnedDocument(id, owner);
            List<Document> remainingDocuments = documentService.findByOwner(owner);
            Map<String, Long> folderCounts = remainingDocuments.stream()
                    .collect(Collectors.groupingBy(document -> document.getFolderKey(), Collectors.counting()));
            long storageBytes = remainingDocuments.stream()
                    .mapToLong(document -> document.getFileSize())
                    .sum();
            return ResponseEntity.ok(Map.of(
                    "message", "Document deleted",
                    "totalDocuments", remainingDocuments.size(),
                    "storageBytes", storageBytes,
                    "folderCounts", folderCounts));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Document not found or no longer available."));
        } catch (IOException exception) {
            LOGGER.error("Unable to delete document {}", id, exception);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Could not delete the document. Please try again."));
        }
    }

    private void deleteOwnedDocument(Long id, String owner) throws IOException {
        Document document = documentService.findOwnedDocument(id, owner);
        documentService.delete(document);
    }
}
