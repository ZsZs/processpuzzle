package com.processpuzzle.calculation.adapter.inbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.calculation.usecase.ImportCalculationDefinitions;
import com.processpuzzle.calculation.usecase.ImportOutcome;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

class DefaultCalculationImporterTest {

    private final ImportCalculationDefinitions importer = mock(ImportCalculationDefinitions.class);
    private final ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
    private final CalculationReleaseParticipant release = mock(CalculationReleaseParticipant.class);

    private static Resource named(String name) {
        return new ByteArrayResource("calculation: a\n".getBytes()) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    @Test
    void importsAndReleasesEachSeedFile() throws IOException {
        when(resolver.getResources(DefaultCalculationImporter.LOCATION))
                .thenReturn(new Resource[] {named("acme-calculations.yaml"), named("oddly-named.yaml")});
        when(importer.execute(eq("acme"), any(InputStream.class))).thenReturn(new ImportOutcome(1, 0, List.of("warn"), List.of("a")));

        new DefaultCalculationImporter(importer, resolver, release).loadDefaults();

        verify(release).release("acme", List.of("a"));
        verify(importer, never()).execute(eq("oddly-named"), any(InputStream.class));
    }

    @Test
    void survivesMissingAndFailingFiles() throws IOException {
        when(resolver.getResources(anyString())).thenReturn(new Resource[0]);
        new DefaultCalculationImporter(importer, resolver, release).loadDefaults();

        org.mockito.Mockito.doThrow(new IOException("scan")).when(resolver).getResources(anyString());
        new DefaultCalculationImporter(importer, resolver, release).loadDefaults();

        org.mockito.Mockito.doReturn(new Resource[] {named("acme-calculations.yaml")}).when(resolver).getResources(anyString());
        when(importer.execute(eq("acme"), any(InputStream.class))).thenThrow(new IllegalStateException("broken"));
        new DefaultCalculationImporter(importer, resolver, release).loadDefaults();
        verify(release, never()).release(anyString(), any());
    }
}
