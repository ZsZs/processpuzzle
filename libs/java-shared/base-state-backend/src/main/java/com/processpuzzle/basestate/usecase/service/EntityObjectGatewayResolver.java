package com.processpuzzle.basestate.usecase.service;

import com.processpuzzle.basestate.usecase.port.EntityObjectGateway;
import com.processpuzzle.basestate.usecase.port.UnavailableEntityObjectGateway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The single place the feature resolves {@link EntityObjectGateway}. Use cases depend on this
 * component, never on the port directly — same shape as {@code OrganizationGuard} in
 * base-app-backend.
 *
 * <p>{@link ObjectProvider#getIfUnique} rather than {@code @ConditionalOnMissingBean}, for the
 * same reason {@code OrganizationGuard} uses it: that condition is only reliable inside
 * auto-configuration, which runs after user beans are registered, and {@code scanBasePackages}
 * would pick up an {@code @AutoConfiguration} class under {@code com.processpuzzle} via the
 * ordinary component scan before the auto-configuration import is applied.
 */
@Component
public class EntityObjectGatewayResolver {

    private final EntityObjectGateway gateway;

    public EntityObjectGatewayResolver(ObjectProvider<EntityObjectGateway> gatewayProvider) {
        this.gateway = gatewayProvider.getIfUnique(UnavailableEntityObjectGateway::new);
    }

    public EntityObjectGateway gateway() {
        return gateway;
    }
}
