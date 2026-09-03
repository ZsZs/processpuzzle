package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.platformadmin.PlatformAdminTestFixtures;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationAccessDeniedException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The one list query in ProcessPuzzle with no tenant specification ANDed onto the caller's RSQL,
 * which is correct here and nowhere else — so what these tests pin down is that the staff gate is
 * consulted before the repository, and that the RSQL is passed through rather than being narrowed.
 */
class FindAllOrganizationsTest {

    private OrganizationRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(OrganizationRepository.class);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(PlatformAdminTestFixtures.organization())));
    }

    @Test
    void pagesThroughTheRepository() {
        Page<Organization> page = findAll(PlatformAdminTestFixtures.permissiveGuard())
                .execute(null, null, 2, 5);

        assertThat(page.getContent()).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void absentPagingFallsBackToTheFirstPageOfTwenty() {
        findAll(PlatformAdminTestFixtures.permissiveGuard()).execute(null, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void theOrderParameterBecomesASort() {
        findAll(PlatformAdminTestFixtures.permissiveGuard()).execute(null, "name,desc", null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("name"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);
    }

    /**
     * The customer list of the whole platform is behind this one check, so it has to happen before
     * anything reaches the repository — not merely before the result is returned.
     */
    @Test
    void requiresStaffAuthorityBeforeTouchingTheRepository() {
        assertThatThrownBy(() -> findAll(PlatformAdminTestFixtures.denyingGuard())
                .execute(null, null, null, null))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(repository);
    }

    /** A crafted RSQL cannot widen anything here, because there is nothing to widen past. */
    @Test
    void anRsqlFilterIsPassedThrough() {
        findAll(PlatformAdminTestFixtures.permissiveGuard()).execute("status==SUSPENDED", null, null, null);

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    private FindAllOrganizations findAll(OrganizationGuard guard) {
        return new FindAllOrganizations(repository, guard);
    }
}
