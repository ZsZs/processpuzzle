package com.processpuzzle.baseentity.instances.usecases.outbound;

import java.util.Map;

/**
 * Validates an EntityObject payload (including recursing into embedded-component sub-payloads)
 * before persistence. The default implementation checks structure, and asks
 * {@link com.processpuzzle.baseentity.port.EntityRulesPort} for the rules the host application enforces.
 */
public interface PayloadValidatorPort {

    void validate(String orgKey, EntityDefinitionView definition, Map<String, Object> payload);
}
