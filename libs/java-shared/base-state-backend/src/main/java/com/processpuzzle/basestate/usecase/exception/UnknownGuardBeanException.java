package com.processpuzzle.basestate.usecase.exception;

/** Thrown by {@code GuardActionResolver} when a {@code GuardRef.beanName} resolves to no bean, or to a bean not of type {@code TransitionGuard}. */
public class UnknownGuardBeanException extends RuntimeException {

    public UnknownGuardBeanException(String beanName) {
        super("No TransitionGuard bean named '" + beanName + "' is registered");
    }
}
