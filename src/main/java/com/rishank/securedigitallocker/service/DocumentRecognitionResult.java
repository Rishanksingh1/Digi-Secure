package com.rishank.securedigitallocker.service;

import java.util.Map;

public record DocumentRecognitionResult(
        String documentType,
        String category,
        Map<String, String> metadata,
        String notice,
        boolean recognized) {
}
