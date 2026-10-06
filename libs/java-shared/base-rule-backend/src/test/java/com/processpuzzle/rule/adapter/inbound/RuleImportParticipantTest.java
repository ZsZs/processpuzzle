package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import com.processpuzzle.rule.usecase.ExportRules;
import com.processpuzzle.rule.usecase.ImportOutcome;
import com.processpuzzle.rule.usecase.ImportRules;
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
 * {@code ImportRules} reports totals only, so the participant decides CREATE versus UPDATE from the
 * organization's export taken before the import, and fingerprints each rule from that same export.
 */
@ExtendWith(MockitoExtension.class)
class RuleImportParticipantTest {

    private static final String ORG = "acme";
    private static final String FILE = "rules/orders.yaml";

    private static final String BUNDLE = """
            rules:
              - id: order-positive-quantity
                context: order
                expression: quantity > 0
                severity: ERROR
              - id: order-has-customer
                context: order
                expression: customer != null
                severity: WARNING
            """;

    @Mock
    private ImportRules importRules;

    @Mock
    private ExportRules exportRules;

    private RuleImportParticipant participant;

    @BeforeEach
    void setUp() {
        participant = new RuleImportParticipant(importRules, exportRules);
    }

    @Test
    void runsAfterEntitiesAndStatesAsTheRuleKind() {
        assertThat(participant.kind()).isEqualTo(DefinitionKinds.RULE);
        assertThat(participant.order()).isEqualTo(DefinitionKinds.RULE_ORDER);
        assertThat(participant.order()).isGreaterThan(DefinitionKinds.STATE_ORDER);
    }

    @Test
    void aRuleAlreadyInTheExportIsAnUpdateTheRestAreCreates() throws IOException {
        when(exportRules.execute(ORG, null)).thenReturn(bytes("""
                rules:
                  - id: order-has-customer
                    context: order
                    expression: true
                    severity: INFO
                """));
        when(importRules.execute(eq(ORG), any())).thenReturn(new ImportOutcome(1, 1, List.of()));

        ParticipantResult result = participant.apply(ORG, FILE, bytes(BUNDLE));

        assertThat(result.isRejected()).isFalse();
        assertThat(result.items()).containsExactly(
                new ImportedItem("order-positive-quantity", ImportedItem.Action.CREATE),
                new ImportedItem("order-has-customer", ImportedItem.Action.UPDATE));
    }

    @Test
    void entriesWithoutIdAreSkippedAndRepeatedIdsReportedOnce() throws IOException {
        when(exportRules.execute(ORG, null)).thenReturn(bytes("rules: []\n"));
        when(importRules.execute(eq(ORG), any())).thenReturn(new ImportOutcome(1, 0, List.of()));

        ParticipantResult result = participant.apply(ORG, FILE, bytes("""
                rules:
                  - id: r1
                    expression: true
                  - expression: false
                  - id: r1
                    expression: true
                """));

        assertThat(result.items()).containsExactly(new ImportedItem("r1", ImportedItem.Action.CREATE));
    }

    @Test
    void unknownYamlKeysDoNotRejectTheFile() throws IOException {
        when(exportRules.execute(ORG, null)).thenReturn(bytes("rules: []\n"));
        when(importRules.execute(eq(ORG), any())).thenReturn(new ImportOutcome(1, 0, List.of()));

        ParticipantResult result = participant.apply(ORG, FILE, bytes("""
                rules:
                  - id: r1
                    somethingNew: 42
                """));

        assertThat(result.isRejected()).isFalse();
        assertThat(result.items()).extracting(ImportedItem::key).containsExactly("r1");
    }

