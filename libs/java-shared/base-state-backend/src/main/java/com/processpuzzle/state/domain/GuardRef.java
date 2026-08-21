package com.processpuzzle.state.domain;

import java.util.Map;

/**
 * Names a Spring bean implementing {@code TransitionGuard}, resolved by bean name — the same
 * named-SPI convention as base-rule's {@code EntityDataExtractor} seam. Existence and type of the
 * bean are checked at definition save time by {@code StateMachineTopologyValidator}, via
 * {@code GuardActionResolver}, so a typo surfaces immediately rather than the first time the
 * transition is fired.
 *
 * @param beanName the Spring bean name, e.g. {@code "sufficientBalanceGuard"}
 * @param params   static configuration passed to {@code TransitionGuard.evaluate()} alongside the
 *                 runtime {@code TransitionContext}; may be {@code null}
 */
public record GuardRef(String beanName, Map<String, Object> params) {

    public GuardRef {
        if (beanName == null || beanName.isBlank()) {
            throw new IllegalArgumentException("GuardRef.beanName must not be blank");
        }
    }
}
