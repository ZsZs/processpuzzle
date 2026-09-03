package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.platformadmin.usecase.port.OrganizationAccessPolicy;
import com.processpuzzle.platformadmin.usecase.port.PermitAllOrganizationAccessPolicy;
import com.processpuzzle.platformadmin.usecase.OrganizationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Mirrors {@code FindAllRulesTest} in base-rule-backend. */
class FindAllAppDefinitionsTest {

    private AppDefinitionRepository repository;
    private FindAllAppDefinitions findAllAppDefinitions;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(AppDefinitionRepository.class);
        ObjectProvider<OrganizationAccessPolicy> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(new PermitAllOrganizationAccessPolicy());
        findAllAppDefinitions = new FindAllAppDefinitions(repository, new OrganizationGuard(provider));
    }

    @Test
    void alwaysQueriesWithASpecification_soTheTenantFilterCanNeverBeSkipped() {
        when(repository.findAll(specArg(), any(Pageable.class))).thenReturn(emptyPage());

        findAllAppDefinitions.execute("my-org", null, null, null, null);

        verify(repository).findAll(specCaptor().capture(), any(Pageable.class));
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    void defaultsToTheFirstPageOfTwentyUnsorted() {
        when(repository.findAll(specArg(), any(Pageable.class))).thenReturn(emptyPage());

        findAllAppDefinitions.execute("my-org", null, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(specArg(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    void pageAndSizeAreForwarded() {
        when(repository.findAll(specArg(), any(Pageable.class))).thenReturn(emptyPage());

        findAllAppDefinitions.execute("my-org", null, null, 3, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(specArg(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void orderIsParsedIntoTheSort() {
        when(repository.findAll(specArg(), any(Pageable.class))).thenReturn(emptyPage());

        findAllAppDefinitions.execute("my-org", null, "name,desc", null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(specArg(), pageable.capture());
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.desc("name"));
    }

    @Test
    void rsqlIsCombinedWithTheTenantSpecification() {
        when(repository.findAll(specArg(), any(Pageable.class))).thenReturn(emptyPage());

        findAllAppDefinitions.execute("my-org", "id==claims-app", null, null, null);

        ArgumentCaptor<Specification<AppDefinition>> spec = specCaptor();
        verify(repository).findAll(spec.capture(), any(Pageable.class));
        assertThat(spec.getValue()).isNotNull();
    }

    @Test
    void invalidRsqlBubblesAsIllegalArgumentBeforeAnyQuery() {
        assertThatThrownBy(() -> findAllAppDefinitions.execute("my-org", "id==", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid RSQL");

        verifyNoInteractions(repository);
    }

    private static Page<AppDefinition> emptyPage() {
        return new PageImpl<>(List.of());
    }

    @SuppressWarnings("unchecked")
    private static Specification<AppDefinition> specArg() {
        return any(Specification.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Specification<AppDefinition>> specCaptor() {
        return ArgumentCaptor.forClass(Specification.class);
    }
}
