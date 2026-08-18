package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.shared.model.ImportResult;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateToolDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteToolDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ExportProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllProcessDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllToolDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindToolDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportProcessDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceRoleDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceToolDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.outbound.ActiveProcessInstanceExistencePort;
import com.processpuzzle.workflow.model.PageOfProcessDefinitionSummary;
import com.processpuzzle.workflow.model.ProcessDefinitionInput;
import com.processpuzzle.workflow.model.RoleDefinitionInput;
import com.processpuzzle.workflow.model.TaskDefinitionInput;
import com.processpuzzle.workflow.model.ToolDefinitionInput;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        ProcessDefinition domain = ProcessDefinition.builder().orgKey(ORG).id("p1").name("Process 1").build();

        when(createUseCase.create(eq(ORG), any(ProcessDefinition.class))).thenReturn(domain);
        when(findUseCase.findByOrgKeyAndId(ORG, "p1")).thenReturn(domain);
        when(replaceUseCase.replace(eq(ORG), eq("p1"), any(ProcessDefinition.class))).thenReturn(domain);
        when(findAllUseCase.findAll(ORG, null, null, null, null)).thenReturn(new PageImpl<>(List.of(domain)));
        when(importUseCase.execute(eq(ORG), any(InputStream.class))).thenReturn(new ImportOutcome(1, 0, List.of()));
        when(exportUseCase.execute(ORG, "p1")).thenReturn("processes: []".getBytes(StandardCharsets.UTF_8));

        ProcessDefinitionInput input = new ProcessDefinitionInput().id("p1").name("Process 1");

        // create
        ResponseEntity<com.processpuzzle.workflow.model.ProcessDefinition> createdRes = endpoint.createProcessDefinition(ORG, input);
        assertThat(createdRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createdRes.getBody().getId()).isEqualTo("p1");

        // get
        ResponseEntity<com.processpuzzle.workflow.model.ProcessDefinition> getRes = endpoint.getProcessDefinition(ORG, "p1");
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRes.getBody().getId()).isEqualTo("p1");

        // update
        ResponseEntity<com.processpuzzle.workflow.model.ProcessDefinition> updateRes = endpoint.updateProcessDefinition(ORG, "p1", input);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // list
        ResponseEntity<PageOfProcessDefinitionSummary> listRes = endpoint.listProcessDefinitions(ORG, null, null, null, null);
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
        FindProcessDefinitionUseCase findUseCase = mock(FindProcessDefinitionUseCase.class);

        RoleDefinitionsEndpoint endpoint = new RoleDefinitionsEndpoint(
                createUseCase, replaceUseCase, deleteUseCase, findUseCase, mapper);

        RoleDefinition domainRole = RoleDefinition.builder().id("dev").name("Developer").build();
        ProcessDefinition proc = ProcessDefinition.builder().orgKey(ORG).id("p1")
                .roles(new ArrayList<>(List.of(domainRole))).build();

        when(findUseCase.findByOrgKeyAndId(ORG, "p1")).thenReturn(proc);
        when(createUseCase.create(eq(ORG), eq("p1"), any())).thenReturn(domainRole);
        when(replaceUseCase.replace(eq(ORG), eq("p1"), eq("dev"), any())).thenReturn(domainRole);

        // list
        ResponseEntity<List<com.processpuzzle.workflow.model.RoleDefinition>> listRes = endpoint.listRoleDefinitions(ORG, "p1", null, null);
        assertThat(listRes.getBody()).hasSize(1);

        // create
        RoleDefinitionInput input = new RoleDefinitionInput().id("dev").name("Developer");
        ResponseEntity<com.processpuzzle.workflow.model.RoleDefinition> createRes = endpoint.createRoleDefinition(ORG, "p1", input);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // get
        ResponseEntity<com.processpuzzle.workflow.model.RoleDefinition> getRes = endpoint.getRoleDefinition(ORG, "p1", "dev");
        assertThat(getRes.getBody().getId()).isEqualTo("dev");

        // update
        ResponseEntity<com.processpuzzle.workflow.model.RoleDefinition> updateRes = endpoint.updateRoleDefinition(ORG, "p1", "dev", input);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // delete
        ResponseEntity<Void> deleteRes = endpoint.deleteRoleDefinition(ORG, "p1", "dev");
        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteUseCase).delete(ORG, "p1", "dev");
    }

    @Test
    void taskDefinitionsEndpoint_allMethods() {
        CreateTaskDefinitionUseCase createUseCase = mock(CreateTaskDefinitionUseCase.class);
        ReplaceTaskDefinitionUseCase replaceUseCase = mock(ReplaceTaskDefinitionUseCase.class);
        DeleteTaskDefinitionUseCase deleteUseCase = mock(DeleteTaskDefinitionUseCase.class);
        FindProcessDefinitionUseCase findUseCase = mock(FindProcessDefinitionUseCase.class);

        TaskDefinitionsEndpoint endpoint = new TaskDefinitionsEndpoint(
                createUseCase, replaceUseCase, deleteUseCase, findUseCase, mapper);

        TaskDefinition domainTask = TaskDefinition.builder().id("code").name("Write code").build();
        ProcessDefinition proc = ProcessDefinition.builder().orgKey(ORG).id("p1")
                .tasks(new ArrayList<>(List.of(domainTask))).build();

        when(findUseCase.findByOrgKeyAndId(ORG, "p1")).thenReturn(proc);
        when(createUseCase.create(eq(ORG), eq("p1"), any())).thenReturn(domainTask);
        when(replaceUseCase.replace(eq(ORG), eq("p1"), eq("code"), any())).thenReturn(domainTask);

        // list
        ResponseEntity<List<com.processpuzzle.workflow.model.TaskDefinition>> listRes = endpoint.listTaskDefinitions(ORG, "p1", null, null);
        assertThat(listRes.getBody()).hasSize(1);

        // create
        TaskDefinitionInput input = new TaskDefinitionInput().id("code").name("Write code");
        ResponseEntity<com.processpuzzle.workflow.model.TaskDefinition> createRes = endpoint.createTaskDefinition(ORG, "p1", input);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // get
        ResponseEntity<com.processpuzzle.workflow.model.TaskDefinition> getRes = endpoint.getTaskDefinition(ORG, "p1", "code");
        assertThat(getRes.getBody().getId()).isEqualTo("code");

        // update
        ResponseEntity<com.processpuzzle.workflow.model.TaskDefinition> updateRes = endpoint.updateTaskDefinition(ORG, "p1", "code", input);
        assertThat(updateRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // delete
        ResponseEntity<Void> deleteRes = endpoint.deleteTaskDefinition(ORG, "p1", "code");
        assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteUseCase).delete(ORG, "p1", "code");
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
}
