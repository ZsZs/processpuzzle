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

/** Mirrors {@code FindAllAppDefinitionsTest} in base-app-backend. */
class FindAllRulesTest {

    private RuleDefinitionRepository repository;
    private FindAllRules findAllRules;

    @BeforeEach
    void setUp() {
        repository = mock(RuleDefinitionRepository.class);
        findAllRules = new FindAllRules(repository);
    }

    @Test
    void alwaysQueriesWithASpecification_soTheTenantFilterCanNeverBeSkipped() {
        when(repository.findAll(specArg(), any(Pageable.class))).thenReturn(emptyPage());

        findAllRules.execute("demo", null, null, null, null, null);

        verify(repository).findAll(specCaptor().capture(), any(Pageable.class));
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    void defaultsToTheFirstPageOfTwentyUnsorted() {
        when(repository.findAll(specArg(), any(Pageable.class))).thenReturn(emptyPage());

        findAllRules.execute("demo", null, null, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(specArg(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    void contextIsCombinedWithTheTenantSpecification() {
        when(repository.findAll(specArg(), any(Pageable.class))).thenReturn(emptyPage());

        findAllRules.execute("demo", "Order", null, null, null, null);

        ArgumentCaptor<Specification<RuleDefinition>> spec = specCaptor();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(spec.capture(), pageable.capture());
        assertThat(spec.getValue()).isNotNull();
        assertThat(pageable.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    void orderIsParsedIntoTheSort() {
        when(repository.findAll(specArg(), any(Pageable.class))).thenReturn(emptyPage());

        findAllRules.execute("demo", null, null, "name,desc", null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(specArg(), pageable.capture());
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.desc("name"));
    }

    @Test
    void pageAndSizeAreForwarded() {
        when(repository.findAll(specArg(), any(Pageable.class))).thenReturn(emptyPage());

        findAllRules.execute("demo", null, null, null, 3, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(specArg(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void whereAndContextAreCombinedIntoSingleSpec() {
        when(repository.findAll(specArg(), any(Pageable.class))).thenReturn(emptyPage());

        findAllRules.execute("demo", "Order", "enabled==true", null, null, null);

        ArgumentCaptor<Specification<RuleDefinition>> spec = specCaptor();
        verify(repository).findAll(spec.capture(), any(Pageable.class));
        assertThat(spec.getValue()).isNotNull();
    }

    @Test
    void invalidWhereBubblesAsIllegalArgumentBeforeAnyQuery() {
        try {
            findAllRules.execute("demo", null, "context==", null, null, null);
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
    private static Specification<RuleDefinition> specArg() {
        return any(Specification.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Specification<RuleDefinition>> specCaptor() {
        return ArgumentCaptor.forClass(Specification.class);
    }
}
