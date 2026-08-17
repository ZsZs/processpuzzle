package com.processpuzzle.basestate.usecase;

import com.processpuzzle.basestate.domain.State;
import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.StateMachineDefinitionRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

        assertThat(yaml).contains("entityName: \"order\"");
        assertThat(yaml).contains("initialStateKey: \"draft\"");
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
        assertThat(outcome.updated()).isEqualTo(0);
    }

    @Test
    void import_shouldHandleEmptyYamlWithoutNPE() throws IOException {
        String yaml = "";
        ImportOutcome outcome = importUseCase.execute(ORG, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isEqualTo(0);
        assertThat(outcome.updated()).isEqualTo(0);
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
}
