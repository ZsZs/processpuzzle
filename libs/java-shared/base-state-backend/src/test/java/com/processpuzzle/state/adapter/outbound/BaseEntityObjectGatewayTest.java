package com.processpuzzle.state.adapter.outbound;

import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.state.usecase.exception.EntityObjectNotFoundException;
import com.processpuzzle.state.usecase.exception.StaleEntityObjectVersionException;
import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BaseEntityObjectGatewayTest {

    private static final String ORG = "acme";
    private static final String ENTITY = "order";
    private static final UUID OBJECT_ID = UUID.randomUUID();

    @Mock
    private EntityObjectAccess entityObjectAccess;

    private BaseEntityObjectGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new BaseEntityObjectGateway(entityObjectAccess);
    }

    @Test
    void findObject_returnsASnapshotOfTheEntityObject() {
        when(entityObjectAccess.find(ENTITY, OBJECT_ID))
                .thenReturn(new EntityObjectView(OBJECT_ID, 7L, Map.of("status", "DRAFT")));

        EntityObjectSnapshot snapshot = gateway.findObject(ORG, ENTITY, OBJECT_ID);

        assertThat(snapshot.id()).isEqualTo(OBJECT_ID);
        assertThat(snapshot.version()).isEqualTo(7L);
        assertThat(snapshot.payload()).containsEntry("status", "DRAFT");
    }

    @Test
    void findObject_translatesNotFoundIntoThePortsOwnException() {
        when(entityObjectAccess.find(ENTITY, OBJECT_ID))
                .thenThrow(new EntityObjectAccessException.NotFound(ENTITY, OBJECT_ID));

        assertThatThrownBy(() -> gateway.findObject(ORG, ENTITY, OBJECT_ID))
                .isInstanceOf(EntityObjectNotFoundException.class);
    }

    @Test
    void updateStateAttribute_returnsThePostWriteVersion() {
        when(entityObjectAccess.updateAttribute(ENTITY, OBJECT_ID, "status", "CONFIRMED", 7L))
                .thenReturn(8L);

        assertThat(gateway.updateStateAttribute(ORG, ENTITY, OBJECT_ID, "status", "CONFIRMED", 7L))
                .isEqualTo(8L);
    }

    @Test
    void updateStateAttribute_translatesNotFoundIntoThePortsOwnException() {
        when(entityObjectAccess.updateAttribute(ENTITY, OBJECT_ID, "status", "CONFIRMED", 7L))
                .thenThrow(new EntityObjectAccessException.NotFound(ENTITY, OBJECT_ID));

        assertThatThrownBy(() -> gateway.updateStateAttribute(ORG, ENTITY, OBJECT_ID, "status", "CONFIRMED", 7L))
                .isInstanceOf(EntityObjectNotFoundException.class);
    }

    /**
     * The translation that matters most: base-entity's compare-and-swap failure has to arrive at
     * base-state as its own conflict type, because that is the one {@code StateApiExceptionHandler}
     * maps to 409. Leaking the base-entity exception would surface an optimistic-lock conflict as a
     * 500.
     */
    @Test
    void updateStateAttribute_translatesAVersionConflictIntoAStaleVersionException() {
        when(entityObjectAccess.updateAttribute(ENTITY, OBJECT_ID, "status", "CONFIRMED", 7L))
                .thenThrow(new EntityObjectAccessException.VersionConflict(OBJECT_ID, 7L, 9L));

        assertThatThrownBy(() -> gateway.updateStateAttribute(ORG, ENTITY, OBJECT_ID, "status", "CONFIRMED", 7L))
                .isInstanceOf(StaleEntityObjectVersionException.class);
    }
    @Test
    void findObjects_mapsEveryObjectOfTheTypeOntoASnapshot() {
        UUID otherId = UUID.randomUUID();
        when(entityObjectAccess.findAll(ENTITY)).thenReturn(List.of(
                new EntityObjectView(OBJECT_ID, 7L, Map.of("status", "DRAFT")),
                new EntityObjectView(otherId, 2L, Map.of("status", "SHIPPED"))));

        List<EntityObjectSnapshot> snapshots = gateway.findObjects(ORG, ENTITY);

        assertThat(snapshots).extracting(EntityObjectSnapshot::id).containsExactly(OBJECT_ID, otherId);
        assertThat(snapshots.getFirst().version()).isEqualTo(7L);
        assertThat(snapshots.getFirst().payload()).containsEntry("status", "DRAFT");
    }

    @Test
    void findObjects_returnsEmptyWhenTheTypeHasNoInstances() {
        when(entityObjectAccess.findAll(ENTITY)).thenReturn(List.of());

        assertThat(gateway.findObjects(ORG, ENTITY)).isEmpty();
    }
}
