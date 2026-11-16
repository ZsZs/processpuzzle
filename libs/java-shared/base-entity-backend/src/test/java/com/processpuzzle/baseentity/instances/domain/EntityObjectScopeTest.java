package com.processpuzzle.baseentity.instances.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.processpuzzle.baseentity.common.ForbiddenException;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityObjectScopeTest {

    private static final String ORG = "acme";
    private static final String OTHER_ORG = "globex";

    @Mock
    private EntityObjectRepository repository;

    @Mock
    private EntityDefinitionLookupPort definitions;

    private EntityObjectScope scope;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scope = new EntityObjectScope(repository, definitions);
    }

    private EntityObject object(String orgKey, String code) {
        return EntityObject.builder().id(id).orgKey(orgKey).entityDefinitionCode(code).version(0L).build();
    }

    /** A tenant type is stored under the request's own organization. */
    @Test
    void storageOrgKey_isTheRequestOrganization() {
        assertThat(scope.storageOrgKey(ORG, "order")).isEqualTo(ORG);
        assertThat(scope.storageOrgKey(OTHER_ORG, "order")).isEqualTo(OTHER_ORG);
    }

    @Test
    void find_returnsTheOrganizationsObjectOfThatType() {
        EntityObject order = object(ORG, "order");
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(order));

        assertThat(scope.find(ORG, "order", id)).containsSame(order);
    }

    /** The repository is asked within the request's organization, so another org's object never comes back. */
    @Test
    void find_hidesAnotherOrganizationsObject() {
        when(repository.findByIdAndOrgKey(id, OTHER_ORG)).thenReturn(Optional.empty());

        assertThat(scope.find(OTHER_ORG, "order", id)).isEmpty();
        verify(repository).findByIdAndOrgKey(id, OTHER_ORG);
    }

    @Test
    void find_hidesAnObjectOfAnotherType() {
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(object(ORG, "order")));

        assertThat(scope.find(ORG, "partner", id)).isEmpty();
    }

    @Test
    void findAll_queriesByTheScopedOrganization() {
        List<EntityObject> orders = List.of(object(ORG, "order"));
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "order")).thenReturn(orders);

        assertThat(scope.findAll(ORG, "order")).containsExactlyElementsOf(orders);
    }

    /** Only an object whose payload the rewrite changes is written. */
    @Test
    void rewriteAll_writesTheObjectsItChanges_only() {
        EntityObject open = object(ORG, "order");
        open.setPayload(new LinkedHashMap<>(Map.of("status", "OPEN")));
        EntityObject done = EntityObject.builder().id(UUID.randomUUID()).orgKey(ORG).entityDefinitionCode("order")
            .version(0L).payload(new LinkedHashMap<>(Map.of("status", "DONE"))).build();
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "order")).thenReturn(List.of(open, done));

        long written = scope.rewriteAll(ORG, "order", payload -> {
            payload.replace("status", "OPEN", "SUBMITTED");
            return payload;
        });

        assertThat(written).isEqualTo(1);
        assertThat(open.getPayload()).containsEntry("status", "SUBMITTED");
        verify(repository).save(open);
        verify(repository, never()).save(done);
    }

    @Test
    void existsAny_queriesByTheScopedOrganization() {
        when(repository.existsByOrgKeyAndEntityDefinitionCode(ORG, "order")).thenReturn(true);
        when(repository.existsByOrgKeyAndEntityDefinitionCode(OTHER_ORG, "order")).thenReturn(false);

        assertThat(scope.existsAny(ORG, "order")).isTrue();
        assertThat(scope.existsAny(OTHER_ORG, "order")).isFalse();
    }

    @Test
    void storageOrgKey_ofASharedType_isTheCommon() {
        when(definitions.sharedFrom(ORG, "asset")).thenReturn(Optional.of("starter-common"));

        assertThat(scope.storageOrgKey(ORG, "asset")).isEqualTo("starter-common");
    }

    @Test
    void find_ofASharedType_readsTheCommonsObject() {
        when(definitions.sharedFrom(ORG, "asset")).thenReturn(Optional.of("starter-common"));
        EntityObject asset = object("starter-common", "asset");
        when(repository.findByIdAndOrgKey(id, "starter-common")).thenReturn(Optional.of(asset));

        assertThat(scope.find(ORG, "asset", id)).contains(asset);
    }

    @Test
    void requireWritable_refusesAMemberWritingASharedType() {
        when(definitions.sharedFrom(ORG, "asset")).thenReturn(Optional.of("starter-common"));

        assertThatThrownBy(() -> scope.requireWritable(ORG, "asset")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireWritable_letsTheCommonWriteItsOwnType() {
        when(definitions.sharedFrom("starter-common", "asset")).thenReturn(Optional.empty());

        assertThat(scope.requireWritable("starter-common", "asset")).isEqualTo("starter-common");
    }

    /** A retired object keeps resolving by id, but the lists leave it out. */
    @Test
    void findAll_leavesRetiredObjectsOut_andFindStillResolvesThem() {
        EntityObject live = object(ORG, "order");
        EntityObject retired = object(ORG, "order");
        retired.setRetiredAt(java.time.Instant.now());
        when(repository.findAllByOrgKeyAndEntityDefinitionCode(ORG, "order")).thenReturn(List.of(live, retired));
        when(repository.findByIdAndOrgKey(id, ORG)).thenReturn(Optional.of(retired));

        assertThat(scope.findAll(ORG, "order")).containsExactly(live);
        assertThat(scope.find(ORG, "order", id)).containsSame(retired);
    }

    @Test
    void referringOrgKeys_areTheOrganizationAndItsCommon() {
        when(definitions.sharedFrom(ORG, "asset")).thenReturn(Optional.of("common"));

        assertThat(scope.referringOrgKeys(ORG, "asset")).containsExactly(ORG, "common");
        assertThat(scope.referringOrgKeys(ORG, "order")).containsExactly(ORG);
    }
}
