package com.rishank.securedigitallocker.controller;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.mail.MailException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.rishank.securedigitallocker.model.User;
import com.rishank.securedigitallocker.service.MfaService;
import com.rishank.securedigitallocker.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final MfaService mfaService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(UserService userService,
                          AuthenticationManager authenticationManager,
                          MfaService mfaService) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.mfaService = mfaService;
    }

    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }

    @PostMapping("/signup")
    public String registerUser(@RequestParam String name,
                                @RequestParam String email,
                                @RequestParam String password,
                                Model model) {
        try {
            userService.registerUser(name, email, password);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "signup";
        } catch (MailException exception) {
            model.addAttribute("error", "Could not send verification email. Check the email server configuration and try again.");
            return "signup";
        }
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token, Model model) {
        try {
            userService.verifyEmail(token);
            return "redirect:/login?verified";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return "signup";
        }
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Model model) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            User user = mfaService.findUser(username);
            if (!user.isEmailVerified()) {
                model.addAttribute("error", "Verify your email address before signing in");
                return "login";
            }
            if (user.isMfaEnabled()
                    && user.getMfaLockedUntil() != null
                    && user.getMfaLockedUntil().isAfter(java.time.LocalDateTime.now())) {
                model.addAttribute("error", "MFA is temporarily locked after repeated failed codes");
                return "login";
            }
            if (user.isMfaEnabled()) {
                request.getSession(true).setAttribute("MFA_PENDING_AUTH", authentication);
                return "redirect:/mfa/verify";
            }
            completeLogin(authentication, request, response);
            return "redirect:/dashboard";
        } catch (BadCredentialsException | IllegalArgumentException exception) {
            model.addAttribute("error", "Invalid email or password");
            return "login";
        }
    }

    public void completeLogin(Authentication authentication,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        if (request.getSession(false) == null) {
            request.getSession(true);
        }
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        if (response != null) {
            securityContextRepository.saveContext(context, request, response);
        }
    }
}