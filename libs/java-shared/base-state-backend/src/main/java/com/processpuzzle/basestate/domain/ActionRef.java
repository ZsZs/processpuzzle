package com.processpuzzle.basestate.domain;

import java.util.Map;

/**
 * Names a Spring bean implementing {@code TransitionAction}, resolved by bean name. Only run
 * after every {@link GuardRef} on the owning {@link Transition} has passed.
 *
 * @param beanName the Spring bean name, e.g. {@code "sendApprovalNotificationAction"}
 * @param params   static configuration passed to {@code TransitionAction.execute()}; may be
 *                 {@code null}
 */
public record ActionRef(String beanName, Map<String, Object> params) {

    public ActionRef {
        if (beanName == null || beanName.isBlank()) {
            throw new IllegalArgumentException("ActionRef.beanName must not be blank");
        }
    }
}
