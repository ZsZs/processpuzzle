package com.processpuzzle.basestate.usecase.service;

import com.processpuzzle.basestate.usecase.exception.UnknownActionBeanException;
import com.processpuzzle.basestate.usecase.exception.UnknownGuardBeanException;
import com.processpuzzle.basestate.usecase.port.TransitionAction;
import com.processpuzzle.basestate.usecase.port.TransitionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuardActionResolverTest {

    private ApplicationContext context;
    private GuardActionResolver resolver;

    @BeforeEach
    void setUp() {
        context = mock(ApplicationContext.class);
        resolver = new GuardActionResolver(context);
    }

    @Test
    void isKnownGuard_shouldReturnTrueWhenBeanExistsAndMatchesType() {
        TransitionGuard guard = mock(TransitionGuard.class);
        when(context.getBean("myGuard", TransitionGuard.class)).thenReturn(guard);

        assertThat(resolver.isKnownGuard("myGuard")).isTrue();
    }

    @Test
    void isKnownGuard_shouldReturnFalseWhenNoSuchBean() {
        when(context.getBean("missing", TransitionGuard.class))
                .thenThrow(new NoSuchBeanDefinitionException("missing"));

        assertThat(resolver.isKnownGuard("missing")).isFalse();
    }

    @Test
    void isKnownGuard_shouldReturnFalseWhenBeanHasWrongType() {
        when(context.getBean("wrongType", TransitionGuard.class))
                .thenThrow(new BeanNotOfRequiredTypeException("wrongType", TransitionGuard.class, String.class));

        assertThat(resolver.isKnownGuard("wrongType")).isFalse();
    }

    @Test
    void isKnownAction_shouldReturnTrueWhenBeanExistsAndMatchesType() {
        TransitionAction action = mock(TransitionAction.class);
        when(context.getBean("myAction", TransitionAction.class)).thenReturn(action);

        assertThat(resolver.isKnownAction("myAction")).isTrue();
    }

    @Test
    void isKnownAction_shouldReturnFalseWhenNoSuchBean() {
        when(context.getBean("missing", TransitionAction.class))
                .thenThrow(new NoSuchBeanDefinitionException("missing"));

        assertThat(resolver.isKnownAction("missing")).isFalse();
    }

    @Test
    void resolveGuard_shouldReturnBeanWhenFound() {
        TransitionGuard guard = mock(TransitionGuard.class);
        when(context.getBean("myGuard", TransitionGuard.class)).thenReturn(guard);

        assertThat(resolver.resolveGuard("myGuard")).isSameAs(guard);
    }

    @Test
    void resolveGuard_shouldThrowWhenNotFound() {
        when(context.getBean("missing", TransitionGuard.class))
                .thenThrow(new NoSuchBeanDefinitionException("missing"));

        assertThatThrownBy(() -> resolver.resolveGuard("missing"))
                .isInstanceOf(UnknownGuardBeanException.class);
    }

    @Test
    void resolveAction_shouldReturnBeanWhenFound() {
        TransitionAction action = mock(TransitionAction.class);
        when(context.getBean("myAction", TransitionAction.class)).thenReturn(action);

        assertThat(resolver.resolveAction("myAction")).isSameAs(action);
    }

    @Test
    void resolveAction_shouldThrowWhenNotFound() {
        when(context.getBean("missing", TransitionAction.class))
                .thenThrow(new NoSuchBeanDefinitionException("missing"));

        assertThatThrownBy(() -> resolver.resolveAction("missing"))
                .isInstanceOf(UnknownActionBeanException.class);
    }
}
