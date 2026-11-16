package com.processpuzzle.baseentity.instances.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/** Deleting a record of a shared type retires it; the common's members reinstate it; a member does neither. */
@ExtendWith(MockitoExtension.class)
class RetireSharedObjectsTest {

    private static final String COMMON = "sailing-common";
    private static final String MEMBER = "club";

    @Mock
    private EntityObjectRepository repository;
    @Mock
    private EntityDefinitionLookupPort definitions;
    @Mock
    private ApplicationEventPublisher events;

    private DeleteEntityInstanceUseCase delete;
    private ReinstateEntityInstanceUseCase reinstate;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        EntityObjectScope scope = new EntityObjectScope(repository, definitions);
        delete = new DeleteEntityInstanceUseCase(repository, scope, events, () -> Optional.of("staff"));
        reinstate = new ReinstateEntityInstanceUseCase(repository, scope, events);
    }

    private EntityObject sharedObject() {
        return EntityObject.builder().id(id).orgKey(COMMON).entityDefinitionCode("asset").build();
    }

    private void commonOwnsAsset() {
        when(definitions.isShared(COMMON, "asset")).thenReturn(true);
        when(repository.findByIdAndOrgKey(id, COMMON)).thenReturn(Optional.of(sharedObject()));
    }

    @Test
    void deleteRetiresTheObjectInsteadOfDeletingIt() {
        commonOwnsAsset();

        delete.delete(COMMON, "asset", id, false);

        verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(
            (EntityObject saved) -> saved.isRetired() && "staff".equals(saved.getRetiredBy())));
        verify(repository, never()).delete(any(EntityObject.class));
        verify(repository, never()).existsAnyReferenceTo(any(), any());
    }

    @Test
    void deletingARetiredObjectAgainChangesNothing() {
        EntityObject retired = sharedObject();
        retired.setRetiredAt(Instant.parse("2026-11-01T00:00:00Z"));
        when(definitions.isShared(COMMON, "asset")).thenReturn(true);
        when(repository.findByIdAndOrgKey(id, COMMON)).thenReturn(Optional.of(retired));

        delete.delete(COMMON, "asset", id, false);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void aMemberCannotRetireWhatItDoesNotOwn() {
        when(definitions.sharedFrom(MEMBER, "asset")).thenReturn(Optional.of(COMMON));

        assertThatThrownBy(() -> delete.delete(MEMBER, "asset", id, false)).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void aTenantTypeIsStillDeletedAndTheGuardLooksAtTheOrganizationAndItsCommonOnly() {
        EntityObject own = EntityObject.builder().id(id).orgKey(MEMBER).entityDefinitionCode("profile").build();
        when(repository.findByIdAndOrgKey(id, MEMBER)).thenReturn(Optional.of(own));
        when(repository.existsAnyReferenceTo(id.toString(), List.of(MEMBER))).thenReturn(false);

        delete.delete(MEMBER, "profile", id, false);

        verify(repository).delete(own);
    }

    @Test
    void reinstateListsTheObjectAgain() {
        EntityObject retired = sharedObject();
        retired.setRetiredAt(Instant.now());
        retired.setRetiredBy("staff");
        when(repository.findByIdAndOrgKey(id, COMMON)).thenReturn(Optional.of(retired));
        when(repository.saveAndFlush(retired)).thenReturn(retired);

        EntityObject result = reinstate.reinstate(COMMON, "asset", id);

        assertThat(result.isRetired()).isFalse();
        assertThat(result.getRetiredBy()).isNull();
    }

    @Test
    void reinstatingAnObjectThatIsNotRetiredChangesNothing() {
        when(repository.findByIdAndOrgKey(id, COMMON)).thenReturn(Optional.of(sharedObject()));

        reinstate.reinstate(COMMON, "asset", id);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void aMemberCannotReinstate() {
        when(definitions.sharedFrom(MEMBER, "asset")).thenReturn(Optional.of(COMMON));

        assertThatThrownBy(() -> reinstate.reinstate(MEMBER, "asset", id)).isInstanceOf(ForbiddenException.class);
    }
}
