package com.processpuzzle.core.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Maps the event columns of Spring Modulith's event publication registry as {@code text}, wherever the JPA registry
 * is on the classpath; see {@code META-INF/processpuzzle/event-publication-orm.xml} for why.
 *
 * <p>A mapping file rather than a column alteration at startup: Hibernate 7's schema update aligns a column with its
 * mapping on every start, so only a mapping that says {@code text} keeps the column {@code text}. It also widens a
 * column a database created narrow, on the first start with this mapping.
 */
@AutoConfiguration
@ConditionalOnClass(name = {EventPublicationMappingAutoConfiguration.REGISTRY_ENTITY,
        "org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryBuilderCustomizer"})
public class EventPublicationMappingAutoConfiguration {

    static final String REGISTRY_ENTITY = "org.springframework.modulith.events.jpa.updating.DefaultJpaEventPublication";
    static final String MAPPING_FILE = "META-INF/processpuzzle/event-publication-orm.xml";

    @Bean
    EntityManagerFactoryBuilderCustomizer eventPublicationMapping() {
        return builder -> builder.addPersistenceUnitPostProcessors(unit -> unit.addMappingFileName(MAPPING_FILE));
    }
}
