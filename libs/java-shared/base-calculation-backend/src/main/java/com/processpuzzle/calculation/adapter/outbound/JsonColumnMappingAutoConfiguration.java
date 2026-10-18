package com.processpuzzle.calculation.adapter.outbound;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.hibernate.cfg.MappingSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Keeps feel-engine's Scala module out of Hibernate's JSON columns.
 *
 * <p>feel-engine needs {@code jackson-module-scala} for FEEL's own JSON functions, and the module registers itself
 * through Jackson's service loader. Hibernate's default JSON format mapper registers every module it finds — so
 * with feel-engine on the classpath, a {@code jsonb} payload's nested objects would read back as Scala maps, in every
 * feature's columns, not only this one's. This gives Hibernate the same mapper with every discovered module but that
 * one. An application that chose its own format mapper keeps it.
 */
@AutoConfiguration
@ConditionalOnClass({HibernatePropertiesCustomizer.class, ObjectMapper.class, JacksonJsonFormatMapper.class})
public class JsonColumnMappingAutoConfiguration {

    static final String SCALA_MODULES = "com.fasterxml.jackson.module.scala.";

    @Bean
    HibernatePropertiesCustomizer scalaFreeJsonFormatMapper() {
        return properties -> properties.putIfAbsent(MappingSettings.JSON_FORMAT_MAPPER,
                new JacksonJsonFormatMapper(objectMapper()));
    }

    static ObjectMapper objectMapper() {
        List<Module> modules = ObjectMapper.findModules(JsonColumnMappingAutoConfiguration.class.getClassLoader()).stream()
                .filter(module -> !module.getClass().getName().startsWith(SCALA_MODULES))
                .toList();
        return new ObjectMapper().registerModules(modules);
    }
}
