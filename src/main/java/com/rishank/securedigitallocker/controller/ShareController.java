package com.rishank.securedigitallocker.controller;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.ZoneId;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.rishank.securedigitallocker.model.SharedLink;
import com.rishank.securedigitallocker.service.DocumentService;
import com.rishank.securedigitallocker.service.DocumentPreviewService;
import com.rishank.securedigitallocker.service.SharedLinkService;

@Controller
public class ShareController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShareController.class);

    private final SharedLinkService sharedLinkService;
    private final DocumentService documentService;
    private final DocumentPreviewService previewService;
    private final String publicBaseUrl;

    public ShareController(SharedLinkService sharedLinkService,
                           DocumentService documentService,
                           DocumentPreviewService previewService,
                           @Value("${app.public-base-url:}") String publicBaseUrl) {
        this.sharedLinkService = sharedLinkService;
        this.documentService = documentService;
        this.previewService = previewService;
        this.publicBaseUrl = publicBaseUrl.trim().replaceAll("/+$", "");
    }

    @PostMapping("/documents/{id}/share")
    public String createShareLink(@PathVariable Long id,
                                  @RequestParam(defaultValue = "24") int expiryHours,
                                  @RequestParam(defaultValue = "false") boolean allowDownload,
                                  @RequestParam(required = false) Integer maxUses,
                                  @RequestParam(defaultValue = "NONE") String accessMode,
                                  @RequestParam(required = false) String pin,
                                  @RequestParam(required = false) String recipientEmail,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes,
                                  HttpServletRequest request) {
        try {
            SharedLink link = sharedLinkService.createWithOptions(authentication.getName(), id, allowDownload,
                    expiryHours * 60, maxUses, accessMode, pin, recipientEmail);
            String shareUrl = buildShareUrl(request, link.getToken());
            redirectAttributes.addFlashAttribute("shareUrl", shareUrl);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/api/documents/{id}/share")
    public ResponseEntity<Map<String, String>> createShareLinkForSharing(
            @PathVariable Long id,
            @RequestParam(required = false) Integer expiryMinutes,
            @RequestParam(defaultValue = "24") int expiryHours,
            @RequestParam(defaultValue = "false") boolean allowDownload,
            @RequestParam(required = false) Integer maxUses,
            @RequestParam(defaultValue = "NONE") String accessMode,
            @RequestParam(required = false) String pin,
            @RequestParam(required = false) String recipientEmail,
            Authentication authentication,
            HttpServletRequest request) {
        try {
            int effectiveExpiry = expiryMinutes == null ? expiryHours * 60 : expiryMinutes;
            SharedLink link = sharedLinkService.createWithOptions(authentication.getName(), id, allowDownload,
                    effectiveExpiry, maxUses, accessMode, pin, recipientEmail);
            String shareUrl = buildShareUrl(request, link.getToken());
            return ResponseEntity.ok(Map.of("shareUrl", shareUrl));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", exception.getMessage()));
        }
    }

    private String buildShareUrl(HttpServletRequest request, String token) {
        if (!publicBaseUrl.isBlank()) {
            return UriComponentsBuilder.fromUriString(publicBaseUrl)
                    .pathSegment("share", token)
                    .build()
                    .toUriString();
        }
        return ServletUriComponentsBuilder.fromContextPath(request)
                .pathSegment("share", token)
                .build()
                .toUriString();
    }

    @GetMapping("/share/{token}")
    public String viewSharedDocument(
            @PathVariable String token,
            Model model,
            HttpServletResponse response,
            HttpSession session,
            HttpServletRequest request) {
        preventCaching(response);
        model.addAttribute("accessRequired", false);
        model.addAttribute("previewPageCount", 0);
        try {
            SharedLink link = sharedLinkService.findActive(token);
            model.addAttribute("expiresAtEpochMillis", link.getExpiryDate()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli());
            if ("EMAIL_OTP".equals(link.getAccessMode()) && !isAuthorized(link, token, session)) {
                try {
                    boolean codeSent = sharedLinkService.sendAccessCode(token);
                    model.addAttribute("codeSent", codeSent);
                    model.addAttribute("codeAlreadySent", !codeSent);
                } catch (MailException exception) {
                    LOGGER.error("Unable to send access code for shared link {}", link.getId(), exception);
                    model.addAttribute("error",
                            "No code was sent because email delivery is unavailable. Ask the document owner to configure SMTP, then reopen this link.");
                }
            }
            if (!isAuthorized(link, token, session)) {
                model.addAttribute("link", link);
                model.addAttribute("accessRequired", true);
                return "shared-document";
            }
            model.addAttribute("link", link);
            sharedLinkService.recordAccess(token, "VIEW", request.getRemoteAddr(),
                    request.getHeader(HttpHeaders.USER_AGENT));
            session.setAttribute(viewSessionAttribute(token), true);
            if (!link.isAllowDownload() && link.getDocument().getFileType() != null
                    && link.getDocument().getFileType().startsWith("application/pdf")) {
                try {
                    byte[] content = documentService.download(link.getDocument());
                    model.addAttribute("previewPageCount", previewService.getPdfPageCount(content));
                } catch (IOException | GeneralSecurityException exception) {
                    model.addAttribute("previewError",
                            "The PDF preview could not be loaded. Please contact the document owner.");
                }
            }
            return "shared-document";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return "shared-document";
        }
    }

    @GetMapping("/share/{token}/view")
    public ResponseEntity<ByteArrayResource> viewInline(
            @PathVariable String token,
            @RequestParam(defaultValue = "0") int page,
            HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session == null || !Boolean.TRUE.equals(session.getAttribute(viewSessionAttribute(token)))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            SharedLink link = sharedLinkService.findActiveForCurrentView(token);
            if (!isAuthorized(link, token, session)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            byte[] content = documentService.download(link.getDocument());
            byte[] deliveredContent;
            MediaType contentType;
            if (!link.isAllowDownload()) {
                if (!previewService.isPdf(content) && !previewService.isImage(content)) {
                    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
                }
                deliveredContent = previewService.isPdf(content)
                        ? previewService.renderPdfPagePreview(content, page)
                        : previewService.renderPreview(content);
                contentType = MediaType.IMAGE_PNG;
            } else if (previewService.isPdf(content)) {
                deliveredContent = content;
                contentType = MediaType.APPLICATION_PDF;
            } else if (previewService.isImage(content)) {
                deliveredContent = previewService.renderPreview(content);
                contentType = MediaType.IMAGE_PNG;
            } else {
                sharedLinkService.recordAccess(token, "DOWNLOAD", request.getRemoteAddr(),
                        request.getHeader(HttpHeaders.USER_AGENT));
                return fileResponse(content, link);
            }
            ContentDisposition disposition = ContentDisposition.inline()
                    .filename(link.getDocument().getFileName())
                    .build();
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(deliveredContent.length)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(new ByteArrayResource(deliveredContent));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        } catch (IOException | GeneralSecurityException exception) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String viewSessionAttribute(String token) {
        return "SHARE_VIEW_ALLOWED_" + token;
    }

    @GetMapping("/share/{token}/download")
    public ResponseEntity<ByteArrayResource> downloadSharedDocument(
            @PathVariable String token, HttpServletRequest request) {
        try {
            SharedLink link = sharedLinkService.findActive(token);
            if (!link.isAllowDownload()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (!isAuthorized(link, token, request.getSession(false))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            byte[] content = documentService.download(link.getDocument());
            sharedLinkService.recordAccess(token, "DOWNLOAD", request.getRemoteAddr(),
                    request.getHeader(HttpHeaders.USER_AGENT));
            return fileResponse(content, link);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        } catch (IOException | GeneralSecurityException exception) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/share/{token}/unlock")
    public String unlock(@PathVariable String token,
                         @RequestParam String code,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        try {
            if (sharedLinkService.verifyAccess(token, code)) {
                session.setAttribute("SHARE_UNLOCKED_" + token, true);
                return "redirect:/share/" + token;
            }
            redirectAttributes.addFlashAttribute("error", "The code is incorrect, expired, or temporarily locked");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/share/" + token;
    }

    @PostMapping("/share/{token}/revoke")
    public String revoke(@PathVariable String token,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            sharedLinkService.revoke(token, authentication.getName());
            redirectAttributes.addFlashAttribute("success", "Share link revoked");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/api/share/{token}/revoke")
    public ResponseEntity<Void> revokeShareLink(@PathVariable String token, Authentication authentication) {
        try {
            sharedLinkService.revoke(token, authentication.getName());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<ByteArrayResource> fileResponse(byte[] content, SharedLink link) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(link.getDocument().getFileName())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.length)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new ByteArrayResource(content));
    }

    private boolean isAuthorized(SharedLink link, String token, HttpSession session) {
        return "NONE".equals(link.getAccessMode())
                || (session != null && Boolean.TRUE.equals(session.getAttribute("SHARE_UNLOCKED_" + token)));
    }

    private void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }
}