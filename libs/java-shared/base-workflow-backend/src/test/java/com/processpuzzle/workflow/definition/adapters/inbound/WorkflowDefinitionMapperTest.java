package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.shared.model.ImportResult;
import com.processpuzzle.workflow.definition.domain.HttpMethod;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.workflow.definition.usecases.outbound.ActiveProcessInstanceExistencePort;
import com.processpuzzle.workflow.model.PageOfProcessDefinitionSummary;
import com.processpuzzle.workflow.model.ProcessDefinitionInput;
import com.processpuzzle.workflow.model.ProcessDefinitionSummary;
import com.processpuzzle.workflow.model.RoleDefinitionInput;
import com.processpuzzle.workflow.model.TaskDefinitionInput;
import com.processpuzzle.workflow.model.ToolDefinitionInput;
import com.processpuzzle.workflow.model.ToolOperationInput;
import com.processpuzzle.workflow.model.WorkProductDefinitionInput;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowDefinitionMapperTest {

    private ActiveProcessInstanceExistencePort existencePort;
    private WorkflowDefinitionMapper mapper;

    @BeforeEach
    void setUp() {
        existencePort = mock(ActiveProcessInstanceExistencePort.class);
        mapper = new WorkflowDefinitionMapper(existencePort);
    }

    @Test
    void toDomain_and_toModel_processDefinition() {
        ProcessDefinitionInput input = new ProcessDefinitionInput()
                .id("proc-1")
                .name("Process 1")
                .description("Desc")
                ._extends("parent-proc")
                .tools(List.of("tool-1"))
                .roles(List.of(new RoleDefinitionInput().id("r1").name("Role 1").description("Role Desc").entityRoleId("er-1")))
                .workProducts(List.of(new WorkProductDefinitionInput().id("wp1").name("WP 1").type(com.processpuzzle.workflow.model.WorkProductType.ARTIFACT)))
                .tasks(List.of(new TaskDefinitionInput().id("t1").name("Task 1").performedBy("r1")));

        ProcessDefinition domain = mapper.toDomain("org-1", input);

        assertThat(domain.getOrgKey()).isEqualTo("org-1");
        assertThat(domain.getId()).isEqualTo("proc-1");
        assertThat(domain.getName()).isEqualTo("Process 1");
        assertThat(domain.getDescription()).isEqualTo("Desc");
        assertThat(domain.getExtendsProcessId()).isEqualTo("parent-proc");
        assertThat(domain.getTools()).containsExactly("tool-1");
        assertThat(domain.getRoles()).hasSize(1);
        assertThat(domain.getWorkProducts()).hasSize(1);
        assertThat(domain.getTasks()).hasSize(1);

        domain.setCreatedAt(Instant.now());
        domain.setUpdatedAt(Instant.now());
        domain.setVersion(1L);

        var model = mapper.toModel(domain);
        assertThat(model.getId()).isEqualTo("proc-1");
        assertThat(model.getName()).isEqualTo("Process 1");
        assertThat(model.getExtends()).isEqualTo("parent-proc");
        assertThat(model.getRoles()).hasSize(1);
        assertThat(model.getWorkProducts()).hasSize(1);
        assertThat(model.getTasks()).hasSize(1);
        assertThat(model.getVersion()).isEqualTo(1L);
    }

    @Test
    void toSummaryModel_and_pageModel() {
        ProcessDefinition domain = ProcessDefinition.builder()
                .orgKey("org-1")
                .id("proc-1")
                .name("Process 1")
                .description("Desc")
                .version(2L)
                .build();
        domain.setCreatedAt(Instant.now());
        domain.setUpdatedAt(Instant.now());

        when(existencePort.countActiveInstancesOf("org-1", "proc-1")).thenReturn(3L);

        ProcessDefinitionSummary summary = mapper.toSummaryModel(domain);
        assertThat(summary.getId()).isEqualTo("proc-1");
        assertThat(summary.getActiveInstances()).isEqualTo(3);

        PageOfProcessDefinitionSummary pageModel = mapper.toModel(new PageImpl<>(List.of(domain), PageRequest.of(0, 10), 1));
        assertThat(pageModel.getContent()).hasSize(1);
        assertThat(pageModel.getTotalElements()).isEqualTo(1);
    }

    @Test
    void toModel_importOutcome() {
        ImportOutcome outcome = new ImportOutcome(2, 1, List.of("warn"));
        ImportResult result = mapper.toModel(outcome);

        assertThat(result.getCreated()).isEqualTo(2);
        assertThat(result.getUpdated()).isEqualTo(1);
        assertThat(result.getErrors()).containsExactly("warn");
    }

    @Test
    void toToolDomain_and_toToolModel() {
        ToolDefinitionInput input = new ToolDefinitionInput()
                .id("tool-1")
                .name("Tool 1")
                .description("Tool Desc")
                .baseUrl(URI.create("https://api.example.com"))
                .auth(new com.processpuzzle.workflow.model.ToolAuthConfig().type(com.processpuzzle.workflow.model.AuthType.BEARER_TOKEN).secretRef("secret-key"))
                .operations(List.of(new ToolOperationInput()
                        .id("op-1")
                        .method(com.processpuzzle.workflow.model.HttpMethod.POST)
                        .path("/items")
                        .description("Create item")
                        .payloadTemplate("{\"name\": \"${item}\"}")
                        .expectedStatusCodes(List.of(200, 201))));

        ToolDefinition domain = mapper.toToolDomain(input);
        domain.setCreatedAt(Instant.now());
        domain.setVersion(1L);

        assertThat(domain.getId()).isEqualTo("tool-1");
        assertThat(domain.getBaseUrl()).isEqualTo("https://api.example.com");
        assertThat(domain.getAuth().getType().name()).isEqualTo("BEARER_TOKEN");
        assertThat(domain.getOperations()).hasSize(1);
        assertThat(domain.getOperations().get(0).getMethod()).isEqualTo(HttpMethod.POST);

        var model = mapper.toToolModel(domain);
        assertThat(model.getId()).isEqualTo("tool-1");
        assertThat(model.getBaseUrl()).isEqualTo(URI.create("https://api.example.com"));
        assertThat(model.getAuth().getSecretRef()).isEqualTo("secret-key");
        assertThat(model.getOperations()).hasSize(1);
    }
}
