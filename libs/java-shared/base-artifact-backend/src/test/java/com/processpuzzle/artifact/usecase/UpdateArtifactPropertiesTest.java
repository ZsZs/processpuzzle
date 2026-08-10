package com.processpuzzle.artifact.usecase;

import com.processpuzzle.artifact.adapter.inbound.ArtifactMapper;
import com.processpuzzle.artifact.domain.Artifact;
import com.processpuzzle.artifact.domain.ArtifactBlock;
import com.processpuzzle.artifact.domain.ArtifactGraph;
import com.processpuzzle.artifact.domain.ArtifactInputPort;
import com.processpuzzle.artifact.domain.ArtifactRepository;
import com.processpuzzle.artifact.domain.BlockKind;
import com.processpuzzle.artifact.domain.PortType;
import com.processpuzzle.artifact.domain.WidgetPlacement;
import com.processpuzzle.artifact.model.ArtifactPropertiesInput;
import com.processpuzzle.artifact.usecase.exception.ArtifactNotFoundException;
import com.processpuzzle.artifact.usecase.service.ArtifactReferentialIntegrityChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateArtifactPropertiesTest {

    private ArtifactRepository repository;
    private UpdateArtifactProperties updateArtifactProperties;

    @BeforeEach
    void setUp() {
        repository = mock(ArtifactRepository.class);
        updateArtifactProperties = new UpdateArtifactProperties(
                repository, new ArtifactReferentialIntegrityChecker(), new ArtifactMapper());
    }

    @Test
    void carriesTheStoredBlocksOverUntouched() {
        // The whole reason this use case exists: ArtifactPropertiesInput has no blocks field, so a
        // Properties save cannot discard content maintained through the block-level endpoints.
        Artifact existing = existing(List.of(standaloneWidget("chart-1", Map.of())));
        when(repository.findByOrgKeyAndId("demo", "q3-plan")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Artifact saved = updateArtifactProperties.execute("demo", "q3-plan", input("Renamed"));

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getTitle()).isEqualTo("Renamed");
        assertThat(saved.getGraph().blocks()).extracting(ArtifactBlock::id).containsExactly("chart-1");
    }

    @Test
    void replacesThePortsRatherThanMergingThem() {
        Artifact existing = existing(List.of());
        when(repository.findByOrgKeyAndId("demo", "q3-plan")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Artifact saved = updateArtifactProperties.execute("demo", "q3-plan", input("Renamed"));

        assertThat(saved.getGraph().inputPorts()).extracting(ArtifactInputPort::name).containsExactly("customer");
    }

    @Test
    void rejectsDeletingAPortThatAnUntouchedBlockStillBindsTo() {
        // Ports are the one thing this endpoint does change, and changing them can invalidate blocks
        // nobody edited — inputBindings values have to name a declared port. Hence the full integrity
        // pass rather than a bare setter.
        Artifact existing = existing(List.of(standaloneWidget("grid-1", Map.of())), Map.of("rows", "customer"));
        when(repository.findByOrgKeyAndId("demo", "q3-plan")).thenReturn(Optional.of(existing));

        ArtifactPropertiesInput noPorts = new ArtifactPropertiesInput().title("Renamed").inputPorts(List.of());

        assertThatThrownBy(() -> updateArtifactProperties.execute("demo", "q3-plan", noPorts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid artifact");
    }

    @Test
    void unknownArtifactIsNotFound() {
        when(repository.findByOrgKeyAndId("demo", "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateArtifactProperties.execute("demo", "missing", input("Renamed")))
                .isInstanceOf(ArtifactNotFoundException.class);
    }

    // region fixtures
    private Artifact existing(List<ArtifactBlock> blocks) {
        return existing(blocks, Map.of());
    }

    private Artifact existing(List<ArtifactBlock> blocks, Map<String, String> inputBindingsOfFirstBlock) {
        List<ArtifactBlock> withBindings = blocks.stream()
                .map(b -> b == blocks.get(0)
                        ? new ArtifactBlock(b.id(), b.kind(), b.editable(), b.content(), b.placement(),
                                b.type(), b.props(), inputBindingsOfFirstBlock, b.outputBindings())
                        : b)
                .toList();
        ArtifactGraph graph = new ArtifactGraph(
                List.of(new ArtifactInputPort("customer", PortType.ENTITY_REF, true, null, null, "Customer", null, null)),
                List.of(),
                withBindings);
        return new Artifact("demo", "q3-plan", "Original", "Original description", graph);
    }

    private ArtifactPropertiesInput input(String title) {
        return new ArtifactPropertiesInput()
                .title(title)
                .description("Updated description")
                .inputPorts(List.of(new com.processpuzzle.artifact.model.ArtifactInputPort()
                        .name("customer")
                        .type(com.processpuzzle.artifact.model.PortType.ENTITY_REF)
                        .required(true)
                        .entityType("Customer")))
                .outputPorts(List.of());
    }

    private ArtifactBlock standaloneWidget(String id, Map<String, Object> props) {
        return new ArtifactBlock(id, BlockKind.WIDGET, null, null, WidgetPlacement.STANDALONE,
                "entity-grid", props, Map.of(), Map.of());
    }
    // endregion
}
