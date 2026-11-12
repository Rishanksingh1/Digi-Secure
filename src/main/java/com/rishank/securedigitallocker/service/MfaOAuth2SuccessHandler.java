package com.rishank.securedigitallocker.service;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import com.rishank.securedigitallocker.model.User;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class MfaOAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final MfaService mfaService;

    public MfaOAuth2SuccessHandler(MfaService mfaService) {
        this.mfaService = mfaService;
        setDefaultTargetUrl("/dashboard");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof OidcUser) {
            User user = mfaService.findUser(authentication.getName());
            if (user.isMfaEnabled()) {
                HttpSession session = request.getSession(true);
                session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                session.setAttribute("MFA_PENDING_AUTH", authentication);
                SecurityContextHolder.clearContext();
                getRedirectStrategy().sendRedirect(request, response, "/mfa/verify");
                return;
            }
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
