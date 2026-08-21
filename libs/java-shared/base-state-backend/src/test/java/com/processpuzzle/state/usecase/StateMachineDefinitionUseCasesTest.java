package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionKey;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.domain.Transition;
import com.processpuzzle.state.usecase.exception.StateMachineAlreadyExistsException;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
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

        StateMachineDefinition input = sampleDefinition("Order SM", "desc", "state", "draft");
        StateMachineDefinition def = createUseCase.execute(input);

        assertThat(def.getOrgKey()).isEqualTo(ORG);
        assertThat(def.getEntityName()).isEqualTo(ENTITY);
        verify(validator).validate("draft", states, transitions);
        verify(repository).save(any(StateMachineDefinition.class));
    }

    @Test
    void create_shouldThrowWhenAlreadyExists() {
        when(repository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(true);

        StateMachineDefinition input = sampleDefinition("Order SM", null, "state", "draft");
        assertThatThrownBy(() -> createUseCase.execute(input))
                .isInstanceOf(StateMachineAlreadyExistsException.class);
    }

    @Test
    void update_shouldReplaceTopologyWhenFound() {
        StateMachineDefinition existing = sampleDefinition("Old", null, "state", "draft");
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StateMachineDefinition update = sampleDefinition("New", "new desc", "status", "draft");
        StateMachineDefinition updated = updateUseCase.execute(ORG, ENTITY, update);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getDescription()).isEqualTo("new desc");
        assertThat(updated.getStateAttributeKey()).isEqualTo("status");
        verify(validator).validate("draft", states, transitions);
    }

    @Test
    void update_shouldThrowWhenNotFound() {
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.empty());

        StateMachineDefinition update = sampleDefinition("New", null, "state", "draft");
        assertThatThrownBy(() -> updateUseCase.execute(ORG, ENTITY, update))
                .isInstanceOf(StateMachineNotFoundException.class);
    }

    @Test
    void delete_shouldDeleteByIdWhenExists() {
        when(repository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(true);

        deleteUseCase.execute(ORG, ENTITY);

        verify(repository).deleteById(new StateMachineDefinitionKey(ORG, ENTITY));
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(repository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(false);

        assertThatThrownBy(() -> deleteUseCase.execute(ORG, ENTITY))
                .isInstanceOf(StateMachineNotFoundException.class);
    }

    @Test
    void find_shouldReturnWhenExists() {
        StateMachineDefinition existing = sampleDefinition("SM", null, "state", "draft");
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
        StateMachineDefinition existing = sampleDefinition("SM", null, "state", "draft");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existing)));

        Page<StateMachineDefinition> page = findAllUseCase.execute(ORG, null, null, 0, 10);
        assertThat(page.getContent()).containsExactly(existing);

        Page<StateMachineDefinition> page2 = findAllUseCase.execute(ORG, "name=='SM'", "name,desc", null, null);
        assertThat(page2.getContent()).containsExactly(existing);
    }

    private StateMachineDefinition sampleDefinition(String name, String description, String stateAttr, String initial) {
        return StateMachineDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .name(name)
                .description(description)
                .stateAttributeKey(stateAttr)
                .initialStateKey(initial)
                .states(states)
                .transitions(transitions)
                .build();
    }
}
