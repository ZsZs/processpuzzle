package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.DiagramDefinition;
import com.processpuzzle.state.domain.DiagramDefinitionKey;
import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.domain.DiagramViewport;
import com.processpuzzle.state.domain.NodeLayout;
import com.processpuzzle.state.domain.Point;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.exception.DiagramDefinitionNotFoundException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagramDefinitionUseCasesTest {

    private static final String ORG = "org-1";
    private static final String ENTITY = "invoice";

    private DiagramDefinitionRepository repository;
    private StateMachineDefinitionRepository stateMachineRepository;

    private SaveDiagramDefinition saveUseCase;
    private FindDiagramDefinition findUseCase;
    private FindAllDiagramDefinitions findAllUseCase;
    private DeleteDiagramDefinition deleteUseCase;

    @BeforeEach
    void setUp() {
        repository = mock(DiagramDefinitionRepository.class);
        stateMachineRepository = mock(StateMachineDefinitionRepository.class);

        saveUseCase = new SaveDiagramDefinition(repository, stateMachineRepository);
        findUseCase = new FindDiagramDefinition(repository);
        findAllUseCase = new FindAllDiagramDefinitions(repository);
        deleteUseCase = new DeleteDiagramDefinition(repository);
    }

    private static DiagramDefinition layout(String stateKey) {
        return DiagramDefinition.builder()
                .orgKey(ORG)
                .entityName(ENTITY)
                .nodes(List.of(new NodeLayout(stateKey, new Point(10, 20), null)))
                .viewport(new DiagramViewport(0, 0, 1))
                .build();
    }

    // ── save ───────────────────────────────────────────────────

    @Test
    void save_shouldInsertAndReportCreatedWhenNoLayoutExists() {
        DiagramDefinition incoming = layout("draft");
        when(stateMachineRepository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(true);
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.empty());
        when(repository.save(incoming)).thenReturn(incoming);

        SaveDiagramDefinition.Result result = saveUseCase.execute(incoming);

        assertThat(result.created()).isTrue();
        assertThat(result.definition()).isSameAs(incoming);
        verify(repository).save(incoming);
    }

    /**
     * The existing managed row is what gets saved, not the incoming detached one — that is what
     * keeps Hibernate's {@code @Version} column in play as the optimistic lock.
     */
    @Test
    void save_shouldReplaceTheExistingLayoutAndReportNotCreated() {
        DiagramDefinition existing = layout("draft");
        DiagramDefinition incoming = layout("approved");
        when(stateMachineRepository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(true);
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        SaveDiagramDefinition.Result result = saveUseCase.execute(incoming);

        assertThat(result.created()).isFalse();
        assertThat(result.definition()).isSameAs(existing);
        assertThat(existing.getNodes()).isEqualTo(incoming.getNodes());
        verify(repository).save(existing);
        verify(repository, never()).save(incoming);
    }

    @Test
    void save_shouldRejectALayoutForAnEntityWithNoStateMachine() {
        DiagramDefinition incoming = layout("draft");
        when(stateMachineRepository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(false);

        assertThatThrownBy(() -> saveUseCase.execute(incoming))
                .isInstanceOf(StateMachineNotFoundException.class);
        verify(repository, never()).save(any());
    }

    /**
     * A row naming a state the machine no longer declares is accepted on purpose — see
     * {@link SaveDiagramDefinition}'s javadoc for why validating it would make two independent,
     * individually-valid saves fail on ordering alone.
     */
    @Test
    void save_shouldAcceptALayoutRowForAnUndeclaredState() {
        DiagramDefinition incoming = layout("a-state-the-machine-dropped");
        when(stateMachineRepository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(true);
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.empty());
        when(repository.save(incoming)).thenReturn(incoming);

        assertThat(saveUseCase.execute(incoming).created()).isTrue();
    }

    // ── find ───────────────────────────────────────────────────

    @Test
    void find_shouldReturnTheLayoutWhenArranged() {
        DiagramDefinition existing = layout("draft");
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.of(existing));

        assertThat(findUseCase.execute(ORG, ENTITY)).isSameAs(existing);
    }

    @Test
    void find_shouldThrowWhenNeverArranged() {
        when(repository.findByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> findUseCase.execute(ORG, ENTITY))
                .isInstanceOf(DiagramDefinitionNotFoundException.class)
                .hasMessageContaining(ENTITY)
                .hasMessageContaining(ORG);
    }

    // ── find all ───────────────────────────────────────────────

    @Test
    void findAll_shouldPageWithTheSuppliedRequest() {
        Page<DiagramDefinition> page = new PageImpl<>(List.of(layout("draft")));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<DiagramDefinition> result = findAllUseCase.execute(ORG, null, "entityName,asc", 2, 5);

        assertThat(result).isSameAs(page);
        verify(repository).findAll(any(Specification.class), org.mockito.ArgumentMatchers
                .argThat((Pageable p) -> p.getPageNumber() == 2 && p.getPageSize() == 5));
    }

    @Test
    void findAll_shouldFallBackToTheDefaultPageAndSize() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        findAllUseCase.execute(ORG, null, null, null, null);

        verify(repository).findAll(any(Specification.class), org.mockito.ArgumentMatchers
                .argThat((Pageable p) -> p.getPageNumber() == 0 && p.getPageSize() == 20));
    }

    /** An RSQL filter must narrow the tenant-scoped query, never replace it. */
    @Test
    void findAll_shouldStillQueryWhenAnRsqlFilterIsGiven() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        findAllUseCase.execute(ORG, "entityName==invoice", null, 0, 20);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ── delete ─────────────────────────────────────────────────

    @Test
    void delete_shouldDeleteByIdWhenArranged() {
        when(repository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(true);

        deleteUseCase.execute(ORG, ENTITY);

        verify(repository).deleteById(new DiagramDefinitionKey(ORG, ENTITY));
    }

    @Test
    void delete_shouldThrowWhenNeverArranged() {
        when(repository.existsByOrgKeyAndEntityName(ORG, ENTITY)).thenReturn(false);

        assertThatThrownBy(() -> deleteUseCase.execute(ORG, ENTITY))
                .isInstanceOf(DiagramDefinitionNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }
}
