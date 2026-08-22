package com.processpuzzle.state.adapter.outbound;

import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.baseentity.api.EntityObjectView;
import com.processpuzzle.state.usecase.exception.EntityObjectNotFoundException;
import com.processpuzzle.state.usecase.exception.StaleEntityObjectVersionException;
import com.processpuzzle.state.usecase.port.EntityObjectGateway;
import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Binds base-state's {@link EntityObjectGateway} port to base-entity's published {@code
 * EntityObjectAccess}. This is the adapter the port's javadoc was waiting for; with it present
 * {@code EntityObjectGatewayResolver} stops falling back to {@code UnavailableEntityObjectGateway}
 * and the operation layer becomes reachable.
 *
 * <p>Two mappings are worth naming.
 *
 * <p><b>{@code entityName} is base-entity's {@code entityDefinitionCode}.</b> Not the display name
 * — the seeded machines name {@code dynamic-entity} and {@code order}, which are definition codes.
 * A machine whose {@code entityName} matched no definition code would fail here at read time;
 * {@code StateMachineTopologyValidator} rejects that at save time instead.
 *
 * <p><b>{@code orgKey} is accepted and ignored.</b> An {@code EntityObject} has no organization
 * column, so instances are effectively single-tenant while the <em>machines</em> that govern them
 * are per-organization. Dropping it here rather than at the caller keeps the port honest about
 * what it will mean once instances do become tenant-scoped: nothing above this line has to change.
 *
 * <p>{@code EntityObjectAccessException} is translated into base-state's port exceptions. The port
 * declares its own failure modes precisely so its callers need not know which adapter is wired, and
 * {@code StateApiExceptionHandler} maps those two to 404 and 409.
 */
@Component
@RequiredArgsConstructor
public class BaseEntityObjectGateway implements EntityObjectGateway {

    private final EntityObjectAccess entityObjectAccess;

    @Override
    public EntityObjectSnapshot findObject(String orgKey, String entityName, UUID objectId) {
        try {
            EntityObjectView view = entityObjectAccess.find(entityName, objectId);
            return new EntityObjectSnapshot(view.id(), view.version(), view.payload());
        } catch (EntityObjectAccessException.NotFound e) {
            throw new EntityObjectNotFoundException(orgKey, entityName, objectId);
        }
    }

    @Override
    public long updateStateAttribute(String orgKey, String entityName, UUID objectId,
                                     String attributeKey, String newValue, long expectedVersion) {
        try {
            return entityObjectAccess.updateAttribute(entityName, objectId, attributeKey, newValue, expectedVersion);
        } catch (EntityObjectAccessException.NotFound e) {
            throw new EntityObjectNotFoundException(orgKey, entityName, objectId);
        } catch (EntityObjectAccessException.VersionConflict e) {
            throw new StaleEntityObjectVersionException(objectId, expectedVersion);
        }
    }
}
