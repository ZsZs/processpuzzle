package com.processpuzzle.basestate.usecase.service;

import com.processpuzzle.basestate.usecase.exception.UnknownActionBeanException;
import com.processpuzzle.basestate.usecase.exception.UnknownGuardBeanException;
import com.processpuzzle.basestate.usecase.port.TransitionAction;
import com.processpuzzle.basestate.usecase.port.TransitionGuard;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Resolves {@link com.processpuzzle.basestate.domain.GuardRef#beanName()} and {@link
 * com.processpuzzle.basestate.domain.ActionRef#beanName()} against the Spring context, by name —
 * unlike {@link EntityObjectGatewayResolver}, which resolves a single override-or-default bean,
 * a state machine may name any number of distinct guard/action beans, so lookup has to be by name
 * rather than by unique type.
 *
 * <p>Used twice: by {@code StateMachineTopologyValidator} at definition save time, so a typo'd
 * {@code beanName} is rejected immediately rather than the first time the transition fires; and
 * by {@code StateMachineEngine} at transition-fire time, to actually evaluate/execute.
 */
@Component
public class GuardActionResolver {

    private final ApplicationContext applicationContext;

    public GuardActionResolver(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** Whether {@code beanName} resolves to a {@link TransitionGuard} bean. */
    public boolean isKnownGuard(String beanName) {
        return isKnownBean(beanName, TransitionGuard.class);
    }

    /** Whether {@code beanName} resolves to a {@link TransitionAction} bean. */
    public boolean isKnownAction(String beanName) {
        return isKnownBean(beanName, TransitionAction.class);
    }

    public TransitionGuard resolveGuard(String beanName) {
        try {
            return applicationContext.getBean(beanName, TransitionGuard.class);
        } catch (BeansException e) {
            throw new UnknownGuardBeanException(beanName);
        }
    }

    public TransitionAction resolveAction(String beanName) {
        try {
            return applicationContext.getBean(beanName, TransitionAction.class);
        } catch (BeansException e) {
            throw new UnknownActionBeanException(beanName);
        }
    }

    private boolean isKnownBean(String beanName, Class<?> type) {
        try {
            applicationContext.getBean(beanName, type);
            return true;
        } catch (BeansException e) {
            return false;
        }
    }
}
