package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionValidator;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportProcessDefinitionsUseCase;
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

class DefaultWorkflowImporterTest {

    private static final String TESTBED_FILE = "processpuzzle-testbed-workflows.yaml";

    private ImportProcessDefinitionsUseCase importUseCase;
    private ResourcePatternResolver resourceResolver;
    private DefaultWorkflowImporter importer;

    @BeforeEach
    void setUp() {
        importUseCase = mock(ImportProcessDefinitionsUseCase.class);
        resourceResolver = mock(ResourcePatternResolver.class);
        importer = new DefaultWorkflowImporter(importUseCase, resourceResolver);
    }

    @Test
    void loadsAndImportsBundledWorkflowsFile() throws IOException {
        Resource bundledResource = bundledTestbedFile();
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{bundledResource});
        when(importUseCase.execute(eq("processpuzzle-testbed"), any(InputStream.class)))
                .thenReturn(new ImportOutcome(1, 0, List.of()));

        importer.loadDefaults();

        verify(importUseCase).execute(eq("processpuzzle-testbed"), any(InputStream.class));
    }

    @Test
    void bundledFileParsesAndImportsValidWorkflow() throws IOException {
        ProcessDefinitionRepository repository = mock(ProcessDefinitionRepository.class);
        ProcessDefinitionValidator validator = new ProcessDefinitionValidator();
        ImportProcessDefinitionsUseCase realImportUseCase = new ImportProcessDefinitionsUseCase(repository, validator);

        when(repository.findByOrgKey("processpuzzle-testbed")).thenReturn(List.of());
        when(repository.findByOrgKeyAndId("processpuzzle-testbed", "order-fulfillment-workflow"))
                .thenReturn(Optional.empty());

        try (InputStream input = bundledTestbedFile().getInputStream()) {
            ImportOutcome outcome = realImportUseCase.execute("processpuzzle-testbed", input);

            assertThat(outcome.errors()).isEmpty();
            assertThat(outcome.created()).isEqualTo(1);
            assertThat(outcome.updated()).isZero();

            ArgumentCaptor<ProcessDefinition> defCaptor = ArgumentCaptor.forClass(ProcessDefinition.class);
            verify(repository).save(defCaptor.capture());

            ProcessDefinition def = defCaptor.getValue();
            assertThat(def.getOrgKey()).isEqualTo("processpuzzle-testbed");
            assertThat(def.getId()).isEqualTo("order-fulfillment-workflow");
            assertThat(def.getName()).isEqualTo("Order Fulfillment Workflow");
            assertThat(def.getRoles()).extracting("id").containsExactly("clerk", "manager");
            assertThat(def.getWorkProducts()).extracting("id").containsExactly("order-entity", "fulfillment-invoice");
            assertThat(def.getTasks()).extracting("id")
                    .containsExactly("review-order", "approve-shipment", "confirm-delivery");
        }
    }

    @Test
    void skipsFileWhenNameDoesNotFollowConvention() throws IOException {
        Resource invalidNameResource = new ByteArrayResource("processes: []".getBytes(StandardCharsets.UTF_8)) {
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
        Resource emptyOrgResource = new ByteArrayResource("processes: []".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "-workflows.yaml";
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
        when(brokenResource.getFilename()).thenReturn("test-org-workflows.yaml");
        when(brokenResource.getInputStream()).thenThrow(new IOException("Stream failed"));
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{brokenResource});

        assertThatCode(() -> importer.loadDefaults()).doesNotThrowAnyException();
    }

    @Test
    void logsWarningsWhenImportOutcomeContainsErrors() throws IOException {
        Resource testResource = mock(Resource.class);
        when(testResource.getFilename()).thenReturn("test-org-workflows.yaml");
        when(testResource.getInputStream()).thenReturn(new ByteArrayResource(new byte[0]).getInputStream());
        when(resourceResolver.getResources(anyString())).thenReturn(new Resource[]{testResource});
        when(importUseCase.execute(eq("test-org"), any(InputStream.class)))
                .thenReturn(new ImportOutcome(0, 0, List.of("Invalid workflow topology")));

        assertThatCode(() -> importer.loadDefaults()).doesNotThrowAnyException();
        verify(importUseCase).execute(eq("test-org"), any(InputStream.class));
    }

    private static Resource bundledTestbedFile() {
        return new ClassPathResource("default-workflows/" + TESTBED_FILE);
    }
}
