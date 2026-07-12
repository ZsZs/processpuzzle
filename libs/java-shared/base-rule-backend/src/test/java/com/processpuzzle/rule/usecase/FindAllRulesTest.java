package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FindAllRulesTest {

    private RuleDefinitionRepository repository;
    private FindAllRules findAllRules;

    @BeforeEach
    void setUp() {
        repository = mock(RuleDefinitionRepository.class);
        findAllRules = new FindAllRules(repository);
    }

    @Test
    void allParamsNull_callsRepositoryWithoutSpecAndDefaultPageable() {
        when(repository.findAll(any(Pageable.class))).thenReturn(emptyPage());

        findAllRules.execute(null, null, null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageableCaptor.capture());
        verify(repository, never()).findAll((Specification<RuleDefinition>) any(), any(Pageable.class));
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().isSorted()).isFalse();
    }

    @Test
    void contextOnly_passesSpecAndDefaultPageable() {
        when(repository.findAll((Specification<RuleDefinition>) any(), any(Pageable.class))).thenReturn(emptyPage());

        findAllRules.execute("Order", null, null, null, null);

        ArgumentCaptor<Specification<RuleDefinition>> specCaptor = specCaptor();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(specCaptor.capture(), pageableCaptor.capture());
        assertThat(specCaptor.getValue()).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    void order_isParsedAndAppliedToPageable() {
        when(repository.findAll(any(Pageable.class))).thenReturn(emptyPage());

        findAllRules.execute(null, null, "name,desc", null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort()).containsExactly(Sort.Order.desc("name"));
    }

    @Test
    void pageAndSize_areForwardedToPageable() {
        when(repository.findAll(any(Pageable.class))).thenReturn(emptyPage());

        findAllRules.execute(null, null, null, 3, 5);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(5);
    }

    @Test
    void whereAndContext_areCombinedIntoSingleSpec() {
        when(repository.findAll((Specification<RuleDefinition>) any(), any(Pageable.class))).thenReturn(emptyPage());

        findAllRules.execute("Order", "enabled==true", null, null, null);

        ArgumentCaptor<Specification<RuleDefinition>> specCaptor = specCaptor();
        verify(repository).findAll(specCaptor.capture(), any(Pageable.class));
        assertThat(specCaptor.getValue()).isNotNull();
    }

    @Test
    void invalidWhere_bubblesAsIllegalArgument() {
        try {
            findAllRules.execute(null, "context==", null, null, null);
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageStartingWith("Invalid RSQL");
            verifyNoInteractions(repository);
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static Page<RuleDefinition> emptyPage() {
        return new PageImpl<>(List.of());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Specification<RuleDefinition>> specCaptor() {
        return ArgumentCaptor.forClass(Specification.class);
    }
}
