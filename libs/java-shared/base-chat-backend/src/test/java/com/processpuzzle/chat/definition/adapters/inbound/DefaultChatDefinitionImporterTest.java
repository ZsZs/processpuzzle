package com.processpuzzle.chat.definition.adapters.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.definition.usecases.inbound.ImportChatDefinitions;
import com.processpuzzle.chat.definition.usecases.inbound.ImportOutcome;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

class DefaultChatDefinitionImporterTest {

    private ImportChatDefinitions importUseCase;
    private ResourcePatternResolver resolver;
    private DefaultChatDefinitionImporter importer;

    @BeforeEach
    void setUp() {
        importUseCase = mock(ImportChatDefinitions.class);
        resolver = mock(ResourcePatternResolver.class);
        importer = new DefaultChatDefinitionImporter(importUseCase, resolver);
    }

    private static Resource file(String name) {
        return new ByteArrayResource("chat-definitions: []".getBytes()) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    @Test
    void takesTheOrganizationFromTheFileNameAndReleasesWhatItImports() throws Exception {
        when(resolver.getResources(DefaultChatDefinitionImporter.LOCATION)).thenReturn(new Resource[] {file("org-1-chat-definitions.yaml")});
        when(importUseCase.execute(eq("org-1"), any(), eq(true))).thenReturn(new ImportOutcome(1, 0, List.of(), List.of("a")));

        importer.loadDefaults();

        verify(importUseCase).execute(eq("org-1"), any(), eq(true));
    }

    @Test
    void skipsAFileThatDoesNotFollowTheNamingConvention() throws Exception {
        when(resolver.getResources(DefaultChatDefinitionImporter.LOCATION)).thenReturn(new Resource[] {file("chat-definitions.yaml"), file("-chat-definitions.yaml")});

        importer.loadDefaults();

        verify(importUseCase, never()).execute(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void neverFailsStartupWhateverGoesWrong() throws Exception {
        when(resolver.getResources(DefaultChatDefinitionImporter.LOCATION)).thenReturn(new Resource[] {file("a-chat-definitions.yaml"), file("b-chat-definitions.yaml")});
        when(importUseCase.execute(eq("a"), any(), eq(true))).thenThrow(new IOException("boom"));
        when(importUseCase.execute(eq("b"), any(), eq(true))).thenReturn(new ImportOutcome(0, 0, List.of("bad"), List.of()));

        importer.loadDefaults();

        verify(importUseCase).execute(eq("b"), any(), eq(true));
    }

    @Test
    void toleratesNoFilesAndAnUnreadableLocation() throws Exception {
        when(resolver.getResources(DefaultChatDefinitionImporter.LOCATION)).thenReturn(new Resource[0]).thenThrow(new IOException("scan failed"));

        importer.loadDefaults();
        importer.loadDefaults();

        verify(importUseCase, never()).execute(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void readsTheOrganizationOutOfTheFileName() {
        assertThat(DefaultChatDefinitionImporter.orgKeyOf("processpuzzle-testbed-chat-definitions.yaml")).isEqualTo("processpuzzle-testbed");
        assertThat(DefaultChatDefinitionImporter.orgKeyOf("x.yaml")).isNull();
        assertThat(DefaultChatDefinitionImporter.orgKeyOf(null)).isNull();
    }
}
