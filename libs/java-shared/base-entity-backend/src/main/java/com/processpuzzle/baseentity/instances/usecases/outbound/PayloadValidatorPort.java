package com.processpuzzle.baseentity.instances.usecases.outbound;

import java.util.Map;

/**
 * Validates an EntityObject payload (including recursing into embedded-component sub-payloads)
 * before persistence. A default structural-only implementation is provided; a module with
 * base-rule on the classpath should supply its own adapter bean
 * ({@code @ConditionalOnMissingBean}) that also runs ConstraintRule evaluation.
 */
public interface PayloadValidatorPort {

    void validate(EntityDefinitionView definition, Map<String, Object> payload);
}
