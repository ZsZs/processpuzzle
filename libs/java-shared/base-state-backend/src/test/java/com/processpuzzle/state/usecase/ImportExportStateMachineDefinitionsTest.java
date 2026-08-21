package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportExportStateMachineDefinitionsTest {

    private static final String ORG = "org-1";

    private StateMachineDefinitionRepository repository;
    private StateMachineTopologyValidator validator;
    private ImportStateMachineDefinitions importUseCase;
    private ExportStateMachineDefinitions exportUseCase;

    @BeforeEach
    void setUp() {
        repository = mock(StateMachineDefinitionRepository.class);
        validator = mock(StateMachineTopologyValidator.class);
        importUseCase = new ImportStateMachineDefinitions(repository, validator);
        exportUseCase = new ExportStateMachineDefinitions(repository);
    }

    @Test
    void export_shouldReturnYamlBytes() throws IOException {
        List<State> states = List.of(new State("draft", "Draft", null, false, false, null));
        StateMachineDefinition def = StateMachineDefinition.builder()
                .orgKey(ORG)
                .entityName("order")
                .name("Order SM")
                .description("desc")
                .stateAttributeKey("state")
                .initialStateKey("draft")
                .states(states)
                .transitions(List.of())
                .build();
        when(repository.findByOrgKeyAndEntityName(ORG, "order")).thenReturn(Optional.of(def));

        byte[] yamlBytes = exportUseCase.execute(ORG, "order");
        String yaml = new String(yamlBytes, StandardCharsets.UTF_8);

        assertThat(yaml)
                .contains("entityName: \"order\"")
                .contains("initialStateKey: \"draft\"");
    }

    @Test
    void export_allEntities_shouldReturnYamlBytes() throws IOException {
        List<State> states = List.of(new State("draft", "Draft", null, false, false, null));
        StateMachineDefinition def = StateMachineDefinition.builder()
                .orgKey(ORG)
                .entityName("order")
                .name("Order SM")
                .description("desc")
                .stateAttributeKey("state")
                .initialStateKey("draft")
                .states(states)
                .transitions(List.of())
                .build();
        when(repository.findByOrgKey(ORG)).thenReturn(List.of(def));

        byte[] yamlBytes = exportUseCase.execute(ORG, null);
        String yaml = new String(yamlBytes, StandardCharsets.UTF_8);

        assertThat(yaml)
                .contains("entityName: \"order\"")
                .contains("initialStateKey: \"draft\"");
    }

    @Test
    void export_singleEntityNotFound_shouldReturnEmptyList() throws IOException {
        when(repository.findByOrgKeyAndEntityName(ORG, "nonexistent")).thenReturn(Optional.empty());

        byte[] yamlBytes = exportUseCase.execute(ORG, "nonexistent");
        String yaml = new String(yamlBytes, StandardCharsets.UTF_8);

        assertThat(yaml).contains("stateMachines: []");
    }

    @Test
    void import_shouldSucceedOnValidYaml() throws IOException {
        String yaml = """
                stateMachines:
                  - entityName: "invoice"
                    name: "Invoice SM"
                    stateAttributeKey: "status"
                    initialStateKey: "draft"
                    states:
                      - key: "draft"
                        name: "Draft"
                    transitions: []
                """;
        when(repository.findByOrgKeyAndEntityName(ORG, "invoice")).thenReturn(Optional.empty());

        ImportOutcome outcome = importUseCase.execute(ORG, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.updated()).isZero();
        verify(repository).save(any(StateMachineDefinition.class));
    }

    @Test
    void import_shouldUpdateExistingStateMachine() throws IOException {
        String yaml = """
                stateMachines:
                  - entityName: "invoice"
                    name: "Updated Invoice SM"
                    stateAttributeKey: "status"
                    initialStateKey: "draft"
                    states:
                      - key: "draft"
                        name: "Draft"
                    transitions: []
                """;
        StateMachineDefinition existing = StateMachineDefinition.builder()
                .orgKey(ORG)
                .entityName("invoice")
                .name("Old SM")
                .stateAttributeKey("status")
                .initialStateKey("draft")
                .build();
        when(repository.findByOrgKeyAndEntityName(ORG, "invoice")).thenReturn(Optional.of(existing));

        ImportOutcome outcome = importUseCase.execute(ORG, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isEqualTo(1);
        assertThat(existing.getName()).isEqualTo("Updated Invoice SM");
    }

    @Test
    void import_shouldHandleEmptyYamlWithoutNPE() throws IOException {
        String yaml = "";
        ImportOutcome outcome = importUseCase.execute(ORG, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isZero();
    }

    @Test
    void import_shouldRejectMissingEntityName() throws IOException {
        String yaml = """
                stateMachines:
                  - name: "No Entity SM"
                    initialStateKey: "draft"
                """;
        ImportOutcome outcome = importUseCase.execute(ORG, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.errors()).isNotEmpty();
        assertThat(outcome.errors().get(0)).contains("missing 'entityName'");
    }

    @Test
    void import_shouldRejectDuplicateEntityNamesInFile() throws IOException {
        String yaml = """
                stateMachines:
                  - entityName: "invoice"
                    name: "SM 1"
                    initialStateKey: "draft"
                  - entityName: "invoice"
                    name: "SM 2"
                    initialStateKey: "draft"
                """;
        ImportOutcome outcome = importUseCase.execute(ORG, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.errors()).isNotEmpty();
        assertThat(outcome.errors().get(0)).contains("Duplicate entityName");
    }

    @Test
    void import_shouldCollectTopologyValidatorErrors() throws IOException {
        String yaml = """
                stateMachines:
                  - entityName: "invoice"
                    name: "Invoice SM"
                    initialStateKey: "invalidState"
                    states: []
                    transitions: []
                """;
        doThrow(new IllegalArgumentException("invalid initialStateKey"))
                .when(validator).validate(anyString(), any(), any());

        ImportOutcome outcome = importUseCase.execute(ORG, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.errors()).isNotEmpty();
        assertThat(outcome.errors().get(0)).contains("'invoice': invalid initialStateKey");
    }
}
