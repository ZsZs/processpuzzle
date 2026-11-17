package com.processpuzzle.baseentity.instances.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.ForbiddenException;
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

/** A shared type whose records follow another record: a booking follows its race, in owner and in visibility. */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class FollowedRecordTest {

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

    private EntityObjectScope scope;
    private final UUID raceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scope = new EntityObjectScope(repository, definitions, stateProvider);
        for (String org : List.of(CLUB, OTHER)) {
            lenient().when(definitions.sharedFrom(org, "entry")).thenReturn(Optional.of(COMMON));
            lenient().when(definitions.sharedFrom(org, "race")).thenReturn(Optional.of(COMMON));
            lenient().when(definitions.sharingOf(org, "entry"))
                .thenReturn(Optional.of(new SharingView("OWNER_ORG", "PUBLIC", List.of(), "race", "race")));
            lenient().when(definitions.sharingOf(org, "race"))
                .thenReturn(Optional.of(new SharingView("OWNER_ORG", "PUBLIC", List.of("PUBLISHED"))));
        }
        lenient().when(definitions.sharingOf(COMMON, "entry"))
            .thenReturn(Optional.of(new SharingView("OWNER_ORG", "PUBLIC", List.of(), "race", "race")));
        lenient().when(stateProvider.getIfAvailable()).thenReturn(recordStates);
        lenient().when(recordStates.trackingOf(COMMON, "race")).thenReturn(Optional.of(new StateTracking("status", "DRAFT")));
    }

    private EntityObject race(String status) {
        return EntityObject.builder().id(raceId).orgKey(COMMON).ownerOrgKey(CLUB).entityDefinitionCode("race")
            .payload(Map.of("status", status)).build();
    }

    private EntityObject entry(String owner) {
        return EntityObject.builder().id(UUID.randomUUID()).orgKey(COMMON).ownerOrgKey(owner).entityDefinitionCode("entry")
            .payload(Map.of("race", raceId.toString())).build();
    }

    @Test
    void anEntryIsOwnedByTheOwnerOfItsRace_andOnlyThatOwnerOrTheCommonAddsOne() {
        when(repository.findByIdAndOrgKey(raceId, COMMON)).thenReturn(Optional.of(race("PUBLISHED")));
        Map<String, Object> payload = Map.of("race", raceId.toString());

        assertThat(scope.ownerForNew(CLUB, "entry", payload)).contains(CLUB);
        assertThat(scope.ownerForNew(COMMON, "entry", payload)).as("the common adds it on the owner's behalf").contains(CLUB);
        assertThatThrownBy(() -> scope.ownerForNew(OTHER, "entry", payload)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anEntryMustNameAnExistingRaceOfTheRightType() {
        Map<String, Object> unknown = Map.of("race", UUID.randomUUID().toString());
        Map<String, Object> notAnId = Map.of("race", "nope");
        Map<String, Object> missing = Map.of();

        assertThatThrownBy(() -> scope.ownerForNew(CLUB, "entry", unknown)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> scope.ownerForNew(CLUB, "entry", notAnId)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> scope.ownerForNew(CLUB, "entry", missing)).isInstanceOf(ConflictException.class);
    }

    @Test
    void theRaceAnEntryFollowsCannotChange() {
        Map<String, Object> stored = Map.of("race", raceId.toString());
        Map<String, Object> moved = Map.of("race", UUID.randomUUID().toString());

        scope.requireFollowedUnchanged(CLUB, "entry", stored, stored);
        assertThatThrownBy(() -> scope.requireFollowedUnchanged(CLUB, "entry", stored, moved)).isInstanceOf(ConflictException.class);
    }

    @Test
    void anEntryIsVisibleAsItsRaceIs() {
        when(repository.findByIdAndOrgKey(raceId, COMMON)).thenReturn(Optional.of(race("PUBLISHED")));
        assertThat(scope.isVisibleTo(OTHER, "entry", entry(CLUB))).isTrue();

        when(repository.findByIdAndOrgKey(raceId, COMMON)).thenReturn(Optional.of(race("DRAFT")));
        assertThat(scope.isVisibleTo(OTHER, "entry", entry(CLUB))).as("its race is still a draft").isFalse();
        assertThat(scope.isVisibleTo(CLUB, "entry", entry(CLUB))).as("the owner").isTrue();
        assertThat(scope.isVisibleTo(COMMON, "entry", entry(CLUB))).as("the common").isTrue();
    }

    @Test
    void anEntryWhoseRaceIsGoneIsHiddenFromOthers() {
        when(repository.findByIdAndOrgKey(raceId, COMMON)).thenReturn(Optional.empty());

        assertThat(scope.isVisibleTo(OTHER, "entry", entry(CLUB))).isFalse();
    }

    @Test
    void aListOfEntriesNamesTheVisibleRaces() {
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(COMMON, "race")).thenReturn(List.of(race("PUBLISHED")));

        assertThat(scope.listRestriction(OTHER, "entry")).hasValueSatisfying(restriction -> {
            assertThat(restriction.followed()).isEqualTo("race");
            assertThat(restriction.followedIds()).containsExactly(raceId.toString());
        });
        assertThat(scope.listRestriction(COMMON, "entry")).isEmpty();
    }

    @Test
    void aSearchAddsTheOwnEntriesOrThoseOfAVisibleRace() {
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(COMMON, "race")).thenReturn(List.of(race("PUBLISHED")));
        SearchEntityInstancesUseCase search = new SearchEntityInstancesUseCase(repository, scope, rsql);
        when(rsql.toSpecification(any(), any(), any())).thenReturn((r, q, c) -> null);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        lenient().when(root.get(any(String.class))).thenReturn(mock(Path.class));
        Expression followedId = mock(Expression.class);
        when(cb.function(eq("jsonb_extract_path_text"), eq(String.class), any(), any())).thenReturn(followedId);

        search.search(OTHER, "entry", null, PageRequest.of(0, 10), false);
        ArgumentCaptor<Specification<EntityObject>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        captor.getValue().toPredicate(root, query, cb);

        verify(followedId).in(List.of(raceId.toString()));
        verify(cb).or(any(), any());
    }

    @Test
    void aSearchWithNoVisibleRaceListsOnlyTheOwnEntries() {
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(COMMON, "race")).thenReturn(List.of(race("DRAFT")));
        SearchEntityInstancesUseCase search = new SearchEntityInstancesUseCase(repository, scope, rsql);
        when(rsql.toSpecification(any(), any(), any())).thenReturn((r, q, c) -> null);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        lenient().when(root.get(any(String.class))).thenReturn(mock(Path.class));

        search.search(OTHER, "entry", null, PageRequest.of(0, 10), false);
        ArgumentCaptor<Specification<EntityObject>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(Pageable.class));
        captor.getValue().toPredicate(root, query, cb);

        verify(cb).equal(any(), eq(OTHER));
    }
}
