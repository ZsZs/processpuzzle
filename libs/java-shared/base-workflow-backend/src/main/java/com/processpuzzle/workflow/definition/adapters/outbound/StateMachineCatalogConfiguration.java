package com.processpuzzle.workflow.definition.adapters.outbound;

import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.workflow.definition.usecases.outbound.NoStateMachineCatalogPort;
import com.processpuzzle.workflow.definition.usecases.outbound.StateMachineCatalogPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link StateMachineCatalogPort} to base-state's machines when they are deployed, and to a catalog that
 * knows none otherwise — the same fallback shape as the entity-state gateway.
 */
@Configuration
public class StateMachineCatalogConfiguration {

    @Bean
    public StateMachineCatalogPort stateMachineCatalogPort(ObjectProvider<StateMachineDefinitionRepository> machines) {
        StateMachineDefinitionRepository repository = machines.getIfAvailable();
        return repository == null ? new NoStateMachineCatalogPort() : new BaseStateMachineCatalogAdapter(repository);
    }
}
