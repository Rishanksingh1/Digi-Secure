package com.rishank.securedigitallocker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ExtendedModelMap;

import com.rishank.securedigitallocker.model.Document;
import com.rishank.securedigitallocker.model.SharedLink;
import com.rishank.securedigitallocker.service.DocumentPreviewService;
import com.rishank.securedigitallocker.service.DocumentService;
import com.rishank.securedigitallocker.service.SharedLinkService;

class ShareControllerTests {

    @Test
    void unprotectedSharePageSetsAccessRequiredFalse() {
        SharedLinkService sharedLinkService = mock(SharedLinkService.class);
        SharedLink link = new SharedLink("token", true,
                java.time.LocalDateTime.now().plusHours(1), new Document());
        when(sharedLinkService.findActive("token")).thenReturn(link);
        ShareController controller = new ShareController(sharedLinkService, mock(DocumentService.class),
                new DocumentPreviewService(), "");
        ExtendedModelMap model = new ExtendedModelMap();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String view = controller.viewSharedDocument("token", model, mock(HttpServletResponse.class),
                mock(HttpSession.class), request);

        assertEquals("shared-document", view);
        assertEquals(Boolean.FALSE, model.getAttribute("accessRequired"));
        verify(sharedLinkService).recordAccess("token", "VIEW", "127.0.0.1", null);
    }

    @Test
    void revokeEndpointReturnsWithoutRedirecting() {
        SharedLinkService sharedLinkService = mock(SharedLinkService.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("owner@example.com");
        ShareController controller = new ShareController(sharedLinkService, mock(DocumentService.class),
                new DocumentPreviewService(), "");

        var response = controller.revokeShareLink("token", authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(sharedLinkService).revoke("token", "owner@example.com");
    }

    @Test
    void protectedSharePageRetainsExpiryWhileWaitingForAccessCode() {
        SharedLinkService sharedLinkService = mock(SharedLinkService.class);
        DocumentService documentService = mock(DocumentService.class);
        SharedLink link = new SharedLink("token", true,
                java.time.LocalDateTime.now().plusHours(1), new Document());
        link.setAccessMode("EMAIL_OTP");
        when(sharedLinkService.findActive("token")).thenReturn(link);
        when(sharedLinkService.sendAccessCode("token")).thenReturn(true);
        HttpSession session = mock(HttpSession.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        ExtendedModelMap model = new ExtendedModelMap();
        ShareController controller = new ShareController(sharedLinkService, documentService,
                new DocumentPreviewService(), "");

        String view = controller.viewSharedDocument("token", model, response, session, request);

        assertEquals("shared-document", view);
        assertEquals(Boolean.TRUE, model.getAttribute("accessRequired"));
        assertEquals(Boolean.TRUE, model.getAttribute("codeSent"));
        org.junit.jupiter.api.Assertions.assertNotNull(model.getAttribute("expiresAtEpochMillis"));
    }

    @Test
    void doesNotClaimCodeWasSentWhenSendIsRateLimited() {
        SharedLinkService sharedLinkService = mock(SharedLinkService.class);
        SharedLink link = new SharedLink("token", true,
                java.time.LocalDateTime.now().plusHours(1), new Document());
        link.setAccessMode("EMAIL_OTP");
        when(sharedLinkService.findActive("token")).thenReturn(link);
        when(sharedLinkService.sendAccessCode("token")).thenReturn(false);
        ExtendedModelMap model = new ExtendedModelMap();
        ShareController controller = new ShareController(sharedLinkService, mock(DocumentService.class),
                new DocumentPreviewService(), "");

        controller.viewSharedDocument("token", model, mock(HttpServletResponse.class),
                mock(HttpSession.class), mock(HttpServletRequest.class));

        assertEquals(Boolean.FALSE, model.getAttribute("codeSent"));
        assertEquals(Boolean.TRUE, model.getAttribute("codeAlreadySent"));
    }

    @Test
    void explainsEmailDeliveryFailureWithoutClaimingCodeWasSent() {
        SharedLinkService sharedLinkService = mock(SharedLinkService.class);
        SharedLink link = new SharedLink("token", true,
                java.time.LocalDateTime.now().plusHours(1), new Document());
        link.setAccessMode("EMAIL_OTP");
        when(sharedLinkService.findActive("token")).thenReturn(link);
        when(sharedLinkService.sendAccessCode("token")).thenThrow(new MailSendException("SMTP unavailable"));
        ExtendedModelMap model = new ExtendedModelMap();
        ShareController controller = new ShareController(sharedLinkService, mock(DocumentService.class),
                new DocumentPreviewService(), "");

        controller.viewSharedDocument("token", model, mock(HttpServletResponse.class),
                mock(HttpSession.class), mock(HttpServletRequest.class));

        assertEquals("No code was sent because email delivery is unavailable. Ask the document owner to configure SMTP, then reopen this link.",
                model.getAttribute("error"));
        org.junit.jupiter.api.Assertions.assertNull(model.getAttribute("codeSent"));
    }

    @Test
    void downloadDisabledRejectsFilesThatCannotBeSafelyPreviewed() throws Exception {
        SharedLinkService sharedLinkService = mock(SharedLinkService.class);
        DocumentService documentService = mock(DocumentService.class);
        Document document = new Document();
        document.setFileName("active-content.html");
        document.setFileType("text/html");
        SharedLink link = new SharedLink("token", false,
                java.time.LocalDateTime.now().plusHours(1), document);
        when(sharedLinkService.findActiveForCurrentView("token")).thenReturn(link);
        when(documentService.download(document)).thenReturn("<script>alert(1)</script>".getBytes());
        ShareController controller = new ShareController(sharedLinkService, documentService,
                new DocumentPreviewService(), "");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("SHARE_VIEW_ALLOWED_token")).thenReturn(true);

        var response = controller.viewInline("token", 0, request);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        verify(sharedLinkService, never()).recordAccess(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownAllowedFileTypesAreServedOnlyAsAttachmentWithNoSniff() throws Exception {
        SharedLinkService sharedLinkService = mock(SharedLinkService.class);
        DocumentService documentService = mock(DocumentService.class);
        Document document = new Document();
        document.setFileName("document.txt");
        document.setFileType("text/plain");
        SharedLink link = new SharedLink("token", true,
                java.time.LocalDateTime.now().plusHours(1), document);
        when(sharedLinkService.findActiveForCurrentView("token")).thenReturn(link);
        when(documentService.download(document)).thenReturn("safe text".getBytes());
        ShareController controller = new ShareController(sharedLinkService, documentService,
                new DocumentPreviewService(), "");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("SHARE_VIEW_ALLOWED_token")).thenReturn(true);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn("test browser");

        var response = controller.viewInline("token", 0, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
        assertEquals("attachment", response.getHeaders().getContentDisposition().getType());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        verify(sharedLinkService).recordAccess("token", "DOWNLOAD", "127.0.0.1", "test browser");
    }

}
