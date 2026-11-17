package com.processpuzzle.calculation.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.usecase.FindCalculationDefinition;
import com.processpuzzle.calculation.usecase.ImportCalculationDefinitions;
import com.processpuzzle.calculation.usecase.ImportOutcome;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.ImportedItem;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CalculationImportParticipantTest {

    private ImportCalculationDefinitions importer;
    private final com.processpuzzle.calculation.usecase.DeleteCalculationDefinition deleteDefinition =
            mock(com.processpuzzle.calculation.usecase.DeleteCalculationDefinition.class);
    private CalculationImportParticipant participant;

    @BeforeEach
    void setUp() {
        importer = mock(ImportCalculationDefinitions.class);
        FindCalculationDefinition find = mock(FindCalculationDefinition.class);
        participant = new CalculationImportParticipant(importer, find, deleteDefinition);
        when(find.findAll("org", Channel.DRAFT)).thenReturn(List.of(
                CalculationDefinition.builder().id("a").source("calculation: a\n").build()));
    }

    @Test
    void reportsCreatedAndUpdatedCalculations() {
        when(importer.execute("org", "file")).thenReturn(new ImportOutcome(1, 1, List.of(), List.of("a", "b")));

        assertThat(participant.apply("org", "calculations/x.yaml", "file".getBytes(StandardCharsets.UTF_8)).items())
                .containsExactly(new ImportedItem("a", ImportedItem.Action.UPDATE), new ImportedItem("b", ImportedItem.Action.CREATE));
    }

    @Test
    void rejectsAFileWithErrors() {
        when(importer.execute("org", "file")).thenReturn(ImportOutcome.rejected(List.of("'b': broken")));
        assertThat(participant.apply("org", "x.yaml", "file".getBytes(StandardCharsets.UTF_8)).errors())
                .containsExactly("x.yaml: 'b': broken");

        when(importer.execute("org", "file")).thenThrow(new IllegalStateException("boom"));
        assertThat(participant.apply("org", "x.yaml", "file".getBytes(StandardCharsets.UTF_8)).errors()).containsExactly("x.yaml: boom");
    }

    @Test
    void fingerprintsBySourceAndRemovesAll() {
        assertThat(participant.fingerprints("org", Set.of("a", "z"))).containsOnlyKeys("a");
        when(importer.removeAll("org")).thenReturn(List.of("a"));
        assertThat(participant.removeAll("org")).containsExactly("a");
        assertThat(DefaultCalculationImporter.orgKeyOf("acme-calculations.yaml")).isEqualTo("acme");
        assertThat(DefaultCalculationImporter.orgKeyOf("-calculations.yaml")).isNull();
        assertThat(DefaultCalculationImporter.orgKeyOf("acme.yaml")).isNull();
    }

    private static final String FILE = "calculation: ranking\nname: Ranking\n---\ncalculation: totals\nname: Totals\n";

    @Test
    void upgradesCalculationByCalculation_keepingTheOtherDocumentsOut() throws Exception {
        when(importer.execute(org.mockito.ArgumentMatchers.eq("org"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ImportOutcome(1, 0, List.of(), List.of("totals")));

        assertThat(participant.supportsUpgrade()).isTrue();
        assertThat(participant.keysIn("calculations/x.yaml", FILE.getBytes(StandardCharsets.UTF_8))).containsExactly("ranking", "totals");
        participant.applyOnly("org", "calculations/x.yaml", FILE.getBytes(StandardCharsets.UTF_8), Set.of("totals"));

        org.mockito.ArgumentCaptor<String> sent = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(importer).execute(org.mockito.ArgumentMatchers.eq("org"), sent.capture());
        assertThat(sent.getValue()).contains("calculation: totals").doesNotContain("ranking");
        assertThat(participant.applyOnly("org", "calculations/x.yaml", FILE.getBytes(StandardCharsets.UTF_8), Set.of("absent")).items()).isEmpty();
    }

    @Test
    void aDocumentThatCannotBeReadHasNoKey_andStaysInTheFileSoItIsReported() {
        byte[] file = "calculation: ranking\n---\nnot: [valid".getBytes(StandardCharsets.UTF_8);
        when(importer.execute(org.mockito.ArgumentMatchers.eq("org"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(ImportOutcome.rejected(List.of("document 2: not valid YAML")));

        assertThat(participant.keysIn("calculations/x.yaml", file)).containsExactly("ranking");
        participant.applyOnly("org", "calculations/x.yaml", file, Set.of("ranking"));

        org.mockito.ArgumentCaptor<String> sent = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(importer).execute(org.mockito.ArgumentMatchers.eq("org"), sent.capture());
        assertThat(sent.getValue()).contains("ranking").contains("not: [valid");
    }

    @Test
    void removingDrafts_isTheDesignersDelete() {
        doThrow(new IllegalStateException("no such calculation")).when(deleteDefinition).delete("org", "ghost");

        var removal = participant.removeDrafts("org", new java.util.LinkedHashSet<>(List.of("ranking", "ghost")));

        assertThat(removal.removed()).containsExactly("ranking");
        assertThat(removal.refused()).containsEntry("ghost", "no such calculation");
    }
}
