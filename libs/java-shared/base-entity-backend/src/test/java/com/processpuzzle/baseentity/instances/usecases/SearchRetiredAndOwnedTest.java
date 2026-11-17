package com.processpuzzle.baseentity.instances.usecases;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.inbound.SearchEntityInstancesUseCase;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort.SharingView;
import com.processpuzzle.baseentity.instances.usecases.outbound.RsqlToInstanceSpecificationPort;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * What a search adds to the caller's filter for a shared type: retired records are left out unless the common's
 * members — or an owner, for its own — ask for them, and an owner-only type lists a member's own records. The
 * specification is evaluated against a mocked criteria builder, which records which restrictions it applied.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class SearchRetiredAndOwnedTest {

    private static final String COMMON = "sailing-common";
    private static final String CLUB = "club-a";

    @Mock
    private EntityObjectRepository repository;
    @Mock
    private EntityDefinitionLookupPort definitions;
    @Mock
    private RsqlToInstanceSpecificationPort rsql;
    @Mock
    private Root<EntityObject> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;

    private SearchEntityInstancesUseCase search;

    @BeforeEach
    void setUp() {
        search = new SearchEntityInstancesUseCase(repository, new EntityObjectScope(repository, definitions), rsql);
        when(rsql.toSpecification(any(), any(), any())).thenReturn((r, q, c) -> null);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        lenient().when(root.get(any(String.class))).thenReturn(mock(Path.class));
    }

    private void sharedInCommon(String visibility) {
        when(definitions.sharedFrom(CLUB, "boat")).thenReturn(Optional.of(COMMON));
        when(definitions.sharingOf(CLUB, "boat")).thenReturn(Optional.of(new SharingView("OWNER_ORG", visibility)));
    }

    private void run(String org, boolean includeRetired) {
        search.search(org, "boat", null, PageRequest.of(0, 10), includeRetired);
        ArgumentCaptor<Specification<EntityObject>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        captor.getValue().toPredicate(root, query, cb);
    }

    @Test
    void theCommonAsksForEveryRetiredRecord() {
        run(COMMON, true);

        verify(cb, atLeastOnce()).conjunction();
        verify(cb, never()).isNull(any());
        verify(cb, never()).or(any(), any());
    }

    @Test
    void anOwnerAsksForItsOwnRetiredRecordsToo() {
        sharedInCommon("PUBLIC");

        run(CLUB, true);

        verify(cb).or(any(), any());
        verify(cb).equal(any(), eq(CLUB));
    }

    @Test
    void anOwnerOnlyTypeListsAMembersOwnRecords() {
        sharedInCommon("OWNER_ONLY");

        run(CLUB, false);

        verify(cb).isNull(any());
        verify(cb, atLeastOnce()).equal(any(), eq(CLUB));
    }
}
