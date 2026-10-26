package com.processpuzzle.state.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.CreateStateMachineDefinition;
import com.processpuzzle.state.usecase.StateMachineTopologyValidator;
import com.processpuzzle.state.usecase.UpdateStateMachineDefinition;
import com.processpuzzle.state.usecase.exception.StateMachineAlreadyExistsException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateMachineAuthoringParticipantTest {

    private static final String ORG = "acme";
    private static final String DRAFT_JSON = """
        {"entityName":"invoice","name":"Invoice lifecycle","stateAttributeKey":"status",
         "initialStateKey":"draft","states":[{"key":"draft","name":"Draft"}],"transitions":[]}""";

    @Mock
    private StateMachineDefinitionRepository repository;

    @Mock
    private StateMachineTopologyValidator validator;

    @Mock
    private CreateStateMachineDefinition createUseCase;

    @Mock
    private UpdateStateMachineDefinition updateUseCase;

    private StateMachineAuthoringParticipant participant;

    @BeforeEach
    void setUp() {
        participant = new StateMachineAuthoringParticipant(repository, new StateMapper(), validator,
            createUseCase, updateUseCase);
    }

    @Test
    void isTheStateKind() {
        assertThat(participant.kind()).isEqualTo(DefinitionKinds.STATE);
    }

    @Test
    void theSchemaIsTheInputComponentWithItsReferencesInlined() {
        assertThat(participant.schema())
            .contains("\"entityName\"", "\"states\"")
            .doesNotContain("$ref");
    }

    @Test
    void aNewDraftIsCreated() {
        when(repository.existsByOrgKeyAndEntityNameAndChannel(ORG, "invoice", Channel.DRAFT)).thenReturn(false);

        assertThat(participant.saveDraft(ORG, "invoice", DRAFT_JSON)).isEmpty();
        verify(createUseCase).execute(any(StateMachineDefinition.class));
        verify(updateUseCase, never()).execute(anyString(), anyString(), any());
    }

    @Test
    void anExistingDraftIsUpdated() {
        when(repository.existsByOrgKeyAndEntityNameAndChannel(ORG, "invoice", Channel.DRAFT)).thenReturn(true);

        assertThat(participant.saveDraft(ORG, "invoice", DRAFT_JSON)).isEmpty();
        verify(updateUseCase).execute(eq(ORG), eq("invoice"), any(StateMachineDefinition.class));
        verify(createUseCase, never()).execute(any());
    }

    @Test
    void invalidJsonIsReturnedAsAnIssueAndNothingIsWritten() {
        List<AuthoringIssue> issues = participant.saveDraft(ORG, "invoice", "{not json");

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).severity()).isEqualTo(AuthoringIssue.Severity.ERROR);
        verify(createUseCase, never()).execute(any());
        verify(updateUseCase, never()).execute(anyString(), anyString(), any());
    }

    @Test
    void aTopologyProblemIsReturnedAsAnIssue() {
        when(repository.existsByOrgKeyAndEntityNameAndChannel(ORG, "invoice", Channel.DRAFT)).thenReturn(false);
        doThrow(new IllegalArgumentException("initialStateKey 'draft' does not resolve to a declared state"))
            .when(createUseCase).execute(any());

        assertThat(participant.saveDraft(ORG, "invoice", DRAFT_JSON))
            .containsExactly(AuthoringIssue.error("", "initialStateKey 'draft' does not resolve to a declared state"));
    }

    @Test
    void aRacingCreateIsReturnedAsAnIssue() {
        when(repository.existsByOrgKeyAndEntityNameAndChannel(ORG, "invoice", Channel.DRAFT)).thenReturn(false);
        doThrow(new StateMachineAlreadyExistsException(ORG, "invoice")).when(createUseCase).execute(any());

        assertThat(participant.saveDraft(ORG, "invoice", DRAFT_JSON)).hasSize(1);
    }

    @Test
    void aDraftWhoseEntityNameDiffersFromTheKeyIsRefused() {
        assertThatThrownBy(() -> participant.saveDraft(ORG, "order", DRAFT_JSON))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match the key");
        verify(createUseCase, never()).execute(any());
    }

    @Test
    void validateReturnsTheTopologyProblemOfTheStoredDraft() {
        StateMachineDefinition stored = StateMachineDefinition.builder()
            .orgKey(ORG).entityName("invoice").stateAttributeKey("status").initialStateKey("draft")
            .states(List.of()).transitions(List.of()).build();
        when(repository.findByOrgKeyAndEntityNameAndChannel(ORG, "invoice", Channel.DRAFT)).thenReturn(Optional.of(stored));
        doThrow(new IllegalArgumentException("A state machine must declare at least one state"))
            .when(validator).validate(ORG, "invoice", "status", "draft", List.of(), List.of());

        assertThat(participant.validate(ORG, "invoice"))
            .containsExactly(AuthoringIssue.error("", "A state machine must declare at least one state"));
    }

    @Test
    void validateOfAnAbsentDraftHasNothingToSay() {
        when(repository.findByOrgKeyAndEntityNameAndChannel(ORG, "invoice", Channel.DRAFT)).thenReturn(Optional.empty());

        assertThat(participant.validate(ORG, "invoice")).isEmpty();
    }

    @Test
    void listIsTheSortedEntityNamesOfTheDrafts() {
        when(repository.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).thenReturn(List.of(
            machine("order"), machine("invoice")));

        assertThat(participant.list(ORG)).containsExactly("invoice", "order");
    }

    @Test
    void getWritesTheDraftAsJsonOrIsEmpty() {
        when(repository.findByOrgKeyAndEntityNameAndChannel(ORG, "invoice", Channel.DRAFT)).thenReturn(Optional.of(machine("invoice")));
        when(repository.findByOrgKeyAndEntityNameAndChannel(ORG, "absent", Channel.DRAFT)).thenReturn(Optional.empty());

        assertThat(participant.get(ORG, "invoice")).hasValueSatisfying(json -> assertThat(json).contains("\"entityName\":\"invoice\""));
        assertThat(participant.get(ORG, "absent")).isEmpty();
    }

    private static StateMachineDefinition machine(String entityName) {
        return StateMachineDefinition.builder()
            .orgKey(ORG).entityName(entityName).stateAttributeKey("status").initialStateKey("draft")
            .states(List.of()).transitions(List.of()).build();
    }
}
