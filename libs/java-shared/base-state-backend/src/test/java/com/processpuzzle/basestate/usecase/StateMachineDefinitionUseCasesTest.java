package com.processpuzzle.basestate.usecase;

import com.processpuzzle.basestate.domain.State;
import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.StateMachineDefinitionKey;
import com.processpuzzle.basestate.domain.StateMachineDefinitionRepository;
import com.processpuzzle.basestate.domain.Transition;
import com.processpuzzle.basestate.usecase.exception.StateMachineAlreadyExistsException;
import com.processpuzzle.basestate.usecase.exception.StateMachineNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StateMachineDefinitionUseCasesTest {

    private static final String ORG = "org-1";
    private static final String ENTITY = "order";

    private StateMachineDefinitionRepository repository;
    private StateMachineTopologyValidator validator;

    private CreateStateMachineDefinition createUseCase;
    private UpdateStateMachineDefinition updateUseCase;
    private DeleteStateMachineDefinition deleteUseCase;
    private FindStateMachineDefinition findUseCase;
    private FindAllStateMachineDefinitions findAllUseCase;

    private List<State> states;
    private List<Transition> transitions;

    @BeforeEach
    void setUp() {
        repository = mock(StateMachineDefinitionRepository.class);
        validator = mock(StateMachineTopologyValidator.class);

        createUseCase = new CreateStateMachineDefinition(repository, validator);
        updateUseCase = new UpdateStateMachineDefinition(repository, validator);
        deleteUseCase = new DeleteStateMachineDefinition(repository);
        findUseCase = new FindStateMachineDefinition(repository);
        findAllUseCase = new FindAllStateMachineDefinitions(repository);

        states = List.of(new State("draft", "Draft", null, false, false, null));
        transitions = List.of();
    }

    @Test
    void create_shouldSaveWhenEntityDoesNotExist() {
        when(repository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(false);
        when(repository.save(any(StateMachineDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        StateMachineDefinition def = createUseCase.execute(ORG, ENTITY, "Order SM", "desc", "state", "draft", states, transitions);

        assertThat(def.getOrgKey()).isEqualTo(ORG);
        assertThat(def.getEntityName()).isEqualTo(ENTITY);
        verify(validator).validate("draft", states, transitions);
        verify(repository).save(any(StateMachineDefinition.class));
    }

    @Test
    void create_shouldThrowWhenAlreadyExists() {
        when(repository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(true);

        assertThatThrownBy(() -> createUseCase.execute(ORG, ENTITY, "Order SM", null, "state", "draft", states, transitions))
                .isInstanceOf(StateMachineAlreadyExistsException.class);
    }

    @Test
    void update_shouldReplaceTopologyWhenFound() {
        StateMachineDefinition existing = new StateMachineDefinition(ORG, ENTITY, "Old", null, "state", "draft", states, transitions);
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StateMachineDefinition updated = updateUseCase.execute(ORG, ENTITY, "New", "new desc", "status", "draft", states, transitions);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getDescription()).isEqualTo("new desc");
        assertThat(updated.getStateAttributeKey()).isEqualTo("status");
        verify(validator).validate("draft", states, transitions);
    }

    @Test
    void update_shouldThrowWhenNotFound() {
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateUseCase.execute(ORG, ENTITY, "New", null, "state", "draft", states, transitions))
                .isInstanceOf(StateMachineNotFoundException.class);
    }

    @Test
    void delete_shouldDeleteByIdWhenExists() {
        when(repository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(true);

        deleteUseCase.execute(ORG, ENTITY);

        verify(repository).deleteById(eq(new StateMachineDefinitionKey(ORG, ENTITY)));
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(repository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(false);

        assertThatThrownBy(() -> deleteUseCase.execute(ORG, ENTITY))
                .isInstanceOf(StateMachineNotFoundException.class);
    }

    @Test
    void find_shouldReturnWhenExists() {
        StateMachineDefinition existing = new StateMachineDefinition(ORG, ENTITY, "SM", null, "state", "draft", states, transitions);
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.of(existing));

        StateMachineDefinition result = findUseCase.execute(ORG, ENTITY);
        assertThat(result).isSameAs(existing);
    }

    @Test
    void find_shouldThrowWhenNotFound() {
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findUseCase.execute(ORG, ENTITY))
                .isInstanceOf(StateMachineNotFoundException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_shouldQueryRepository() {
        StateMachineDefinition existing = new StateMachineDefinition(ORG, ENTITY, "SM", null, "state", "draft", states, transitions);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existing)));

        Page<StateMachineDefinition> page = findAllUseCase.execute(ORG, null, null, 0, 10);
        assertThat(page.getContent()).containsExactly(existing);
    }
}
