package com.processpuzzle.state.usecase;

import com.processpuzzle.state.usecase.exception.EntityObjectNotFoundException;
import com.processpuzzle.state.usecase.exception.StaleEntityObjectVersionException;
import com.processpuzzle.state.usecase.exception.StateMachineAlreadyExistsException;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import com.processpuzzle.state.usecase.exception.UnknownActionBeanException;
import com.processpuzzle.state.usecase.exception.UnknownGuardBeanException;
import com.processpuzzle.state.usecase.exception.UnknownTriggerException;
import com.processpuzzle.state.usecase.port.EntityObjectGateway;
import com.processpuzzle.state.usecase.port.UnavailableEntityObjectGateway;
import com.processpuzzle.state.usecase.service.EntityObjectGatewayResolver;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayAndResolverTest {

    @Test
    void unavailableEntityObjectGateway_throwsOnAllMethods() {
        UnavailableEntityObjectGateway gateway = new UnavailableEntityObjectGateway();
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> gateway.findObject("org-1", "invoice", id))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("No EntityObjectGateway bean is configured");

        assertThatThrownBy(() -> gateway.updateStateAttribute("org-1", "invoice", id, "status", "approved", 1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("No EntityObjectGateway bean is configured");

        assertThatThrownBy(() -> gateway.findObjects("org-1", "invoice"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("No EntityObjectGateway bean is configured");
    }

    @Test
    @SuppressWarnings("unchecked")
    void entityObjectGatewayResolver_resolvesConfiguredGatewayOrFallback() {
        ObjectProvider<EntityObjectGateway> providerWithBean = mock(ObjectProvider.class);
        EntityObjectGateway customGateway = mock(EntityObjectGateway.class);
        when(providerWithBean.getIfUnique(any(Supplier.class))).thenReturn(customGateway);

        EntityObjectGatewayResolver resolver = new EntityObjectGatewayResolver(providerWithBean);
        assertThat(resolver.gateway()).isSameAs(customGateway);

        ObjectProvider<EntityObjectGateway> providerEmpty = mock(ObjectProvider.class);
        when(providerEmpty.getIfUnique(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<EntityObjectGateway> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        EntityObjectGatewayResolver fallbackResolver = new EntityObjectGatewayResolver(providerEmpty);
        assertThat(fallbackResolver.gateway()).isInstanceOf(UnavailableEntityObjectGateway.class);
    }

    @Test
    void testExceptions() {
        UUID id = UUID.randomUUID();

        EntityObjectNotFoundException ex1 = new EntityObjectNotFoundException("org1", "invoice", id);
        assertThat(ex1.getMessage()).contains("No entity object '" + id + "' of type 'invoice' in organization 'org1'");

        StaleEntityObjectVersionException ex2 = new StaleEntityObjectVersionException(id, 1L);
        assertThat(ex2.getMessage()).contains("has changed since version 1");

        StateMachineNotFoundException ex3 = new StateMachineNotFoundException("org1", "invoice");
        assertThat(ex3.getMessage()).contains("No state machine definition for entityName 'invoice' in organization 'org1'");

        StateMachineAlreadyExistsException ex4 = new StateMachineAlreadyExistsException("org1", "invoice");
        assertThat(ex4.getMessage()).contains("entityName 'invoice' already has a state machine definition in organization 'org1'");

        UnknownTriggerException ex5 = new UnknownTriggerException("invoice", "cancel");
        assertThat(ex5.getMessage()).contains("No transition with triggerKey 'cancel'");

        UnknownGuardBeanException ex6 = new UnknownGuardBeanException("missingGuard");
        assertThat(ex6.getMessage()).contains("No TransitionGuard bean named 'missingGuard' is registered");

        UnknownActionBeanException ex7 = new UnknownActionBeanException("missingAction");
        assertThat(ex7.getMessage()).contains("No TransitionAction bean named 'missingAction' is registered");
    }
}
