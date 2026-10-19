package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.engine.CalculationEngine;
import com.processpuzzle.calculation.engine.ExpressionEnginePort;
import com.processpuzzle.calculation.port.EntityAttributeTypePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The engine is plain Java, so the use cases get it from here. */
@Configuration(proxyBeanMethods = false)
class CalculationEngineConfiguration {

    @Bean
    CalculationEngine calculationEngine(ExpressionEnginePort feel) {
        return new CalculationEngine(feel);
    }

    /** Without the host's entity catalog, values are passed as stored. */
    @Bean
    TypedValues typedValues(ObjectProvider<EntityAttributeTypePort> types) {
        return new TypedValues(types.getIfAvailable(() -> EntityAttributeTypePort.UNKNOWN));
    }
}
