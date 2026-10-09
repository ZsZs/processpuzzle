package com.processpuzzle.composition;

import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.baseentity.api.EntityObjectAccess;
import com.processpuzzle.baseentity.api.EntityObjectAccessException;
import com.processpuzzle.workflow.execution.usecases.outbound.EntityLabelPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Names a workflow instance's subject for base-workflow from base-entity: the value of the attribute the
 * entity's definition marks {@code isLinkToDetails} — the order number of an Order, the column its own list
 * links by. Neither library names the other; see the package documentation.
 *
 * <p>Asked once per instance in a response. An object that has gone, an id that is not a UUID, or a type
 * that names nothing all answer empty, and base-workflow shows the id instead: a label is decoration, and
 * must not fail the instance list.
 */
@Component
public class EntityLabelAdapter implements EntityLabelPort {

    private final EntityAttributeQuery attributeQuery;
    private final EntityObjectAccess objectAccess;

    public EntityLabelAdapter(EntityAttributeQuery attributeQuery, EntityObjectAccess objectAccess) {
        this.attributeQuery = attributeQuery;
        this.objectAccess = objectAccess;
    }

    @Override
    public Optional<String> labelOf(String orgKey, String entityType, String entityId) {
        Optional<String> titleAttribute = attributeQuery.titleAttribute(orgKey, entityType);
        Optional<UUID> objectId = parse(entityId);
        if (titleAttribute.isEmpty() || objectId.isEmpty()) {
            return Optional.empty();
        }
        try {
            Object value = objectAccess.find(orgKey, entityType, objectId.get()).payload().get(titleAttribute.get());
            return value == null || value.toString().isBlank() ? Optional.empty() : Optional.of(value.toString());
        } catch (EntityObjectAccessException e) {
            return Optional.empty();
        }
    }

    private static Optional<UUID> parse(String entityId) {
        try {
            return entityId == null ? Optional.empty() : Optional.of(UUID.fromString(entityId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
