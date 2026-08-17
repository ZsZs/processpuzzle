package com.processpuzzle.basestate.adapter.inbound;

import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.StateMachineDefinitionRepository;
import com.processpuzzle.basestate.usecase.ImportOutcome;
import com.processpuzzle.basestate.usecase.ImportStateMachineDefinitions;
import com.processpuzzle.basestate.usecase.StateMachineTopologyValidator;
import com.processpuzzle.basestate.usecase.service.GuardActionResolver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultStateImporterTest {

    private static final String TESTBED_FILE = "processpuzzle-testbed-state-machines.yaml";

    private ImportStateMachineDefinitions importUseCase;
    private ResourcePatternResolver resourceResolver;
    private DefaultStateImporter importer;

    @BeforeEach
    void setUp() {
        importUseCase = mock(ImportStateMachineDefinitions.class);
        resourceResolver = mock(ResourcePatternResolver.class);
        importer = new DefaultStateImporter(importUseCase, resourceResolver);
    }

    @Test
    void loadsAndImportsBundledStateMachinesFile() throws IOException {
        Resource bundledResource = bundledTestbedFile();
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{bundledResource});
        when(importUseCase.execute(eq("processpuzzle-testbed"), any(InputStream.class)))
                .thenReturn(new ImportOutcome(1, 0, List.of()));

        importer.loadDefaults();

        verify(importUseCase).execute(eq("processpuzzle-testbed"), any(InputStream.class));
    }

    @Test
    void bundledFileParsesAndImportsValidDynamicEntityStateMachine() throws IOException {
        StateMachineDefinitionRepository repository = mock(StateMachineDefinitionRepository.class);
        GuardActionResolver guardActionResolver = mock(GuardActionResolver.class);
        StateMachineTopologyValidator validator = new StateMachineTopologyValidator(guardActionResolver);
        ImportStateMachineDefinitions realImportUseCase = new ImportStateMachineDefinitions(repository, validator);

        when(repository.findByOrgKeyAndEntityName("processpuzzle-testbed", "dynamic-entity"))
                .thenReturn(Optional.empty());

        try (InputStream input = bundledTestbedFile().getInputStream()) {
            ImportOutcome outcome = realImportUseCase.execute("processpuzzle-testbed", input);

            assertThat(outcome.errors()).isEmpty();
            assertThat(outcome.created()).isEqualTo(1);
            assertThat(outcome.updated()).isEqualTo(0);

            ArgumentCaptor<StateMachineDefinition> defCaptor = ArgumentCaptor.forClass(StateMachineDefinition.class);
            verify(repository).save(defCaptor.capture());

            StateMachineDefinition def = defCaptor.getValue();
            assertThat(def.getOrgKey()).isEqualTo("processpuzzle-testbed");
            assertThat(def.getEntityName()).isEqualTo("dynamic-entity");
            assertThat(def.getStateAttributeKey()).isEqualTo("enumAttr");
            assertThat(def.getInitialStateKey()).isEqualTo("DRAFT");
            assertThat(def.getStates()).extracting("key")
                    .containsExactly("DRAFT", "IN_PROGRESS", "COMPLETED", "ARCHIVED");
            assertThat(def.getTransitions()).extracting("key")
                    .containsExactly("start-progress", "complete", "reopen", "archive");
        }
    }

    @Test
    void skipsFileWhenNameDoesNotFollowConvention() throws IOException {
        Resource invalidNameResource = new ByteArrayResource("stateMachines: []".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "invalid-name.yaml";
            }
        };
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{invalidNameResource});

        importer.loadDefaults();

        verifyNoInteractions(importUseCase);
    }

    @Test
    void skipsFileWhenOrgKeyIsEmpty() throws IOException {
        Resource emptyOrgResource = new ByteArrayResource("stateMachines: []".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "-state-machines.yaml";
            }
        };
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{emptyOrgResource});

        importer.loadDefaults();

        verifyNoInteractions(importUseCase);
    }

    @Test
    void handlesResourceScanFailureGracefully() throws IOException {
        when(resourceResolver.getResources(anyString())).thenThrow(new IOException("Disk I/O error"));

        assertThatCode(() -> importer.loadDefaults()).doesNotThrowAnyException();
        verifyNoInteractions(importUseCase);
    }

    @Test
    void handlesEmptyResourceListGracefully() throws IOException {
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[0]);

        assertThatCode(() -> importer.loadDefaults()).doesNotThrowAnyException();
        verifyNoInteractions(importUseCase);
    }

    @Test
    void handlesImportExceptionGracefully() throws IOException {
        Resource brokenResource = mock(Resource.class);
        when(brokenResource.getFilename()).thenReturn("test-org-state-machines.yaml");
        when(brokenResource.getInputStream()).thenThrow(new IOException("Stream failed"));
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{brokenResource});

        assertThatCode(() -> importer.loadDefaults()).doesNotThrowAnyException();
    }

    @Test
    void logsWarningsWhenImportOutcomeContainsErrors() throws IOException {
        Resource testResource = mock(Resource.class);
        when(testResource.getFilename()).thenReturn("test-org-state-machines.yaml");
        when(testResource.getInputStream()).thenReturn(new ByteArrayResource(new byte[0]).getInputStream());
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{testResource});
        when(importUseCase.execute(eq("test-org"), any(InputStream.class)))
                .thenReturn(new ImportOutcome(0, 0, List.of("Invalid state machine topology")));

        assertThatCode(() -> importer.loadDefaults()).doesNotThrowAnyException();
        verify(importUseCase).execute(eq("test-org"), any(InputStream.class));
    }

    private static Resource bundledTestbedFile() {
        return new ClassPathResource("default-state-machines/" + TESTBED_FILE);
    }
}
