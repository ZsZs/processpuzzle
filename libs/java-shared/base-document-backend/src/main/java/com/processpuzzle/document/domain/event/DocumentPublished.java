package com.processpuzzle.document.domain.event;

import java.time.Instant;

/**
 * One locale of a document became publicly visible.
 *
 * <p>Published as a Spring application event so an approval or notification step can attach later
 * without base-document knowing it exists — the integration direction the platform uses everywhere
 * else. base-workflow is the expected first subscriber: an editorial approval flow reacts to this
 * rather than base-document calling into it.
 *
 * @param revision the draft revision that became the published snapshot
 */
public record DocumentPublished(
        String orgKey,
        String documentId,
        String slug,
        String locale,
        Long revision,
        Instant publishedAt,
        String publishedBy) {
}
