package com.processpuzzle.calculation.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
    private CalculationImportParticipant participant;

    @BeforeEach
    void setUp() {
        importer = mock(ImportCalculationDefinitions.class);
        FindCalculationDefinition find = mock(FindCalculationDefinition.class);
        participant = new CalculationImportParticipant(importer, find);
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
}
