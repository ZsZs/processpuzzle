package com.processpuzzle.orgadmin.usecases.inbound;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code processpuzzle.self-service-roles.*}. Closed by default: a deployment opts in to letting every
 * signed-in user pick realm roles for themselves.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "processpuzzle.self-service-roles")
public class SelfServiceRolesProperties {

    /** Whether {@code /organizations/{orgKey}/me/roles} answers. */
    private boolean enabled = false;
}
