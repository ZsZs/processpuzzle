package com.processpuzzle.basestate.usecase.port;

import java.util.UUID;

/**
 * Fallback used when the deploying application supplies no {@link EntityObjectGateway} bean.
 *
 * <p>Unlike {@code PermitAllOrganizationAccessPolicy}, which has a genuinely safe permissive
 * default, there is no safe default read or write for an entity object — returning an empty
 * payload would make every guard evaluate against absent data, and silently accepting a write
 * would either persist nothing or throw somewhere far from the caller. Both methods here throw an
 * {@link UnsupportedOperationException} instead, which core's catch-all {@code
 * UnhandledExceptionHandler} maps to {@code 500}: an unwired integration is a deployment
 * misconfiguration, not a caller's mistake, so it should read as one.
 *
 * <p>Deliberately not a {@code @Component}: it is instantiated as a fallback by {@link
 * com.processpuzzle.basestate.usecase.service.EntityObjectGatewayResolver}, so a real gateway bean
 * never has to compete with it.
 */
public class UnavailableEntityObjectGateway implements EntityObjectGateway {

    private static final String MESSAGE =
            "No EntityObjectGateway bean is configured. base-state-backend cannot read or write "
                    + "EntityObject state until base-entity-backend's operation layer (or another "
                    + "adapter) implements com.processpuzzle.basestate.usecase.port.EntityObjectGateway "
                    + "and registers it as a Spring bean.";

    @Override
    public EntityObjectSnapshot findObject(String orgKey, String entityName, UUID objectId) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public long updateStateAttribute(String orgKey, String entityName, UUID objectId,
                                      String attributeKey, String newValue, long expectedVersion) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
