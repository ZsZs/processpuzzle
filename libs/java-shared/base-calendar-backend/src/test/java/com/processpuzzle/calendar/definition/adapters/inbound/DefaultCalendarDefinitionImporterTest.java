package com.processpuzzle.calendar.definition.adapters.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.usecases.inbound.ImportCalendarDefinitions;
import com.processpuzzle.calendar.definition.usecases.inbound.ImportOutcome;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

class DefaultCalendarDefinitionImporterTest {

    private ImportCalendarDefinitions importUseCase;
    private ResourcePatternResolver resolver;
    private DefaultCalendarDefinitionImporter importer;

    @BeforeEach
    void setUp() {
        importUseCase = mock(ImportCalendarDefinitions.class);
        resolver = mock(ResourcePatternResolver.class);
        importer = new DefaultCalendarDefinitionImporter(importUseCase, resolver);
    }

    private static Resource file(String name) {
        return new ByteArrayResource("calendar-definitions: []".getBytes()) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    @Test
    void takesTheOrganizationFromTheFileNameAndReleasesWhatItImports() throws Exception {
        when(resolver.getResources(DefaultCalendarDefinitionImporter.LOCATION)).thenReturn(new Resource[] {file("org-1-calendar-definitions.yaml")});
        when(importUseCase.execute(eq("org-1"), any(), eq(true))).thenReturn(new ImportOutcome(1, 0, List.of(), List.of("a")));

        importer.loadDefaults();

        verify(importUseCase).execute(eq("org-1"), any(), eq(true));
    }

    @Test
    void skipsAFileThatDoesNotFollowTheNamingConvention() throws Exception {
        when(resolver.getResources(DefaultCalendarDefinitionImporter.LOCATION)).thenReturn(new Resource[] {file("calendar-definitions.yaml"), file("-calendar-definitions.yaml")});

        importer.loadDefaults();

        verify(importUseCase, never()).execute(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void neverFailsStartupWhateverGoesWrong() throws Exception {
        when(resolver.getResources(DefaultCalendarDefinitionImporter.LOCATION)).thenReturn(new Resource[] {file("a-calendar-definitions.yaml"), file("b-calendar-definitions.yaml")});
        when(importUseCase.execute(eq("a"), any(), eq(true))).thenThrow(new IOException("boom"));
        when(importUseCase.execute(eq("b"), any(), eq(true))).thenReturn(new ImportOutcome(0, 0, List.of("bad"), List.of()));

        importer.loadDefaults();

        verify(importUseCase).execute(eq("b"), any(), eq(true));
    }

    @Test
    void toleratesNoFilesAndAnUnreadableLocation() throws Exception {
        when(resolver.getResources(DefaultCalendarDefinitionImporter.LOCATION)).thenReturn(new Resource[0]).thenThrow(new IOException("scan failed"));

        importer.loadDefaults();
        importer.loadDefaults();

        verify(importUseCase, never()).execute(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void readsTheOrganizationOutOfTheFileName() {
        assertThat(DefaultCalendarDefinitionImporter.orgKeyOf("processpuzzle-testbed-calendar-definitions.yaml")).isEqualTo("processpuzzle-testbed");
        assertThat(DefaultCalendarDefinitionImporter.orgKeyOf("x.yaml")).isNull();
        assertThat(DefaultCalendarDefinitionImporter.orgKeyOf(null)).isNull();
    }
}
