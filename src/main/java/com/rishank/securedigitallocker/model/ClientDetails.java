package com.rishank.securedigitallocker.model;

import java.util.Locale;

public final class ClientDetails {

    private final String browser;
    private final String platform;

    private ClientDetails(String browser, String platform) {
        this.browser = browser;
        this.platform = platform;
    }

    public static ClientDetails fromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ClientDetails("Unknown", "Unknown");
        }

        String normalized = userAgent.toLowerCase(Locale.ROOT);
        return new ClientDetails(detectBrowser(normalized), detectPlatform(normalized));
    }

    public String getBrowser() {
        return browser;
    }

    public String getPlatform() {
        return platform;
    }

    private static String detectBrowser(String userAgent) {
        if (userAgent.contains("edg/") || userAgent.contains("edga/") || userAgent.contains("edgios/")) {
            return "Microsoft Edge";
        }
        if (userAgent.contains("opr/") || userAgent.contains("opera")) {
            return "Opera";
        }
        if (userAgent.contains("samsungbrowser/")) {
            return "Samsung Internet";
        }
        if (userAgent.contains("crios/") || userAgent.contains("chrome/")) {
            return "Google Chrome";
        }
        if (userAgent.contains("fxios/") || userAgent.contains("firefox/")) {
            return "Firefox";
        }
        if (userAgent.contains("safari/") && userAgent.contains("version/")) {
            return "Safari";
        }
        return "Unknown";
    }

    private static String detectPlatform(String userAgent) {
        if (userAgent.contains("windows nt 10.0")) {
            return "Windows 10/11";
        }
        if (userAgent.contains("windows nt 6.3")) {
            return "Windows 8.1";
        }
        if (userAgent.contains("windows nt 6.2")) {
            return "Windows 8";
        }
        if (userAgent.contains("windows nt 6.1")) {
            return "Windows 7";
        }
        if (userAgent.contains("windows")) {
            return "Windows";
        }
        if (userAgent.contains("ipad")) {
            return "iPadOS";
        }
        if (userAgent.contains("iphone") || userAgent.contains("ipod")
                || userAgent.contains("cpu iphone os") || userAgent.contains("cpu os")) {
            return "iOS";
        }
        if (userAgent.contains("android")) {
            return "Android";
        }
        if (userAgent.contains("cros")) {
            return "ChromeOS";
        }
        if (userAgent.contains("mac os x") || userAgent.contains("macintosh")) {
            return "macOS";
        }
        if (userAgent.contains("linux")) {
            return "Linux";
        }
        return "Unknown";
    }
}
