package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.shared.model.ImportResult;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateArtifactDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateToolDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteArtifactDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteToolDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ExportProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllArtifactDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllProcessDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllRoleDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllTaskDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllToolDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindArtifactDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindToolDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportProcessDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceArtifactDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceToolDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.outbound.ActiveProcessInstanceExistencePort;
import com.processpuzzle.workflow.model.ArtifactDefinitionInput;
import com.processpuzzle.workflow.model.PageOfWorkflow;
import com.processpuzzle.workflow.model.WorkflowInput;
import com.processpuzzle.workflow.model.RoleDefinitionInput;
import com.processpuzzle.workflow.model.TaskDefinitionInput;
import com.processpuzzle.workflow.model.ToolDefinitionInput;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each of the five definition endpoints is a thin delegation layer, so one test per endpoint that
 * walks all its operations is the whole contract worth asserting: the right use case is called with
 * the org key and id from the path, and the response carries the mapped model and status. The four
 * catalog endpoints are now identical in shape — org-scoped, no process in the path — which is
 * exactly what the reference-based redesign set out to achieve.
 */
class WorkflowDefinitionEndpointsTest {

    private static final String ORG = "org-1";

    private ActiveProcessInstanceExistencePort existencePort;
    private WorkflowDefinitionMapper mapper;

    @BeforeEach
    void setUp() {
        existencePort = mock(ActiveProcessInstanceExistencePort.class);
        mapper = new WorkflowDefinitionMapper(existencePort);
    }

    @Test
    void processDefinitionsEndpoint_allMethods() throws IOException {
        CreateProcessDefinitionUseCase createUseCase = mock(CreateProcessDefinitionUseCase.class);
        ReplaceProcessDefinitionUseCase replaceUseCase = mock(ReplaceProcessDefinitionUseCase.class);
        DeleteProcessDefinitionUseCase deleteUseCase = mock(DeleteProcessDefinitionUseCase.class);
        FindProcessDefinitionUseCase findUseCase = mock(FindProcessDefinitionUseCase.class);
        FindAllProcessDefinitionsUseCase findAllUseCase = mock(FindAllProcessDefinitionsUseCase.class);
        ImportProcessDefinitionsUseCase importUseCase = mock(ImportProcessDefinitionsUseCase.class);
        ExportProcessDefinitionUseCase exportUseCase = mock(ExportProcessDefinitionUseCase.class);

        ProcessDefinitionsEndpoint endpoint = new ProcessDefinitionsEndpoint(
                createUseCase, replaceUseCase, deleteUseCase, findUseCase, findAllUseCase,
                importUseCase, exportUseCase, mapper);

        Workflow domain = Workflow.builder().orgKey(ORG).id("p1").name("Process 1").build();

        when(createUseCase.create(eq(ORG), any(Workflow.class))).thenReturn(domain);
        when(findUseCase.findByOrgKeyAndId(ORG, "p1")).thenReturn(domain);
        when(replaceUseCase.replace(eq(ORG), eq("p1"), any(Workflow.class))).thenReturn(domain);
        when(findAllUseCase.findAll(ORG, null, null, null, null)).thenReturn(new PageImpl<>(List.of(domain)));
        when(importUseCase.execute(eq(ORG), any(InputStream.class))).thenReturn(new ImportOutcome(1, 0, List.of()));
        when(exportUseCase.execute(ORG, "p1")).thenReturn("processes: []".getBytes(StandardCharsets.UTF_8));

        WorkflowInput input = new WorkflowInput().id("p1").name("Process 1");

        // create
        ResponseEntity<com.processpuzzle.workflow.model.Workflow> createdRes = endpoint.createProcessDefinition(ORG, input);
        assertThat(createdRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdRes.getBody().getId()).isEqualTo("p1");

        // get
        ResponseEntity<com.processpuzzle.workflow.model.Workflow> getRes = endpoint.getProcessDefinition(ORG, "p1");
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRes.getBody().getId()).isEqualTo("p1");

