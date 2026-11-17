package com.processpuzzle.baseentity.instances.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.inbound.SearchEntityInstancesUseCase;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort.SharingView;
import com.processpuzzle.baseentity.instances.usecases.outbound.RsqlToInstanceSpecificationPort;
import com.processpuzzle.core.definition.RecordStates;
import com.processpuzzle.core.definition.RecordStates.StateTracking;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** A public shared type limited by visibleWhen: other members see a record only in the listed states of its machine. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class VisibleWhenTest {

    private static final String COMMON = "sailing-common";
    private static final String CLUB = "club-a";
    private static final String OTHER = "club-b";

    @Mock
    private EntityObjectRepository repository;
    @Mock
    private EntityDefinitionLookupPort definitions;
    @Mock
    private ObjectProvider<RecordStates> stateProvider;
    @Mock
    private RecordStates recordStates;
    @Mock
    private RsqlToInstanceSpecificationPort rsql;
    @Mock
    private Root<EntityObject> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path path;
    @Mock
    private Expression held;
    @Mock
    private Expression state;

    private EntityObjectScope scope;

    @BeforeEach
    void setUp() {
        scope = new EntityObjectScope(repository, definitions, stateProvider);
        lenient().when(definitions.sharedFrom(OTHER, "race")).thenReturn(Optional.of(COMMON));
        lenient().when(definitions.sharingOf(OTHER, "race"))
            .thenReturn(Optional.of(new SharingView("OWNER_ORG", "PUBLIC", List.of("PUBLISHED", "COMPLETED"))));
        lenient().when(stateProvider.getIfAvailable()).thenReturn(recordStates);
        lenient().when(recordStates.trackingOf(COMMON, "race")).thenReturn(Optional.of(new StateTracking("status", "DRAFT")));
    }

    private EntityObject race(String owner, Map<String, Object> payload) {
        return EntityObject.builder().id(UUID.randomUUID()).orgKey(COMMON).ownerOrgKey(owner).entityDefinitionCode("race").payload(payload).build();
    }

    @Test
    void otherMembersSeeARecordOnlyInAListedState() {
        assertThat(scope.isVisibleTo(OTHER, "race", race(CLUB, Map.of("status", "PUBLISHED")))).isTrue();
        assertThat(scope.isVisibleTo(OTHER, "race", race(CLUB, Map.of("status", "IN_PROGRESS")))).isFalse();
    }

    @Test
    void aRecordHoldingNoStateIsInTheInitialOne() {
        assertThat(scope.isVisibleTo(OTHER, "race", race(CLUB, Map.of()))).as("DRAFT is not listed").isFalse();
    }

    @Test
    void theOwnerAndTheCommonSeeARecordInEveryState() {
        EntityObject draft = race(OTHER, Map.of("status", "DRAFT"));

        assertThat(scope.isVisibleTo(OTHER, "race", draft)).isTrue();
        assertThat(scope.isVisibleTo(COMMON, "race", race(CLUB, Map.of("status", "DRAFT")))).isTrue();
    }

    @Test
    void withoutAStateMachineOnlyTheOwnerSeesARecord() {
        when(recordStates.trackingOf(COMMON, "race")).thenReturn(Optional.empty());

        assertThat(scope.isVisibleTo(OTHER, "race", race(CLUB, Map.of("status", "PUBLISHED")))).isFalse();
        assertThat(scope.listRestriction(OTHER, "race")).hasValueSatisfying(restriction -> assertThat(restriction.ownOnly()).isTrue());
    }

    @Test
    void withoutStateSupportInTheHostOnlyTheOwnerSeesARecord() {
        when(stateProvider.getIfAvailable()).thenReturn(null);

        assertThat(scope.isVisibleTo(OTHER, "race", race(CLUB, Map.of("status", "PUBLISHED")))).isFalse();
    }

    @Test
    void aSearchAddsTheOwnRecordsOrTheVisibleStates() {
        SearchEntityInstancesUseCase search = new SearchEntityInstancesUseCase(repository, scope, rsql);
        when(rsql.toSpecification(any(), any(), any())).thenReturn((r, q, c) -> null);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        lenient().when(root.get(any(String.class))).thenReturn(path);
        when(cb.function(eq("jsonb_extract_path_text"), eq(String.class), any(), any())).thenReturn(held);
        when(cb.coalesce(held, "DRAFT")).thenReturn(state);

        search.search(OTHER, "race", null, PageRequest.of(0, 10), false);
        ArgumentCaptor<Specification<EntityObject>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        captor.getValue().toPredicate(root, query, cb);

        verify(state).in(List.of("PUBLISHED", "COMPLETED"));
        verify(cb).or(any(), any());
    }
}
