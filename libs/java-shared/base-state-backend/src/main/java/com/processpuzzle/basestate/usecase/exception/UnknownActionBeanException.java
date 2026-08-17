package com.processpuzzle.basestate.usecase.exception;

/** Thrown by {@code GuardActionResolver} when an {@code ActionRef.beanName} resolves to no bean, or to a bean not of type {@code TransitionAction}. */
public class UnknownActionBeanException extends RuntimeException {

    public UnknownActionBeanException(String beanName) {
        super("No TransitionAction bean named '" + beanName + "' is registered");
    }
}
