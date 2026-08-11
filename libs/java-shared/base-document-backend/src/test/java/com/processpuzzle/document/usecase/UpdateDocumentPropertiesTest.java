package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentGraph;
import com.processpuzzle.document.domain.DocumentInputPort;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.PortType;
import com.processpuzzle.document.domain.WidgetPlacement;
import com.processpuzzle.document.model.DocumentPropertiesInput;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateDocumentPropertiesTest {

    private DocumentRepository repository;
    private UpdateDocumentProperties updateDocumentProperties;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        updateDocumentProperties = new UpdateDocumentProperties(
                repository, new DocumentReferentialIntegrityChecker(), new DocumentMapper());
    }

    @Test
    void carriesTheStoredBlocksOverUntouched() {
        // The whole reason this use case exists: DocumentPropertiesInput has no blocks field, so a
        // Properties save cannot discard content maintained through the block-level endpoints.
        Document existing = existing(List.of(standaloneWidget("chart-1", Map.of())));
        when(repository.findByOrgKeyAndId("demo", "q3-plan")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Document saved = updateDocumentProperties.execute("demo", "q3-plan", input("Renamed"));

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getTitle()).isEqualTo("Renamed");
        assertThat(saved.getGraph().blocks()).extracting(DocumentBlock::id).containsExactly("chart-1");
    }

    @Test
    void replacesThePortsRatherThanMergingThem() {
        Document existing = existing(List.of());
        when(repository.findByOrgKeyAndId("demo", "q3-plan")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Document saved = updateDocumentProperties.execute("demo", "q3-plan", input("Renamed"));

        assertThat(saved.getGraph().inputPorts()).extracting(DocumentInputPort::name).containsExactly("customer");
    }

    @Test
    void rejectsDeletingAPortThatAnUntouchedBlockStillBindsTo() {
        // Ports are the one thing this endpoint does change, and changing them can invalidate blocks
        // nobody edited — inputBindings values have to name a declared port. Hence the full integrity
        // pass rather than a bare setter.
        Document existing = existing(List.of(standaloneWidget("grid-1", Map.of())), Map.of("rows", "customer"));
        when(repository.findByOrgKeyAndId("demo", "q3-plan")).thenReturn(Optional.of(existing));

        DocumentPropertiesInput noPorts = new DocumentPropertiesInput().title("Renamed").inputPorts(List.of());

        assertThatThrownBy(() -> updateDocumentProperties.execute("demo", "q3-plan", noPorts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid document");
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId("demo", "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateDocumentProperties.execute("demo", "missing", input("Renamed")))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    // region fixtures
    private Document existing(List<DocumentBlock> blocks) {
        return existing(blocks, Map.of());
    }

    private Document existing(List<DocumentBlock> blocks, Map<String, String> inputBindingsOfFirstBlock) {
        List<DocumentBlock> withBindings = blocks.stream()
                .map(b -> b == blocks.get(0)
                        ? new DocumentBlock(b.id(), b.kind(), b.editable(), b.content(), b.placement(),
                                b.type(), b.props(), inputBindingsOfFirstBlock, b.outputBindings())
                        : b)
                .toList();
        DocumentGraph graph = new DocumentGraph(
                List.of(new DocumentInputPort("customer", PortType.ENTITY_REF, true, null, null, "Customer", null, null)),
                List.of(),
                withBindings);
        return new Document("demo", "q3-plan", "Original", "Original description", graph);
    }

    private DocumentPropertiesInput input(String title) {
        return new DocumentPropertiesInput()
                .title(title)
                .description("Updated description")
                .inputPorts(List.of(new com.processpuzzle.document.model.DocumentInputPort()
                        .name("customer")
                        .type(com.processpuzzle.document.model.PortType.ENTITY_REF)
                        .required(true)
                        .entityType("Customer")))
                .outputPorts(List.of());
    }

    private DocumentBlock standaloneWidget(String id, Map<String, Object> props) {
        return new DocumentBlock(id, BlockKind.WIDGET, null, null, WidgetPlacement.STANDALONE,
                "entity-grid", props, Map.of(), Map.of());
    }
    // endregion
}
