package com.processpuzzle.event.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.usecase.ImportEventDefinitions;
import com.processpuzzle.event.usecase.ImportOutcome;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

class DefaultEventDefinitionImporterTest {

    @Test
    void importsTheBundledTestbedSeedIntoItsOrganization() {
        EventDefinitionRepository repository = mock(EventDefinitionRepository.class);
        when(repository.findByOrgKeyAndId(any(), any())).thenReturn(Optional.empty());
        DefaultEventDefinitionImporter importer = new DefaultEventDefinitionImporter(
                new ImportEventDefinitions(repository), new PathMatchingResourcePatternResolver());

        importer.loadDefaults();

        verify(repository).findByOrgKeyAndId("processpuzzle-testbed", "OrderCreatedEvent");
        verify(repository).findByOrgKeyAndId("processpuzzle-testbed", "OrderConfirmedEvent");
        verify(repository).findByOrgKeyAndId("processpuzzle-testbed", "OrderDeliveredEvent");
    }

    @Test
    void skipsAFileOffTheNamingConventionAndSurvivesFailures() throws IOException {
        ImportEventDefinitions useCase = mock(ImportEventDefinitions.class);
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        Resource badName = new ByteArrayResource(new byte[0]) {
            @Override
            public String getFilename() {
                return "events.yaml";
            }
        };
        Resource failing = new ByteArrayResource(new byte[0]) {
            @Override
            public String getFilename() {
                return "acme-event-definitions.yaml";
            }
        };
        when(resolver.getResources(DefaultEventDefinitionImporter.LOCATION)).thenReturn(new Resource[] {badName, failing});
        when(useCase.execute(eq("acme"), any())).thenThrow(new IOException("boom"));

        new DefaultEventDefinitionImporter(useCase, resolver).loadDefaults();

        verify(useCase).execute(eq("acme"), any());
    }

    @Test
    void logsTheErrorsOfARejectedFile() throws IOException {
        ImportEventDefinitions useCase = mock(ImportEventDefinitions.class);
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        Resource file = new ByteArrayResource(new byte[0]) {
            @Override
            public String getFilename() {
                return "acme-event-definitions.yaml";
            }
        };
        when(resolver.getResources(DefaultEventDefinitionImporter.LOCATION)).thenReturn(new Resource[] {file});
        when(useCase.execute(eq("acme"), any())).thenReturn(ImportOutcome.rejected(List.of("bad")));

        new DefaultEventDefinitionImporter(useCase, resolver).loadDefaults();

        verify(useCase).execute(eq("acme"), any());
    }

    @Test
    void anEmptyOrUnreadableLocationImportsNothing() throws IOException {
        ImportEventDefinitions useCase = mock(ImportEventDefinitions.class);
        ResourcePatternResolver empty = mock(ResourcePatternResolver.class);
        when(empty.getResources(any())).thenReturn(new Resource[0]);
        ResourcePatternResolver broken = mock(ResourcePatternResolver.class);
        when(broken.getResources(any())).thenThrow(new IOException("boom"));

        new DefaultEventDefinitionImporter(useCase, empty).loadDefaults();
        new DefaultEventDefinitionImporter(useCase, broken).loadDefaults();

        verify(useCase, never()).execute(any(), any());
    }

    @Test
    void derivesTheOrganizationFromTheFileName() {
        assertThat(DefaultEventDefinitionImporter.orgKeyOf("acme-event-definitions.yaml")).isEqualTo("acme");
        assertThat(DefaultEventDefinitionImporter.orgKeyOf("-event-definitions.yaml")).isNull();
        assertThat(DefaultEventDefinitionImporter.orgKeyOf("acme.yaml")).isNull();
        assertThat(DefaultEventDefinitionImporter.orgKeyOf(null)).isNull();
    }
}
