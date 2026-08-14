package com.processpuzzle.widget.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The version/publish counters. Worth pinning down because status is <em>derived</em> from them
 * rather than stored, so an off-by-one here reports "unpublished edits" on a freshly published
 * widget — the exact failure the entity's javadoc explains {@code @Version} would have caused.
 */
class WidgetDefinitionTest {

    private static final String ORG_KEY = "acme";
    private static final String KEY = "cards-grid";

    @Test
    void isDraftWhenNeverPublished() {
        WidgetDefinition definition = new WidgetDefinition(ORG_KEY, KEY, "Cards grid");

        assertThat(definition.getVersion()).isEqualTo(1L);
        assertThat(definition.getPublishedVersion()).isNull();
        assertThat(definition.status()).isEqualTo(WidgetDefinitionStatus.DRAFT);
    }

    @Test
    void isPublishedImmediatelyAfterPublish() {
        WidgetDefinition definition = new WidgetDefinition(ORG_KEY, KEY, "Cards grid");

        definition.publish();

        assertThat(definition.status()).isEqualTo(WidgetDefinitionStatus.PUBLISHED);
        assertThat(definition.getPublishedVersion()).isEqualTo(definition.getVersion());
    }

    /** Publishing must not bump the counter, or the definition would report as DRAFT the instant it committed. */
    @Test
    void publishLeavesTheVersionCounterAlone() {
        WidgetDefinition definition = new WidgetDefinition(ORG_KEY, KEY, "Cards grid");
        long before = definition.getVersion();

        definition.publish();

        assertThat(definition.getVersion()).isEqualTo(before);
    }

    @Test
    void fallsBackToDraftAfterAnEditOnTopOfAPublish() {
        WidgetDefinition definition = new WidgetDefinition(ORG_KEY, KEY, "Cards grid");
        definition.publish();

        definition.markEdited();

        assertThat(definition.status()).isEqualTo(WidgetDefinitionStatus.DRAFT);
        assertThat(definition.getVersion()).isEqualTo(2L);
        assertThat(definition.getPublishedVersion()).isEqualTo(1L);
    }

    @Test
    void republishingCatchesUpToTheCurrentVersion() {
        WidgetDefinition definition = new WidgetDefinition(ORG_KEY, KEY, "Cards grid");
        definition.publish();
        definition.markEdited();

        definition.publish();

        assertThat(definition.status()).isEqualTo(WidgetDefinitionStatus.PUBLISHED);
        assertThat(definition.getPublishedVersion()).isEqualTo(2L);
    }
}
