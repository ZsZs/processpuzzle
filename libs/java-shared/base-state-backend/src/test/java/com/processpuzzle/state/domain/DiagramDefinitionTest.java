package com.processpuzzle.state.domain;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagramDefinitionTest {

    private static final String ORG = "org-1";
    private static final String ENTITY = "invoice";

    private static NodeLayout node(String stateKey) {
        return new NodeLayout(stateKey, new Point(10, 20), new NodeSize(150, 60));
    }

    private static EdgeLayout edge(String transitionKey) {
        return new EdgeLayout(transitionKey, List.of(new Point(1, 2)), "port-right", "port-left", "orthogonal");
    }

    @Test
    void builder_shouldPopulateIdentityAndLayout() {
        DiagramDefinition definition = DiagramDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .nodes(List.of(node("draft")))
                .edges(List.of(edge("submit")))
                .viewport(new DiagramViewport(-40, 12, 1.5))
                .build();

        assertThat(definition.getOrgKey()).isEqualTo(ORG);
        assertThat(definition.getEntityName()).isEqualTo(ENTITY);
        assertThat(definition.getNodes()).containsExactly(node("draft"));
        assertThat(definition.getEdges()).containsExactly(edge("submit"));
        assertThat(definition.getViewport()).isEqualTo(new DiagramViewport(-40, 12, 1.5));
        assertThat(definition.getVersion()).isNull();
    }

    @Test
    void builder_shouldTreatNullCollectionsAsEmptyAndNullViewportAsAbsent() {
        DiagramDefinition definition = DiagramDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .nodes(null)
                .edges(null)
                .viewport(null)
                .build();

        assertThat(definition.getNodes()).isEmpty();
        assertThat(definition.getEdges()).isEmpty();
        assertThat(definition.getViewport()).isNull();
    }

    /** A machine that has been opened but never arranged is the default, so it must be buildable. */
    @Test
    void builder_shouldBuildAnUnarrangedLayout() {
        DiagramDefinition definition = DiagramDefinition.builder().orgKey(ORG).entityName(ENTITY).build();

        assertThat(definition.getNodes()).isEmpty();
        assertThat(definition.getEdges()).isEmpty();
        assertThat(definition.getViewport()).isNull();
    }

    @Test
    void getters_shouldReturnDefensiveCopies() {
        DiagramDefinition definition = DiagramDefinition.builder()
                .orgKey(ORG).entityName(ENTITY).nodes(List.of(node("draft"))).build();

        assertThatThrownBy(() -> definition.getNodes().add(node("other")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> definition.getEdges().add(edge("other")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** Mutating the caller's list after building must not reach inside the entity. */
    @Test
    void builder_shouldCopyTheCallersLists() {
        List<NodeLayout> nodes = new ArrayList<>(List.of(node("draft")));
        DiagramDefinition definition = DiagramDefinition.builder()
                .orgKey(ORG).entityName(ENTITY).nodes(nodes).build();

        nodes.add(node("approved"));

        assertThat(definition.getNodes()).hasSize(1);
    }

    @Test
    void replaceLayout_shouldReplaceEverythingWholesale() {
        DiagramDefinition definition = DiagramDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .nodes(List.of(node("draft")))
                .edges(List.of(edge("submit")))
                .viewport(new DiagramViewport(1, 2, 3))
                .build();

        definition.replaceLayout(List.of(node("approved")), List.of(), new DiagramViewport(0, 0, 1));

        assertThat(definition.getNodes()).containsExactly(node("approved"));
        assertThat(definition.getEdges()).isEmpty();
        assertThat(definition.getViewport()).isEqualTo(new DiagramViewport(0, 0, 1));
    }

    @Test
    void replaceLayout_shouldClearTheViewportWhenGivenNone() {
        DiagramDefinition definition = DiagramDefinition.builder()
                .orgKey(ORG).entityName(ENTITY).viewport(new DiagramViewport(1, 2, 3)).build();

        definition.replaceLayout(null, null, null);

        assertThat(definition.getViewport()).isNull();
        assertThat(definition.getNodes()).isEmpty();
        assertThat(definition.getEdges()).isEmpty();
    }

    @Test
    void onCreate_shouldStampBothTimestamps() {
        DiagramDefinition definition = DiagramDefinition.builder().orgKey(ORG).entityName(ENTITY).build();

        definition.onCreate();

        assertThat(definition.getCreatedAt()).isNotNull();
        assertThat(definition.getUpdatedAt()).isEqualTo(definition.getCreatedAt());
    }

    @Test
    void onUpdate_shouldMoveUpdatedAtOnly() {
        DiagramDefinition definition = DiagramDefinition.builder().orgKey(ORG).entityName(ENTITY).build();
        definition.onCreate();
        var created = definition.getCreatedAt();

        definition.onUpdate();

        assertThat(definition.getCreatedAt()).isEqualTo(created);
        assertThat(definition.getUpdatedAt()).isAfterOrEqualTo(created);
    }

    /**
     * The stamped instants have to survive the trip out to the contract as UTC offsets. Asserted
     * from here rather than from {@code DiagramDefinitionMapperTest} because {@link #onCreate()}
     * is a JPA callback and stays package-private — only a test in this package can stamp an
     * entity the way Hibernate does.
     */
    @Test
    void stampedTimestamps_shouldMapToUtcOffsets() {
        DiagramDefinition definition = DiagramDefinition.builder().orgKey(ORG).entityName(ENTITY).build();
        definition.onCreate();

        var model = new com.processpuzzle.state.adapter.inbound.DiagramDefinitionMapper().toModel(definition);

        assertThat(model.getCreatedAt()).isNotNull();
        assertThat(model.getCreatedAt().getOffset()).isEqualTo(java.time.ZoneOffset.UTC);
        assertThat(model.getCreatedAt().toInstant()).isEqualTo(definition.getCreatedAt());
        assertThat(model.getUpdatedAt().toInstant()).isEqualTo(definition.getUpdatedAt());
    }

    /** The JPA constructor: Hibernate instantiates through it before populating any field. */
    @Test
    void noArgConstructor_shouldStartWithAnEmptyLayout() {
        DiagramDefinition definition = new DiagramDefinition();

        assertThat(definition.getNodes()).isEmpty();
        assertThat(definition.getEdges()).isEmpty();
        assertThat(definition.getViewport()).isNull();
    }
}
