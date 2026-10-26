package com.processpuzzle.baseentity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionValidator;
import com.processpuzzle.baseentity.definition.usecases.inbound.CreateEntityDefinitionUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.FindEntityDefinitionsByCodesUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.ReplaceEntityDefinitionUseCase;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityDefinitionAuthoringParticipantTest {

    private static final String ORG = "acme";
    private static final String DRAFT_JSON = "{\"code\":\"invoice\",\"name\":\"Invoice\",\"attributes\":[]}";

    @Mock
    private EntityDefinitionRepository repository;

    @Mock
    private EntityDefinitionValidator validator;

    @Mock
    private CreateEntityDefinitionUseCase createUseCase;

    @Mock
    private ReplaceEntityDefinitionUseCase replaceUseCase;

    @Mock
    private FindEntityDefinitionsByCodesUseCase findByCodes;

    private EntityDefinitionAuthoringParticipant participant;

    @BeforeEach
    void setUp() {
        participant = new EntityDefinitionAuthoringParticipant(repository, new EntityDefinitionMapper(), validator,
            createUseCase, replaceUseCase, findByCodes);
    }

    @Test
    void isTheEntityKind() {
        assertThat(participant.kind()).isEqualTo(DefinitionKinds.ENTITY);
    }

    @Test
    void theSchemaIsTheInputComponentWithItsReferencesInlined() {
        assertThat(participant.schema())
            .contains("\"required\"", "\"code\"", "\"attributes\"")
            .doesNotContain("$ref");
    }

    @Test
    void aNewDraftIsCreated() {
        when(findByCodes.find(ORG, Set.of("invoice"), Channel.DRAFT)).thenReturn(List.of());

        List<AuthoringIssue> issues = participant.saveDraft(ORG, "invoice", DRAFT_JSON);

        assertThat(issues).isEmpty();
        verify(createUseCase).create(eq(ORG), any(BaseEntityDefinition.class));
        verify(replaceUseCase, never()).replace(any(), any(), any());
    }

    @Test
    void anExistingDraftIsReplaced() {
        when(findByCodes.find(ORG, Set.of("invoice"), Channel.DRAFT)).thenReturn(List.of(new BaseEntityDefinition()));

        List<AuthoringIssue> issues = participant.saveDraft(ORG, "invoice", DRAFT_JSON);

        assertThat(issues).isEmpty();
        verify(replaceUseCase).replace(eq(ORG), eq("invoice"), any(BaseEntityDefinition.class));
        verify(createUseCase, never()).create(any(), any());
    }

    @Test
    void invalidJsonIsReturnedAsAnIssueAndNothingIsWritten() {
        List<AuthoringIssue> issues = participant.saveDraft(ORG, "invoice", "{not json");

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).severity()).isEqualTo(AuthoringIssue.Severity.ERROR);
        verify(createUseCase, never()).create(any(), any());
        verify(replaceUseCase, never()).replace(any(), any(), any());
    }

    @Test
    void validationViolationsAreReturnedAsIssuesPerAttribute() {
        when(findByCodes.find(ORG, Set.of("invoice"), Channel.DRAFT)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new ValidationException(List.of(
                new ValidationException.Violation("amount", "amount needs a value kind"))))
            .when(createUseCase).create(any(), any());

        List<AuthoringIssue> issues = participant.saveDraft(ORG, "invoice", DRAFT_JSON);

        assertThat(issues).containsExactly(
            AuthoringIssue.error("amount", "amount needs a value kind"));
    }

    @Test
    void aConflictIsReturnedAsAnIssueAtTheDraftLevel() {
        when(findByCodes.find(ORG, Set.of("invoice"), Channel.DRAFT)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new ConflictException("Entity definition 'invoice' already exists"))
            .when(createUseCase).create(any(), any());

        List<AuthoringIssue> issues = participant.saveDraft(ORG, "invoice", DRAFT_JSON);

        assertThat(issues).containsExactly(AuthoringIssue.error("", "Entity definition 'invoice' already exists"));
    }

    @Test
    void aDraftWhoseCodeDiffersFromTheKeyIsRefused() {
        assertThatThrownBy(() -> participant.saveDraft(ORG, "order", DRAFT_JSON))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match the key");
        verify(createUseCase, never()).create(any(), any());
    }

    @Test
    void validateReturnsTheViolationsOfTheStoredDraft() {
        BaseEntityDefinition stored = new BaseEntityDefinition();
        when(findByCodes.find(ORG, Set.of("invoice"), Channel.DRAFT)).thenReturn(List.of(stored));
        org.mockito.Mockito.doThrow(new ValidationException(List.of(
                new ValidationException.Violation(null, "no attributes"))))
            .when(validator).validate(stored);

        assertThat(participant.validate(ORG, "invoice")).containsExactly(AuthoringIssue.error("", "no attributes"));
    }

    @Test
    void validateOfAnAbsentDraftHasNothingToSay() {
        when(findByCodes.find(ORG, Set.of("invoice"), Channel.DRAFT)).thenReturn(List.of());

        assertThat(participant.validate(ORG, "invoice")).isEmpty();
    }

    @Test
    void listIsTheSortedCodesOfTheDrafts() {
        when(repository.findAllByOrgKeyAndChannel(ORG, Channel.DRAFT)).thenReturn(List.of(
            definition("order"), definition("invoice")));

        assertThat(participant.list(ORG)).containsExactly("invoice", "order");
    }

    @Test
    void getWritesTheDraftAsJsonOrIsEmpty() {
        when(repository.findByOrgKeyAndCodeAndChannel(ORG, "invoice", Channel.DRAFT)).thenReturn(java.util.Optional.of(definition("invoice")));
        when(repository.findByOrgKeyAndCodeAndChannel(ORG, "absent", Channel.DRAFT)).thenReturn(java.util.Optional.empty());

        assertThat(participant.get(ORG, "invoice")).hasValueSatisfying(json -> assertThat(json).contains("\"code\":\"invoice\""));
        assertThat(participant.get(ORG, "absent")).isEmpty();
    }

    private static BaseEntityDefinition definition(String code) {
        BaseEntityDefinition definition = new BaseEntityDefinition();
        definition.setCode(code);
        definition.setName(code);
        return definition;
    }
}
