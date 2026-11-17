package com.processpuzzle.baseentity.instances.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.baseentity.common.ForbiddenException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.inbound.DeleteEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.ReinstateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort.SharingView;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** A shared type its owners write: the member that created a record changes it, other members only read it. */
@ExtendWith(MockitoExtension.class)
class OwnerWritesTest {

    private static final String COMMON = "sailing-common";
    private static final String CLUB = "club-a";
    private static final String OTHER = "club-b";

    @Mock
    private EntityObjectRepository repository;
    @Mock
    private EntityDefinitionLookupPort definitions;
    @Mock
    private ApplicationEventPublisher events;

    private EntityObjectScope scope;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scope = new EntityObjectScope(repository, definitions);
    }

    private void sharedAs(String org, String write, String visibility) {
        lenient().when(definitions.sharedFrom(org, "boat")).thenReturn(Optional.of(COMMON));
        lenient().when(definitions.sharingOf(org, "boat")).thenReturn(Optional.of(new SharingView(write, visibility)));
    }

    private EntityObject boatOwnedBy(String owner) {
        return EntityObject.builder().id(id).orgKey(COMMON).ownerOrgKey(owner).entityDefinitionCode("boat").build();
    }

    @Test
    void aMemberMayWriteATypeItsOwnersWrite_butNotOneTheCommonAdministers() {
        sharedAs(CLUB, "OWNER_ORG", "PUBLIC");
        assertThat(scope.requireWritable(CLUB, "boat")).isEqualTo(COMMON);

        sharedAs(OTHER, "COMMON_ADMIN", "PUBLIC");
        assertThatThrownBy(() -> scope.requireWritable(OTHER, "boat")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aNewObjectIsOwnedByTheMemberThatCreatedIt_andByNoOneWhenTheCommonDoes() {
        sharedAs(CLUB, "OWNER_ORG", "PUBLIC");

        assertThat(scope.ownerForNew(CLUB, "boat")).contains(CLUB);
        assertThat(scope.ownerForNew(COMMON, "boat")).isEmpty();
    }

    @Test
    void aMemberChangesItsOwnObjectsOnly_andTheCommonChangesAny() {
        sharedAs(CLUB, "OWNER_ORG", "PUBLIC");

        scope.requireOwnedBy(CLUB, "boat", boatOwnedBy(CLUB));
        assertThatThrownBy(() -> scope.requireOwnedBy(CLUB, "boat", boatOwnedBy(OTHER))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> scope.requireOwnedBy(CLUB, "boat", boatOwnedBy(null))).isInstanceOf(ForbiddenException.class);
        scope.requireOwnedBy(COMMON, "boat", boatOwnedBy(OTHER));
    }

    @Test
    void anOwnerOnlyTypeIsVisibleToItsOwnerAndTheCommonOnly() {
        sharedAs(CLUB, "OWNER_ORG", "OWNER_ONLY");
        sharedAs(OTHER, "OWNER_ORG", "OWNER_ONLY");
        when(repository.findByIdAndOrgKey(id, COMMON)).thenReturn(Optional.of(boatOwnedBy(CLUB)));

        assertThat(scope.find(CLUB, "boat", id)).isPresent();
        assertThat(scope.find(OTHER, "boat", id)).isEmpty();
        assertThat(scope.find(COMMON, "boat", id)).isPresent();
        assertThat(scope.listsOwnOnly(OTHER, "boat")).isTrue();
        assertThat(scope.listsOwnOnly(COMMON, "boat")).isFalse();
    }

    @Test
    void aPublicTypeIsVisibleToEveryMember() {
        sharedAs(OTHER, "OWNER_ORG", "PUBLIC");
        when(repository.findByIdAndOrgKey(id, COMMON)).thenReturn(Optional.of(boatOwnedBy(CLUB)));

        assertThat(scope.find(OTHER, "boat", id)).isPresent();
        assertThat(scope.listsOwnOnly(OTHER, "boat")).isFalse();
    }

    @Test
    void anOwnerRetiresItsObject_butNotAnotherMembers() {
        sharedAs(CLUB, "OWNER_ORG", "PUBLIC");
        when(definitions.isShared(CLUB, "boat")).thenReturn(true);
        DeleteEntityInstanceUseCase delete = new DeleteEntityInstanceUseCase(repository, scope, events, () -> Optional.of("club"));

        when(repository.findByIdAndOrgKey(id, COMMON)).thenReturn(Optional.of(boatOwnedBy(CLUB)));
        delete.delete(CLUB, "boat", id, false);
        verify(repository).saveAndFlush(any(EntityObject.class));

        when(repository.findByIdAndOrgKey(id, COMMON)).thenReturn(Optional.of(boatOwnedBy(OTHER)));
        assertThatThrownBy(() -> delete.delete(CLUB, "boat", id, false)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anOwnerReinstatesItsObject_butNotAnotherMembers() {
        sharedAs(CLUB, "OWNER_ORG", "PUBLIC");
        ReinstateEntityInstanceUseCase reinstate = new ReinstateEntityInstanceUseCase(repository, scope, events);
        when(repository.findByIdAndOrgKey(id, COMMON)).thenReturn(Optional.of(boatOwnedBy(OTHER)));

        assertThatThrownBy(() -> reinstate.reinstate(CLUB, "boat", id)).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
