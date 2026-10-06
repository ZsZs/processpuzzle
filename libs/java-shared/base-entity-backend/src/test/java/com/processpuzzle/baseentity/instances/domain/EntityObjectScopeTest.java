package com.processpuzzle.baseentity.instances.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityObjectScopeTest {

    private static final String ORG = "acme";
    private static final String OTHER_ORG = "globex";

    @Mock
    private EntityObjectRepository repository;

    private EntityObjectScope scope;

    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        scope = new EntityObjectScope(repository);
    }

    private EntityObject object(String orgKey, String code) {
        return EntityObject.builder().id(id).orgKey(orgKey).entityDefinitionCode(code).version(0L).build();
    }

    /** Every entity type is a tenant type today; a global type will answer the reserved key instead. */
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

        assertThat(scope.findAll(ORG, "order")).isSameAs(orders);
    }

    @Test
    void existsAny_queriesByTheScopedOrganization() {
        when(repository.existsByOrgKeyAndEntityDefinitionCode(ORG, "order")).thenReturn(true);
        when(repository.existsByOrgKeyAndEntityDefinitionCode(OTHER_ORG, "order")).thenReturn(false);

        assertThat(scope.existsAny(ORG, "order")).isTrue();
        assertThat(scope.existsAny(OTHER_ORG, "order")).isFalse();
    }
}
