package com.rishank.securedigitallocker.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.rishank.securedigitallocker.model.ActivityLog;
import com.rishank.securedigitallocker.model.Document;
import com.rishank.securedigitallocker.model.DocumentFolder;
import com.rishank.securedigitallocker.model.SharedLink;
import com.rishank.securedigitallocker.model.ShareAccessLog;
import com.rishank.securedigitallocker.service.ActivityLogService;
import com.rishank.securedigitallocker.service.DocumentService;
import com.rishank.securedigitallocker.service.SharedLinkService;

@Controller
public class DashboardController {

    private final DocumentService documentService;
    private final ActivityLogService activityLogService;
    private final SharedLinkService sharedLinkService;

    public DashboardController(DocumentService documentService,
                               ActivityLogService activityLogService,
                               SharedLinkService sharedLinkService) {
        this.documentService = documentService;
        this.activityLogService = activityLogService;
        this.sharedLinkService = sharedLinkService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication,
                            @RequestParam(defaultValue = "") String query,
                            @RequestParam(defaultValue = "all") String folder,
                            Model model) {
        String email = authentication.getName();
        List<Document> allDocuments = documentService.findByOwner(email);
        List<Document> documents = documentService.searchByOwner(email, query);
        String selectedFolder = DocumentFolder.fromKey(folder)
                .map(value -> value.getKey())
                .orElse("all");
        String folderLabel = DocumentFolder.fromKey(selectedFolder)
                .map(value -> value.getLabel())
                .orElse("All files");
        Map<String, Long> folderCounts = allDocuments.stream()
                .collect(Collectors.groupingBy(document -> document.getFolderKey(), Collectors.counting()));
        if (DocumentFolder.fromKey(folder).isEmpty() && !"all".equals(folder)) {
            model.addAttribute("folderError", "That folder was not recognized; showing all files.");
        }

        List<ActivityLog> activityLogs = activityLogService.recentForOwner(email);
        List<SharedLink> activeSharedLinks = sharedLinkService.findByOwner(email).stream()
                .filter(link -> !link.isRevoked())
                .filter(link -> link.getExpiryDate().isAfter(java.time.LocalDateTime.now()))
                .filter(link -> !link.isUseLimitReached())
                .toList();
        List<ShareAccessLog> shareAccessLogs = sharedLinkService.recentAccessForOwner(email);
        model.addAttribute("email", email);
        model.addAttribute("query", query);
        model.addAttribute("selectedFolder", selectedFolder);
        model.addAttribute("folderLabel", folderLabel);
        model.addAttribute("documentFolders", DocumentFolder.values());
        model.addAttribute("folderCounts", folderCounts);
        model.addAttribute("documents", documents);
        model.addAttribute("activityLogs", activityLogs);
        model.addAttribute("activeSharedLinks", activeSharedLinks);
        model.addAttribute("activeSharedLinkCount", activeSharedLinks.size());
        model.addAttribute("shareAccessLogs", shareAccessLogs);
        model.addAttribute("totalDocuments", allDocuments.size());
        model.addAttribute("storageUsed", formatStorage(allDocuments));
        model.addAttribute("emailOtpAvailable", sharedLinkService.isEmailOtpAvailable());
        return "dashboard";
    }

    private String formatStorage(List<Document> documents) {
        long totalBytes = 0;
        for (Document document : documents) {
            totalBytes += document.getFileSize();
        }
        if (totalBytes < 1024 * 1024) {
            return String.format("%.1f KB", totalBytes / 1024.0);
        }
        return String.format("%.1f MB", totalBytes / (1024.0 * 1024.0));
    }
}