    @Test
    void passesTheWholeFileToTheImport() throws IOException {
        when(exportRules.execute(ORG, null)).thenReturn(bytes("rules: []\n"));
        when(importRules.execute(eq(ORG), any())).thenReturn(new ImportOutcome(2, 0, List.of()));

        participant.apply(ORG, FILE, bytes(BUNDLE));

        ArgumentCaptor<InputStream> input = ArgumentCaptor.forClass(InputStream.class);
        verify(importRules).execute(eq(ORG), input.capture());
        assertThat(new String(input.getValue().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(BUNDLE);
    }

    @Test
    void prefixesEveryErrorWithTheFileName() throws IOException {
        when(exportRules.execute(ORG, null)).thenReturn(bytes("rules: []\n"));
        when(importRules.execute(eq(ORG), any()))
                .thenReturn(new ImportOutcome(0, 0, List.of("order-has-customer: bad expression", "unknown context")));

        ParticipantResult result = participant.apply(ORG, FILE, bytes(BUNDLE));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.items()).isEmpty();
        assertThat(result.errors())
                .containsExactly(FILE + ": order-has-customer: bad expression", FILE + ": unknown context");
    }

    @Test
    void unreadableYamlIsRejectedWithoutTouchingTheUseCases() {
        ParticipantResult result = participant.apply(ORG, FILE, bytes("rules: [ unclosed"));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.errors()).singleElement().asString().startsWith(FILE + ": not a readable rule file: ");
        verifyNoInteractions(importRules, exportRules);
    }

    @Test
    void aWronglyShapedFileIsRejected() {
        ParticipantResult result = participant.apply(ORG, FILE, bytes("rules: just-a-string\n"));

        assertThat(result.errors()).singleElement().asString().startsWith(FILE + ": not a readable rule file: ");
        verifyNoInteractions(importRules, exportRules);
    }

    @Test
    void anImportFailureIsRejected() throws IOException {
        when(exportRules.execute(ORG, null)).thenReturn(bytes("rules: []\n"));
        when(importRules.execute(eq(ORG), any())).thenThrow(new IllegalArgumentException("boom"));

        ParticipantResult result = participant.apply(ORG, FILE, bytes(BUNDLE));

        assertThat(result.errors()).containsExactly(FILE + ": boom");
    }

    @Test
    void anImportIoFailureIsRejected() throws IOException {
        when(exportRules.execute(ORG, null)).thenReturn(bytes("rules: []\n"));
        when(importRules.execute(eq(ORG), any())).thenThrow(new IOException("truncated"));

        ParticipantResult result = participant.apply(ORG, FILE, bytes(BUNDLE));

        assertThat(result.errors()).containsExactly(FILE + ": truncated");
    }

    @Test
    void fingerprintsOnlyTheRequestedRules() throws IOException {
        when(exportRules.execute(ORG, null)).thenReturn(bytes(BUNDLE));

        Map<String, String> fingerprints = participant.fingerprints(ORG, Set.of("order-has-customer", "ghost"));

        assertThat(fingerprints).containsOnlyKeys("order-has-customer");
        assertThat(fingerprints.get("order-has-customer"))
                .contains("\"expression\":\"customer != null\"")
                .doesNotContain("null,", ":null");
    }

    @Test
    void fingerprintsDoNotDependOnFieldOrder() throws IOException {
        when(exportRules.execute(ORG, null)).thenReturn(
                bytes("rules:\n  - id: r1\n    context: order\n    expression: x > 1\n"),
                bytes("rules:\n  - expression: x > 1\n    id: r1\n    context: order\n"));

        String first = participant.fingerprints(ORG, Set.of("r1")).get("r1");
        String second = participant.fingerprints(ORG, Set.of("r1")).get("r1");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void aChangedRuleChangesItsFingerprint() throws IOException {
        when(exportRules.execute(ORG, null)).thenReturn(
                bytes("rules:\n  - id: r1\n    expression: x > 1\n"),
                bytes("rules:\n  - id: r1\n    expression: x > 2\n"));

        String first = participant.fingerprints(ORG, Set.of("r1")).get("r1");
        String second = participant.fingerprints(ORG, Set.of("r1")).get("r1");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void anUnreadableExportIsAnIllegalState() throws IOException {
        when(exportRules.execute(ORG, null)).thenThrow(new IOException("db gone"));

        assertThatThrownBy(() -> participant.fingerprints(ORG, Set.of("r1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ORG);
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void removeAllDelegatesToTheUseCase() {
        when(importRules.removeAll(ORG)).thenReturn(java.util.List.of("max-quantity"));

        assertThat(participant.removeAll(ORG)).containsExactly("max-quantity");
    }
}