        // update
        ResponseEntity<com.processpuzzle.workflow.model.Workflow> updateRes = endpoint.updateProcessDefinition(ORG, "p1", input);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // list
        ResponseEntity<PageOfWorkflow> listRes = endpoint.listProcessDefinitions(ORG, null, null, null, null);
        assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listRes.getBody().getContent()).hasSize(1);

        // delete
        ResponseEntity<Void> deleteRes = endpoint.deleteProcessDefinition(ORG, "p1");
        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteUseCase).delete(ORG, "p1");

        // import
        MockMultipartFile file = new MockMultipartFile("file", "test.yaml", "text/yaml", "processes: []".getBytes());
        ResponseEntity<ImportResult> importRes = endpoint.importProcessDefinitions(ORG, file);
        assertThat(importRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // export
        ResponseEntity<Resource> exportRes = endpoint.exportProcessDefinition(ORG, "p1");
        assertThat(exportRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exportRes.getBody()).isNotNull();
    }

    @Test
    void roleDefinitionsEndpoint_allMethods() {
        CreateRoleDefinitionUseCase createUseCase = mock(CreateRoleDefinitionUseCase.class);
        ReplaceRoleDefinitionUseCase replaceUseCase = mock(ReplaceRoleDefinitionUseCase.class);
        DeleteRoleDefinitionUseCase deleteUseCase = mock(DeleteRoleDefinitionUseCase.class);
        FindRoleDefinitionUseCase findUseCase = mock(FindRoleDefinitionUseCase.class);
        FindAllRoleDefinitionsUseCase findAllUseCase = mock(FindAllRoleDefinitionsUseCase.class);

        RoleDefinitionsEndpoint endpoint = new RoleDefinitionsEndpoint(
                createUseCase, replaceUseCase, deleteUseCase, findUseCase, findAllUseCase, mapper);

        RoleDefinition role = RoleDefinition.builder().orgKey(ORG).id("dev").name("Developer").build();

        when(findAllUseCase.findAll(ORG, null, null)).thenReturn(List.of(role));
        when(findUseCase.findByOrgKeyAndId(ORG, "dev")).thenReturn(role);
        when(createUseCase.create(eq(ORG), any())).thenReturn(role);
        when(replaceUseCase.replace(eq(ORG), eq("dev"), any())).thenReturn(role);

        // list
        ResponseEntity<List<com.processpuzzle.workflow.model.RoleDefinition>> listRes = endpoint.listRoleDefinitions(ORG, null, null);
        assertThat(listRes.getBody()).hasSize(1);

        // create
        RoleDefinitionInput input = new RoleDefinitionInput().id("dev").name("Developer");
        ResponseEntity<com.processpuzzle.workflow.model.RoleDefinition> createRes = endpoint.createRoleDefinition(ORG, input);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // get
        ResponseEntity<com.processpuzzle.workflow.model.RoleDefinition> getRes = endpoint.getRoleDefinition(ORG, "dev");
        assertThat(getRes.getBody().getId()).isEqualTo("dev");

        // update
        ResponseEntity<com.processpuzzle.workflow.model.RoleDefinition> updateRes = endpoint.updateRoleDefinition(ORG, "dev", input);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // delete
        ResponseEntity<Void> deleteRes = endpoint.deleteRoleDefinition(ORG, "dev");
        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteUseCase).delete(ORG, "dev");
    }

    @Test
    void artifactDefinitionsEndpoint_allMethods() {
        CreateArtifactDefinitionUseCase createUseCase = mock(CreateArtifactDefinitionUseCase.class);
        ReplaceArtifactDefinitionUseCase replaceUseCase = mock(ReplaceArtifactDefinitionUseCase.class);
        DeleteArtifactDefinitionUseCase deleteUseCase = mock(DeleteArtifactDefinitionUseCase.class);
        FindArtifactDefinitionUseCase findUseCase = mock(FindArtifactDefinitionUseCase.class);
        FindAllArtifactDefinitionsUseCase findAllUseCase = mock(FindAllArtifactDefinitionsUseCase.class);

        ArtifactDefinitionsEndpoint endpoint = new ArtifactDefinitionsEndpoint(
                createUseCase, replaceUseCase, deleteUseCase, findUseCase, findAllUseCase, mapper);

        ArtifactDefinition artifact = ArtifactDefinition.builder()
                .orgKey(ORG).id("spec").name("Specification").artifactType(ArtifactType.DOCUMENT).build();

        when(findAllUseCase.findAll(ORG, null, null)).thenReturn(List.of(artifact));
        when(findUseCase.findByOrgKeyAndId(ORG, "spec")).thenReturn(artifact);
        when(createUseCase.create(eq(ORG), any())).thenReturn(artifact);
        when(replaceUseCase.replace(eq(ORG), eq("spec"), any())).thenReturn(artifact);

        // list
        ResponseEntity<List<com.processpuzzle.workflow.model.ArtifactDefinition>> listRes = endpoint.listArtifactDefinitions(ORG, null, null);
        assertThat(listRes.getBody()).hasSize(1);

        // create
        ArtifactDefinitionInput input = new ArtifactDefinitionInput().id("spec").name("Specification")
                .artifactType(com.processpuzzle.workflow.model.ArtifactType.DOCUMENT);
        ResponseEntity<com.processpuzzle.workflow.model.ArtifactDefinition> createRes = endpoint.createArtifactDefinition(ORG, input);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // get
        ResponseEntity<com.processpuzzle.workflow.model.ArtifactDefinition> getRes = endpoint.getArtifactDefinition(ORG, "spec");
        assertThat(getRes.getBody().getId()).isEqualTo("spec");

        // update
        ResponseEntity<com.processpuzzle.workflow.model.ArtifactDefinition> updateRes = endpoint.updateArtifactDefinition(ORG, "spec", input);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // delete
        ResponseEntity<Void> deleteRes = endpoint.deleteArtifactDefinition(ORG, "spec");
        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteUseCase).delete(ORG, "spec");
    }

    @Test
    void taskDefinitionsEndpoint_allMethods() {
        CreateTaskDefinitionUseCase createUseCase = mock(CreateTaskDefinitionUseCase.class);
        ReplaceTaskDefinitionUseCase replaceUseCase = mock(ReplaceTaskDefinitionUseCase.class);
        DeleteTaskDefinitionUseCase deleteUseCase = mock(DeleteTaskDefinitionUseCase.class);
        FindTaskDefinitionUseCase findUseCase = mock(FindTaskDefinitionUseCase.class);
        FindAllTaskDefinitionsUseCase findAllUseCase = mock(FindAllTaskDefinitionsUseCase.class);

        TaskDefinitionsEndpoint endpoint = new TaskDefinitionsEndpoint(
                createUseCase, replaceUseCase, deleteUseCase, findUseCase, findAllUseCase, mapper);

        TaskDefinition task = TaskDefinition.builder().orgKey(ORG).id("code").name("Write code")
                .performedByRoles(List.of("dev")).build();

        when(findAllUseCase.findAll(ORG, null, null)).thenReturn(List.of(task));
        when(findUseCase.findByOrgKeyAndId(ORG, "code")).thenReturn(task);
        when(createUseCase.create(eq(ORG), any())).thenReturn(task);
        when(replaceUseCase.replace(eq(ORG), eq("code"), any())).thenReturn(task);

        // list
        ResponseEntity<List<com.processpuzzle.workflow.model.TaskDefinition>> listRes = endpoint.listTaskDefinitions(ORG, null, null);
        assertThat(listRes.getBody()).hasSize(1);

        // create
        TaskDefinitionInput input = new TaskDefinitionInput().id("code").name("Write code").performedByRoles(List.of("dev"));
        ResponseEntity<com.processpuzzle.workflow.model.TaskDefinition> createRes = endpoint.createTaskDefinition(ORG, input);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // get
        ResponseEntity<com.processpuzzle.workflow.model.TaskDefinition> getRes = endpoint.getTaskDefinition(ORG, "code");
        assertThat(getRes.getBody().getId()).isEqualTo("code");

        // update
        ResponseEntity<com.processpuzzle.workflow.model.TaskDefinition> updateRes = endpoint.updateTaskDefinition(ORG, "code", input);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // delete
        ResponseEntity<Void> deleteRes = endpoint.deleteTaskDefinition(ORG, "code");
        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteUseCase).delete(ORG, "code");
    }

    @Test
    void toolDefinitionsEndpoint_allMethods() {
        CreateToolDefinitionUseCase createUseCase = mock(CreateToolDefinitionUseCase.class);
        ReplaceToolDefinitionUseCase replaceUseCase = mock(ReplaceToolDefinitionUseCase.class);
        DeleteToolDefinitionUseCase deleteUseCase = mock(DeleteToolDefinitionUseCase.class);
        FindToolDefinitionUseCase findUseCase = mock(FindToolDefinitionUseCase.class);
        FindAllToolDefinitionsUseCase findAllUseCase = mock(FindAllToolDefinitionsUseCase.class);

        ToolDefinitionsEndpoint endpoint = new ToolDefinitionsEndpoint(
                createUseCase, replaceUseCase, deleteUseCase, findUseCase, findAllUseCase, mapper);

        ToolDefinition tool = ToolDefinition.builder().orgKey(ORG).id("tool1").name("Tool One").build();

        when(findAllUseCase.findAll(ORG, null, null)).thenReturn(List.of(tool));
        when(findUseCase.findByOrgKeyAndId(ORG, "tool1")).thenReturn(tool);
        when(createUseCase.create(eq(ORG), any())).thenReturn(tool);
        when(replaceUseCase.replace(eq(ORG), eq("tool1"), any())).thenReturn(tool);

        // list
        ResponseEntity<List<com.processpuzzle.workflow.model.ToolDefinition>> listRes = endpoint.listToolDefinitions(ORG, null, null);
        assertThat(listRes.getBody()).hasSize(1);

        // create
        ToolDefinitionInput input = new ToolDefinitionInput().id("tool1").name("Tool One").baseUrl(URI.create("https://example.com"));
        ResponseEntity<com.processpuzzle.workflow.model.ToolDefinition> createRes = endpoint.createToolDefinition(ORG, input);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // get
        ResponseEntity<com.processpuzzle.workflow.model.ToolDefinition> getRes = endpoint.getToolDefinition(ORG, "tool1");
        assertThat(getRes.getBody().getId()).isEqualTo("tool1");

        // update
        ResponseEntity<com.processpuzzle.workflow.model.ToolDefinition> updateRes = endpoint.updateToolDefinition(ORG, "tool1", input);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // delete
        ResponseEntity<Void> deleteRes = endpoint.deleteToolDefinition(ORG, "tool1");
        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteUseCase).delete(ORG, "tool1");
    }

    /**
     * The link that was missing. {@code ReplaceProcessDefinitionUseCase} has always compared the
     * caller's version against the stored one, but {@code version} lived only on the read schema, so
     * over HTTP that comparison read a field nothing populated and the contract's promise of
     * lost-update protection was unreachable. These assert the version actually arrives; the guard
     * itself is covered by {@code ProcessDefinitionUseCasesTest}.
     */
    @Test
    void updateProcessDefinition_forwardsTheCallersVersion() {
        ReplaceProcessDefinitionUseCase replaceUseCase = mock(ReplaceProcessDefinitionUseCase.class);
        ProcessDefinitionsEndpoint endpoint = new ProcessDefinitionsEndpoint(
                mock(CreateProcessDefinitionUseCase.class), replaceUseCase,
                mock(DeleteProcessDefinitionUseCase.class), mock(FindProcessDefinitionUseCase.class),
                mock(FindAllProcessDefinitionsUseCase.class), mock(ImportProcessDefinitionsUseCase.class),
                mock(ExportProcessDefinitionUseCase.class), mapper);
        Workflow saved = Workflow.builder().orgKey(ORG).id("p1").name("Process 1").build();
        when(replaceUseCase.replace(eq(ORG), eq("p1"), any(Workflow.class))).thenReturn(saved);

        endpoint.updateProcessDefinition(ORG, "p1", new WorkflowInput().id("p1").name("Process 1").version(3L));
        // Omitting it stays an unconditional overwrite, so a client that never read a version still works.
        endpoint.updateProcessDefinition(ORG, "p1", new WorkflowInput().id("p1").name("Process 1"));

        ArgumentCaptor<Workflow> forwarded = ArgumentCaptor.captor();
        verify(replaceUseCase, times(2)).replace(eq(ORG), eq("p1"), forwarded.capture());
        assertThat(forwarded.getAllValues()).extracting(Workflow::getVersion).containsExactly(3L, null);
    }

    /** The same wiring for a catalog PUT, which shares the guard and shared the gap. */
    @Test
    void updateRoleDefinition_forwardsTheCallersVersion() {
        ReplaceRoleDefinitionUseCase replaceUseCase = mock(ReplaceRoleDefinitionUseCase.class);
        RoleDefinitionsEndpoint endpoint = new RoleDefinitionsEndpoint(
                mock(CreateRoleDefinitionUseCase.class), replaceUseCase,
                mock(DeleteRoleDefinitionUseCase.class), mock(FindRoleDefinitionUseCase.class),
                mock(FindAllRoleDefinitionsUseCase.class), mapper);
        when(replaceUseCase.replace(eq(ORG), eq("r1"), any(RoleDefinition.class)))
                .thenReturn(RoleDefinition.builder().orgKey(ORG).id("r1").name("Role").build());

        endpoint.updateRoleDefinition(ORG, "r1", new RoleDefinitionInput().id("r1").name("Role").version(5L));

        ArgumentCaptor<RoleDefinition> forwarded = ArgumentCaptor.captor();
        verify(replaceUseCase).replace(eq(ORG), eq("r1"), forwarded.capture());
        assertThat(forwarded.getValue().getVersion()).isEqualTo(5L);
    }
}
