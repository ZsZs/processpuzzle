package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import com.processpuzzle.state.usecase.ExportStateMachineDefinitions;
import com.processpuzzle.state.usecase.ImportOutcome;
import com.processpuzzle.state.usecase.ImportStateMachineDefinitions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The participant decides CREATE versus UPDATE from what the organization's export held before the
 * import ran, and fingerprints each machine from that same export.
 */
@ExtendWith(MockitoExtension.class)
class StateMachineImportParticipantTest {

    private static final String ORG = "acme";
    private static final String FILE = "state/orders.yaml";

    private static final String BUNDLE = """
            stateMachines:
              - entityName: order
                name: Order lifecycle
                stateAttributeKey: status
                initialStateKey: NEW
              - entityName: invoice
                name: Invoice lifecycle
                stateAttributeKey: status
                initialStateKey: DRAFT
            """;

    @Mock
    private ImportStateMachineDefinitions importDefinitions;

    @Mock
    private ExportStateMachineDefinitions exportDefinitions;

    private StateMachineImportParticipant participant;

    @BeforeEach
    void setUp() {
        participant = new StateMachineImportParticipant(importDefinitions, exportDefinitions);
    }

    @Test
    void runsAfterEntitiesAsTheStateKind() {
        assertThat(participant.kind()).isEqualTo(DefinitionKinds.STATE);
        assertThat(participant.order()).isEqualTo(DefinitionKinds.STATE_ORDER);
        assertThat(participant.order()).isGreaterThan(DefinitionKinds.ENTITY_ORDER);
    }

    @Test
    void aMachineAlreadyInTheExportIsAnUpdateTheRestAreCreates() throws IOException {
        when(exportDefinitions.execute(ORG, null)).thenReturn(bytes("""
                stateMachines:
                  - entityName: order
                    name: Old order lifecycle
                """));
        when(importDefinitions.execute(eq(ORG), any())).thenReturn(new ImportOutcome(1, 1, List.of()));

        ParticipantResult result = participant.apply(ORG, FILE, bytes(BUNDLE));

        assertThat(result.isRejected()).isFalse();
        assertThat(result.items()).containsExactly(
                new ImportedItem("order", ImportedItem.Action.UPDATE),
                new ImportedItem("invoice", ImportedItem.Action.CREATE));
    }

    @Test
    void anEmptyOrganizationMakesEverythingACreate() throws IOException {
        when(exportDefinitions.execute(ORG, null)).thenReturn(bytes("stateMachines: []\n"));
        when(importDefinitions.execute(eq(ORG), any())).thenReturn(new ImportOutcome(2, 0, List.of()));

        ParticipantResult result = participant.apply(ORG, FILE, bytes(BUNDLE));

        assertThat(result.items()).extracting(ImportedItem::action)
                .containsOnly(ImportedItem.Action.CREATE);
    }

    @Test
    void passesTheWholeFileToTheImport() throws IOException {
        when(exportDefinitions.execute(ORG, null)).thenReturn(bytes("stateMachines: []\n"));
        when(importDefinitions.execute(eq(ORG), any())).thenReturn(new ImportOutcome(2, 0, List.of()));

        participant.apply(ORG, FILE, bytes(BUNDLE));

        ArgumentCaptor<InputStream> input = ArgumentCaptor.forClass(InputStream.class);
        verify(importDefinitions).execute(eq(ORG), input.capture());
        assertThat(new String(input.getValue().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(BUNDLE);
    }

    @Test
    void prefixesEveryErrorWithTheFileName() throws IOException {
        when(exportDefinitions.execute(ORG, null)).thenReturn(bytes("stateMachines: []\n"));
        when(importDefinitions.execute(eq(ORG), any()))
                .thenReturn(new ImportOutcome(0, 0, List.of("order: unknown attribute", "invoice: no states")));

        ParticipantResult result = participant.apply(ORG, FILE, bytes(BUNDLE));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.items()).isEmpty();
        assertThat(result.errors()).containsExactly(FILE + ": order: unknown attribute", FILE + ": invoice: no states");
    }

    @Test
    void unreadableYamlIsRejectedWithoutTouchingTheUseCases() {
        ParticipantResult result = participant.apply(ORG, FILE, bytes("stateMachines: [ unclosed"));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.errors()).singleElement().asString()
                .startsWith(FILE + ": not a readable state machine file: ");
        verifyNoInteractions(importDefinitions, exportDefinitions);
    }

    @Test
    void anImportFailureIsRejected() throws IOException {
        when(exportDefinitions.execute(ORG, null)).thenReturn(bytes("stateMachines: []\n"));
        when(importDefinitions.execute(eq(ORG), any())).thenThrow(new IllegalStateException("boom"));

        ParticipantResult result = participant.apply(ORG, FILE, bytes(BUNDLE));

        assertThat(result.errors()).containsExactly(FILE + ": boom");
    }

    @Test
    void anImportIoFailureIsRejected() throws IOException {
        when(exportDefinitions.execute(ORG, null)).thenReturn(bytes("stateMachines: []\n"));
        when(importDefinitions.execute(eq(ORG), any())).thenThrow(new IOException("truncated"));

        ParticipantResult result = participant.apply(ORG, FILE, bytes(BUNDLE));

        assertThat(result.errors()).containsExactly(FILE + ": truncated");
    }

    @Test
    void fingerprintsOnlyTheRequestedMachines() throws IOException {
        when(exportDefinitions.execute(ORG, null)).thenReturn(bytes(BUNDLE));

        Map<String, String> fingerprints = participant.fingerprints(ORG, Set.of("order", "ghost"));

        assertThat(fingerprints).containsOnlyKeys("order");
        assertThat(fingerprints.get("order")).contains("\"name\":\"Order lifecycle\"");
    }

    @Test
    void fingerprintsDoNotDependOnFieldOrder() throws IOException {
        when(exportDefinitions.execute(ORG, null)).thenReturn(
                bytes("""
                        stateMachines:
                          - entityName: order
                            name: Order lifecycle
                            initialStateKey: NEW
                        """),
                bytes("""
                        stateMachines:
                          - initialStateKey: NEW
                            name: Order lifecycle
                            entityName: order
                        """));

        String first = participant.fingerprints(ORG, Set.of("order")).get("order");
        String second = participant.fingerprints(ORG, Set.of("order")).get("order");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void aChangedMachineChangesItsFingerprint() throws IOException {
        when(exportDefinitions.execute(ORG, null)).thenReturn(
                bytes("stateMachines:\n  - entityName: order\n    name: Order lifecycle\n"),
                bytes("stateMachines:\n  - entityName: order\n    name: Renamed\n"));

        String first = participant.fingerprints(ORG, Set.of("order")).get("order");
        String second = participant.fingerprints(ORG, Set.of("order")).get("order");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void anUnreadableExportIsAnIllegalState() throws IOException {
        when(exportDefinitions.execute(ORG, null)).thenThrow(new IOException("db gone"));

        assertThatThrownBy(() -> participant.fingerprints(ORG, Set.of("order")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ORG);
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void removeAllDelegatesToTheUseCase() {
        when(importDefinitions.removeAll(ORG)).thenReturn(java.util.List.of("Order"));

        assertThat(participant.removeAll(ORG)).containsExactly("Order");
    }
}
