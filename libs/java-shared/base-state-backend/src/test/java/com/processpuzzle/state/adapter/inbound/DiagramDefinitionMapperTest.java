package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.state.domain.DiagramViewport;
import com.processpuzzle.state.domain.EdgeLayout;
import com.processpuzzle.state.domain.NodeLayout;
import com.processpuzzle.state.domain.NodeSize;
import com.processpuzzle.state.domain.Point;
import com.processpuzzle.state.model.DiagramDefinitionInput;
import com.processpuzzle.state.model.PageOfDiagramDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DiagramDefinitionMapperTest {

    private static final String ORG = "org-1";
    private static final String ENTITY = "invoice";

    private final DiagramDefinitionMapper mapper = new DiagramDefinitionMapper();

    private static com.processpuzzle.state.model.NodeLayout modelNode() {
        return new com.processpuzzle.state.model.NodeLayout(
                "draft", new com.processpuzzle.state.model.Point(12.5, 34.0))
                .size(new com.processpuzzle.state.model.NodeSize(150.0, 60.0));
    }

    private static com.processpuzzle.state.model.EdgeLayout modelEdge() {
        return new com.processpuzzle.state.model.EdgeLayout("submit")
                .points(List.of(new com.processpuzzle.state.model.Point(1.0, 2.0)))
                .sourcePort("port-right")
                .targetPort("port-left")
                .routing("orthogonal");
    }

    @Test
    void toDomain_shouldMapEveryLayoutField() {
        DiagramDefinitionInput input = new DiagramDefinitionInput(ENTITY)
                .nodes(List.of(modelNode()))
                .edges(List.of(modelEdge()))
                .viewport(new com.processpuzzle.state.model.DiagramViewport(-40.0, 12.0, 1.5));

        com.processpuzzle.state.domain.DiagramDefinition domain = mapper.toDomain(ORG, ENTITY, input);

        assertThat(domain.getOrgKey()).isEqualTo(ORG);
        assertThat(domain.getEntityName()).isEqualTo(ENTITY);
        assertThat(domain.getNodes())
                .containsExactly(new NodeLayout("draft", new Point(12.5, 34), new NodeSize(150, 60)));
        assertThat(domain.getEdges()).containsExactly(new EdgeLayout(
                "submit", List.of(new Point(1, 2)), "port-right", "port-left", "orthogonal"));
        assertThat(domain.getViewport()).isEqualTo(new DiagramViewport(-40, 12, 1.5));
    }

    /**
     * The path wins. A body naming a different entityName cannot retarget the layout, which is why
     * {@link DiagramDefinitionInput#getEntityName()} is never read.
     */
    @Test
    void toDomain_shouldTakeTheEntityNameFromThePathNotTheBody() {
        DiagramDefinitionInput input = new DiagramDefinitionInput("some-other-entity");

        com.processpuzzle.state.domain.DiagramDefinition domain = mapper.toDomain(ORG, ENTITY, input);

        assertThat(domain.getEntityName()).isEqualTo(ENTITY);
    }

    @Test
    void toDomain_shouldTolerateAnEmptyInput() {
        com.processpuzzle.state.domain.DiagramDefinition domain =
                mapper.toDomain(ORG, ENTITY, new DiagramDefinitionInput(ENTITY));

        assertThat(domain.getNodes()).isEmpty();
        assertThat(domain.getEdges()).isEmpty();
        assertThat(domain.getViewport()).isNull();
    }

    @Test
    void toDomain_shouldMapANodeWithNoSizeAndAnEdgeWithNoWaypoints() {
        DiagramDefinitionInput input = new DiagramDefinitionInput(ENTITY)
                .nodes(List.of(new com.processpuzzle.state.model.NodeLayout(
                        "draft", new com.processpuzzle.state.model.Point(0.0, 0.0))))
                .edges(List.of(new com.processpuzzle.state.model.EdgeLayout("submit")));

        com.processpuzzle.state.domain.DiagramDefinition domain = mapper.toDomain(ORG, ENTITY, input);

        assertThat(domain.getNodes().get(0).size()).isNull();
        assertThat(domain.getEdges().get(0).points()).isEmpty();
    }

    @Test
    void toModel_shouldMapEveryLayoutFieldBack() {
        com.processpuzzle.state.domain.DiagramDefinition domain =
                com.processpuzzle.state.domain.DiagramDefinition.builder()
                        .orgKey(ORG)
                        .entityName(ENTITY)
                        .nodes(List.of(new NodeLayout("draft", new Point(12.5, 34), new NodeSize(150, 60))))
                        .edges(List.of(new EdgeLayout(
                                "submit", List.of(new Point(1, 2)), "port-right", "port-left", "orthogonal")))
                        .viewport(new DiagramViewport(-40, 12, 1.5))
                        .build();

        com.processpuzzle.state.model.DiagramDefinition model = mapper.toModel(domain);

        assertThat(model.getEntityName()).isEqualTo(ENTITY);
        assertThat(model.getOrgKey()).isEqualTo(ORG);
        assertThat(model.getNodes()).containsExactly(modelNode());
        assertThat(model.getEdges()).containsExactly(modelEdge());
        assertThat(model.getViewport())
                .isEqualTo(new com.processpuzzle.state.model.DiagramViewport(-40.0, 12.0, 1.5));
    }

    /** An unarranged layout must map without a viewport and without null-guard fuss downstream. */
    @Test
    void toModel_shouldMapAnUnarrangedLayout() {
        com.processpuzzle.state.model.DiagramDefinition model = mapper.toModel(
                com.processpuzzle.state.domain.DiagramDefinition.builder()
                        .orgKey(ORG).entityName(ENTITY).build());

        assertThat(model.getNodes()).isEmpty();
        assertThat(model.getEdges()).isEmpty();
        assertThat(model.getViewport()).isNull();
        assertThat(model.getCreatedAt()).isNull();
        assertThat(model.getUpdatedAt()).isNull();
        assertThat(model.getVersion()).isNull();
    }

    @Test
    void toModel_shouldMapThePage() {
        com.processpuzzle.state.domain.DiagramDefinition domain =
                com.processpuzzle.state.domain.DiagramDefinition.builder()
                        .orgKey(ORG).entityName(ENTITY).build();

        PageOfDiagramDefinition page = mapper.toModel(
                new PageImpl<>(List.of(domain), PageRequest.of(1, 5), 11));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(11);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(5);
    }
}
