package com.processpuzzle.workflow.common;

import com.processpuzzle.state.api.StateOperationApi;
import com.processpuzzle.workflow.execution.adapters.outbound.BaseStateEntityStateGateway;
import com.processpuzzle.workflow.execution.adapters.outbound.UnavailableEntityStateGateway;
import com.processpuzzle.workflow.execution.usecases.outbound.EntityStateGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Same shape as base-state's own wiring for EntityObjectGateway towards base-entity:
 * ObjectProvider so this module compiles and starts whether or not base-state is deployed
 * alongside it, with a one-line warning at startup rather than a silent no-op — the warning is
 * where you'll notice a missing dependency instead of discovering it the first time a task with
 * completionStateTriggerKey completes and nothing happens.
 */
@Configuration
class WorkflowStateGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStateGatewayConfig.class);

    @Bean
    EntityStateGateway entityStateGateway(ObjectProvider<StateOperationApi> stateOperationApiProvider) {
        StateOperationApi stateOperationApi = stateOperationApiProvider.getIfAvailable();

        if (stateOperationApi == null) {
            log.warn("base-state's StateOperationApi is not available — falling back to "
                    + "UnavailableEntityStateGateway. Tasks with completionStateTriggerKey or "
                    + "preconditionStateKey will fail loudly instead of driving order state.");
            return new UnavailableEntityStateGateway();
        }

        return new BaseStateEntityStateGateway(stateOperationApi);
    }
}
