package com.rishank.securedigitallocker.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum DocumentFolder {
    PDFS("pdfs", "PDFs", "PDF"),
    IMAGES("images", "Images", "IMG"),
    OTHER("other", "Other", "FILE");

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("bmp", "gif", "heic", "jpeg", "jpg", "png", "tif", "tiff", "webp");

    private final String key;
    private final String label;
    private final String icon;

    DocumentFolder(String key, String label, String icon) {
        this.key = key;
        this.label = label;
        this.icon = icon;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public static Optional<DocumentFolder> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(folder -> folder.key.equalsIgnoreCase(key))
                .findFirst();
    }

    public static DocumentFolder classify(String fileName, String contentType) {
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedType.equals("application/pdf") || hasExtension(fileName, "pdf")) {
            return PDFS;
        }
        if (normalizedType.startsWith("image/") || hasImageExtension(fileName)) {
            return IMAGES;
        }
        return OTHER;
    }

    private static boolean hasImageExtension(String fileName) {
        if (fileName == null) {
            return false;
        }
        int extensionStart = fileName.lastIndexOf('.');
        return extensionStart >= 0
                && IMAGE_EXTENSIONS.contains(fileName.substring(extensionStart + 1).toLowerCase(Locale.ROOT));
    }

    private static boolean hasExtension(String fileName, String extension) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith("." + extension);
    }
}
