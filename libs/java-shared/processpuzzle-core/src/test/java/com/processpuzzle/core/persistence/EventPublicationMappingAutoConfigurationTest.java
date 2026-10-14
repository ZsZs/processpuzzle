package com.processpuzzle.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.orm.jpa.persistenceunit.MutablePersistenceUnitInfo;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitPostProcessor;

class EventPublicationMappingAutoConfigurationTest {

    @Test
    void addsTheMappingFileToThePersistenceUnit() {
        EntityManagerFactoryBuilder builder = mock(EntityManagerFactoryBuilder.class);
        ArgumentCaptor<PersistenceUnitPostProcessor> postProcessor = ArgumentCaptor.forClass(PersistenceUnitPostProcessor.class);

        new EventPublicationMappingAutoConfiguration().eventPublicationMapping().customize(builder);

        verify(builder).addPersistenceUnitPostProcessors(postProcessor.capture());
        MutablePersistenceUnitInfo unit = new MutablePersistenceUnitInfo();
        postProcessor.getValue().postProcessPersistenceUnitInfo(unit);
        assertThat(unit.getMappingFileNames()).containsExactly(EventPublicationMappingAutoConfiguration.MAPPING_FILE);
    }

    @Test
    void theMappingFileWidensBothRegistryEntities() throws IOException {
        try (InputStream mapping = getClass().getClassLoader().getResourceAsStream(EventPublicationMappingAutoConfiguration.MAPPING_FILE)) {
            assertThat(mapping).isNotNull();
            String content = new String(mapping.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content)
                .contains(EventPublicationMappingAutoConfiguration.REGISTRY_ENTITY)
                .contains("org.springframework.modulith.events.jpa.archiving.ArchivedJpaEventPublication")
                .contains("name=\"serializedEvent\"", "name=\"listenerId\"", "name=\"eventType\"");
        }
    }

    @Test
    void staysOutOfABackendWithoutTheJpaRegistry() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventPublicationMappingAutoConfiguration.class))
            .run(context -> assertThat(context).doesNotHaveBean("eventPublicationMapping"));
    }
}
