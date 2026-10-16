package com.processpuzzle.document.usecase;

import com.processpuzzle.document.usecase.service.DocumentPlatformFacts;
import com.processpuzzle.shared.event.PlatformEvent;

import java.util.List;

/**
 * Builds a {@link DocumentPlatformFacts} for a test, either recording what it publishes or
 * discarding it — so a test about something else does not have to care about platform facts.
 */
public final class TestFacts {

    private TestFacts() {
    }

    public static DocumentPlatformFacts ignored() {
        return new DocumentPlatformFacts(event -> { });
    }

    public static DocumentPlatformFacts recordingInto(List<PlatformEvent> published) {
        return new DocumentPlatformFacts(event -> published.add((PlatformEvent) event));
    }
}
