package com.rishank.securedigitallocker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;

import com.rishank.securedigitallocker.model.Document;
import com.rishank.securedigitallocker.service.ActivityLogService;
import com.rishank.securedigitallocker.service.DocumentService;
import com.rishank.securedigitallocker.service.SharedLinkService;

class DashboardControllerTests {

    @Test
    void keepsSearchResultsUnfilteredForClientSideFolderSwitching() {
        DocumentService documentService = mock(DocumentService.class);
        ActivityLogService activityLogService = mock(ActivityLogService.class);
        SharedLinkService sharedLinkService = mock(SharedLinkService.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("owner@example.com");

        Document pdf = new Document();
        pdf.setFileName("report.pdf");
        pdf.setFileType("application/pdf");
        Document image = new Document();
        image.setFileName("photo.png");
        image.setFileType("image/png");
        List<Document> documents = List.of(pdf, image);

        when(documentService.findByOwner("owner@example.com")).thenReturn(documents);
        when(documentService.searchByOwner("owner@example.com", "")).thenReturn(documents);
        when(activityLogService.recentForOwner("owner@example.com")).thenReturn(List.of());
        when(sharedLinkService.findByOwner("owner@example.com")).thenReturn(List.of());
        when(sharedLinkService.recentAccessForOwner("owner@example.com")).thenReturn(List.of());
        when(sharedLinkService.isEmailOtpAvailable()).thenReturn(false);

        ExtendedModelMap model = new ExtendedModelMap();
        DashboardController controller = new DashboardController(
                documentService, activityLogService, sharedLinkService);

        controller.dashboard(authentication, "", "images", model);

        assertEquals(documents, model.getAttribute("documents"));
        assertEquals("images", model.getAttribute("selectedFolder"));
    }
}
