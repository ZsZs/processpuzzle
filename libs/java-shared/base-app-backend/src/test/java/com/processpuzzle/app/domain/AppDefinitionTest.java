package com.processpuzzle.app.domain;

import com.processpuzzle.app.domain.RouteTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The draft/published pair, read directly off the entity. {@code PublishAppDefinitionTest} covers the
 * use cases that drive it; what is left here is the entity's own invariants — never holding a
 * {@code null} draft, and distinguishing "unpublished edits" from "never published at all".
 */
class AppDefinitionTest {

    @Test
    void aDefinitionCreatedWithoutAGraphStillHasADraftToEdit() {
        AppDefinition definition =
                new AppDefinition("my-org", "claims-app", "Claims Management", null, null, null);

        assertThat(definition.getDraftGraph()).isEqualTo(AppGraph.empty());
        assertThat(definition.getPublishedGraph()).isNull();
        assertThat(definition.getRevision()).isEqualTo(1L);
        assertThat(definition.graphFor(true)).isEqualTo(AppGraph.empty());
        assertThat(definition.graphFor(false)).isNull();
    }

    @Test
    void replacingTheDraftWithNoGraphEmptiesItRatherThanNullingIt() {
        AppDefinition definition = new AppDefinition("my-org", "claims-app", "Claims Management", null, null,
                graphWithOnePage());

        definition.replaceDraft("Renamed", "renamed.app.name", "New description.", null);

        assertThat(definition.getDraftGraph()).isEqualTo(AppGraph.empty());
        assertThat(definition.getName()).isEqualTo("Renamed");
        assertThat(definition.getTranslocoId()).isEqualTo("renamed.app.name");
        assertThat(definition.getDescription()).isEqualTo("New description.");
        assertThat(definition.getRevision()).isEqualTo(2L);
    }

    /**
     * The two published-state questions answer differently for a definition edited after publishing —
     * which is exactly the state that lets end users keep seeing the previous revision.
     */
    @Test
    void aDefinitionEditedAfterPublishingHasAPublishedRevisionWithoutBeingPublished() {
        AppDefinition definition = new AppDefinition("my-org", "claims-app", "Claims Management", null, null,
                graphWithOnePage());
        definition.publish();

        assertThat(definition.isPublished()).isTrue();
        assertThat(definition.hasPublishedRevision()).isTrue();

        definition.replaceDraft("Claims Management", null, null, AppGraph.empty());

        assertThat(definition.isPublished()).isFalse();
        assertThat(definition.hasPublishedRevision()).isTrue();
        assertThat(definition.graphFor(false).routes()).hasSize(1);
        assertThat(definition.graphFor(true).routes()).isEmpty();
    }

    @Test
    void headerFieldsAreNotSnapshottedSoRenamingIsVisibleImmediately() {
        AppDefinition definition = new AppDefinition("my-org", "claims-app", "Claims Management", null,
                "Handles claims.", graphWithOnePage());
        definition.publish();

        definition.replaceDraft("Claims Management v2", null, null, graphWithOnePage());

        assertThat(definition.getName()).isEqualTo("Claims Management v2");
        assertThat(definition.getDescription()).isNull();
    }

    @Test
    void bothTimestampsAreStampedOnInsertAndOnlyOneIsMovedOnUpdate() {
        AppDefinition definition =
                new AppDefinition("my-org", "claims-app", "Claims Management", null, null, null);

        definition.onCreate();

        assertThat(definition.getCreatedAt()).isNotNull().isEqualTo(definition.getUpdatedAt());

        java.time.Instant created = definition.getCreatedAt();
        definition.onUpdate();

        assertThat(definition.getCreatedAt()).isEqualTo(created);
        assertThat(definition.getUpdatedAt()).isAfterOrEqualTo(created);
    }

    private static AppGraph graphWithOnePage() {
        return new AppGraph(null, null, List.of(),
                List.of(new AppRoute("claims-list", "Claims", null, null, List.of(), RouteTarget.ofWidgets(List.of()))), List.of());
    }
}
