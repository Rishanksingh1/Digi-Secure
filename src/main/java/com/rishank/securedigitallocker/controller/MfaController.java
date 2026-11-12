package com.rishank.securedigitallocker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.rishank.securedigitallocker.model.User;
import com.rishank.securedigitallocker.service.MfaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class MfaController {

    private final MfaService mfaService;
    private final AuthController authController;

    public MfaController(MfaService mfaService, AuthController authController) {
        this.mfaService = mfaService;
        this.authController = authController;
    }

    @GetMapping("/mfa/setup")
    public String setupPage(org.springframework.security.core.Authentication authentication, Model model) {
        User user = mfaService.findUser(authentication.getName());
        String secret = mfaService.prepareSetup(user);
        model.addAttribute("qrCode", mfaService.qrCodeDataUri(mfaService.provisioningUri(user, secret)));
        model.addAttribute("secret", secret);
        model.addAttribute("mfaEnabled", user.isMfaEnabled());
        return "mfa-setup";
    }

    @PostMapping("/mfa/setup")
    public String enableMfa(@RequestParam String code,
                            org.springframework.security.core.Authentication authentication,
                            Model model) {
        User user = mfaService.findUser(authentication.getName());
        try {
            mfaService.enable(user, code);
            return "redirect:/dashboard?mfaEnabled";
        } catch (IllegalArgumentException exception) {
            String secret = mfaService.prepareSetup(user);
            model.addAttribute("qrCode", mfaService.qrCodeDataUri(mfaService.provisioningUri(user, secret)));
            model.addAttribute("secret", secret);
            model.addAttribute("error", exception.getMessage());
            return "mfa-setup";
        }
    }

    @GetMapping("/mfa/verify")
    public String verifyPage() {
        return "mfa-verify";
    }

    @PostMapping("/mfa/verify")
    public String verifyCode(@RequestParam String code,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             Model model) {
        Object pending = request.getSession().getAttribute("MFA_PENDING_AUTH");
        if (!(pending instanceof org.springframework.security.core.Authentication authentication)) {
            return "redirect:/login";
        }

        User user = mfaService.findUser(authentication.getName());
        if (!mfaService.verify(user, code)) {
            if (user.getMfaLockedUntil() != null
                    && user.getMfaLockedUntil().isAfter(java.time.LocalDateTime.now())) {
                request.getSession().removeAttribute("MFA_PENDING_AUTH");
                model.addAttribute("error", "Too many failed codes. MFA is locked for 15 minutes.");
            } else {
                model.addAttribute("error", "The verification code is incorrect or expired");
            }
            return "mfa-verify";
        }

        request.getSession().removeAttribute("MFA_PENDING_AUTH");
        authController.completeLogin(authentication, request, response);
        return "redirect:/dashboard";
    }
}
