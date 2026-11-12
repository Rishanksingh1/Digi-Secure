package com.rishank.securedigitallocker.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class SharedLinkTests {

    @Test
    void limitedDownloadLinkCountsDownloadsButNotAutomaticPreviews() {
        SharedLink link = new SharedLink("token", true, LocalDateTime.now().plusHours(1), new Document());
        link.setMaxViews(1);
        link.setViewCount(1);

        assertFalse(link.isUseLimitReached());

        link.setDownloadCount(1);
        assertTrue(link.isUseLimitReached());
    }

    @Test
    void noDownloadLinkUsesPreviewCountAsItsLimit() {
        SharedLink link = new SharedLink("token", false, LocalDateTime.now().plusHours(1), new Document());
        link.setMaxViews(2);
        link.setViewCount(1);

        assertFalse(link.isUseLimitReached());

        link.setViewCount(2);
        assertTrue(link.isUseLimitReached());
    }
}
