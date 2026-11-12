package com.rishank.securedigitallocker.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ClientDetailsTests {

    @Test
    void identifiesEdgeAndWindowsFromTypicalEdgeUserAgent() {
        ClientDetails details = ClientDetails.fromUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0");

        assertEquals("Microsoft Edge", details.getBrowser());
        assertEquals("Windows 10/11", details.getPlatform());
    }

    @Test
    void identifiesSafariAndIosWithoutClaimingAnExactOsVersion() {
        ClientDetails details = ClientDetails.fromUserAgent(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
                        + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");

        assertEquals("Safari", details.getBrowser());
        assertEquals("iOS", details.getPlatform());
    }

    @Test
    void reportsUnknownForMissingOrUnrecognizedUserAgent() {
        ClientDetails missing = ClientDetails.fromUserAgent(null);
        ClientDetails unrecognized = ClientDetails.fromUserAgent("an unfamiliar client");

        assertEquals("Unknown", missing.getBrowser());
        assertEquals("Unknown", missing.getPlatform());
        assertEquals("Unknown", unrecognized.getBrowser());
        assertEquals("Unknown", unrecognized.getPlatform());
    }
}
