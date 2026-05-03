package com.kuhen.cryptopro.preprocess;

import com.kuhen.cryptopro.config.PreprocessingProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionRegimeDetectorTest {

    private final SessionRegimeDetector detector = new SessionRegimeDetector(new PreprocessingProperties());

    @Test
    void identifiesOverlapWindowInUtc() {
        SessionType sessionType = detector.detectSessionType(Instant.parse("2026-04-20T13:30:00Z"));
        assertEquals(SessionType.OVERLAP, sessionType);
    }
}

