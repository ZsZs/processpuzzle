package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.EventDirection;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagram;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagramRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.usecases.inbound.SyncRoleDirectory;
import com.processpuzzle.workflow.definition.usecases.outbound.RoleDirectoryPort;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.StartTimerSchedule;
import com.processpuzzle.workflow.execution.domain.StartTimerScheduleRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceCounter;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceCounterRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.i18n.WorkflowTranslationBundle;
import com.processpuzzle.workflow.i18n.WorkflowTranslationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Erasing one tenant removes every row this feature holds for it, and nothing of another's.
 *
 * <p>Against a real transaction, because that is the whole correctness argument: the listener is
 * {@code BEFORE_COMMIT}, and an {@code AFTER_COMMIT} one would have its deletes discarded without an
 * error. Publishing the event inside a committed transaction and finding the rows gone is what proves
 * the phase, which is why the class suspends {@code @DataJpaTest}'s own rolled-back transaction.
 *
 * <p>H2 in PostgreSQL mode, because instances, definitions and translation bundles map their documents
 * as {@code jsonb}. The role directory is a mock behind the real {@link SyncRoleDirectory}, so the
 * reserved-name skip under test is the production one.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:workflow-erasure;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan({"com.processpuzzle.workflow.execution.domain", "com.processpuzzle.workflow.definition.domain",
        "com.processpuzzle.workflow.i18n"})
@EnableJpaRepositories({"com.processpuzzle.workflow.execution.domain",
        "com.processpuzzle.workflow.definition.domain", "com.processpuzzle.workflow.i18n"})
@Import({TenantDataCleaner.class, SyncRoleDirectory.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantDataCleanerTest {

    private static final String ERASED = "erased-org";
    private static final String KEPT = "kept-org";

    /** Every table of this feature; each has its own {@code org_key}, which is what makes the check uniform. */
    private static final List<String> TABLES = List.of(
            "workflow_event_instance", "workflow_task_instance", "workflow_artifact_instance", "workflow_instance",
            "workflow_instance_counter", "workflow_start_timer_schedule", "workflow_diagram", "workflow_definition",
            "workflow_task_definition", "workflow_artifact_definition", "workflow_role_definition",
            "workflow_tool_definition", "workflow_translations");

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {

        @Bean
        RoleDirectoryPort roleDirectoryPort() {
            return mock(RoleDirectoryPort.class);
        }
    }

    @Autowired private EventInstanceRepository eventInstances;
    @Autowired private TaskInstanceRepository taskInstances;
    @Autowired private ArtifactInstanceRepository artifactInstances;
    @Autowired private WorkflowInstanceRepository workflowInstances;
    @Autowired private WorkflowInstanceCounterRepository instanceCounters;
    @Autowired private StartTimerScheduleRepository startTimerSchedules;
    @Autowired private WorkflowDiagramRepository diagrams;
    @Autowired private WorkflowRepository workflows;
    @Autowired private TaskDefinitionRepository taskDefinitions;
    @Autowired private ArtifactDefinitionRepository artifactDefinitions;
    @Autowired private RoleDefinitionRepository roleDefinitions;
    @Autowired private ToolDefinitionRepository toolDefinitions;
    @Autowired private WorkflowTranslationRepository translations;

    @Autowired
    private RoleDirectoryPort roleDirectory;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void erasesEveryRowOfTheTenantAndNothingOfAnother() {
        givenAWorkflowWithARunningInstanceFor(ERASED);
        givenAWorkflowWithARunningInstanceFor(KEPT);

        erase(ERASED);

        assertThat(TABLES).allSatisfy(table ->
                assertThat(count(table, ERASED)).as("%s of the erased tenant", table).isZero());
        assertThat(TABLES).allSatisfy(table ->
                assertThat(count(table, KEPT)).as("%s of the kept tenant", table).isEqualTo(expectedRows(table)));
    }

    /** Only once the rows are committed gone, and through the same removal a single role delete takes. */
    @Test
    void removesTheTenantsRolesFromTheDirectoryAfterCommit() {
        givenAWorkflowWithARunningInstanceFor(ERASED);

        erase(ERASED);

        verify(roleDirectory).deleteRole(ERASED, "reviewer");
        verify(roleDirectory).deleteRole(ERASED, "approver");
    }

    /** {@code org-admin} names the platform's own role; deleting a definition that claims it must not revoke it. */
    @Test
    void leavesReservedRolesInTheDirectory() {
        givenAWorkflowWithARunningInstanceFor(ERASED);
        roleDefinitions.saveAndFlush(role(ERASED, "org-admin"));
        roleDefinitions.saveAndFlush(role(ERASED, "default-roles-erased-org"));

        erase(ERASED);

        assertThat(count("workflow_role_definition", ERASED)).as("the rows go regardless").isZero();
        verify(roleDirectory, never()).deleteRole(anyString(), eq("org-admin"));
        verify(roleDirectory, never()).deleteRole(anyString(), eq("default-roles-erased-org"));
        verify(roleDirectory).deleteRole(ERASED, "reviewer");
    }

    /** After commit nobody can catch it: a failing directory is logged, and the other roles still go. */
    @Test
    void aFailingDirectoryDoesNotFailTheErasure() {
        givenAWorkflowWithARunningInstanceFor(ERASED);
        doThrow(new IllegalStateException("directory down")).when(roleDirectory).deleteRole(ERASED, "reviewer");
        doThrow(new IllegalStateException("directory down")).when(roleDirectory).deleteRole(ERASED, "approver");

        erase(ERASED);

        assertThat(TABLES).allSatisfy(table -> assertThat(count(table, ERASED)).as(table).isZero());
        verify(roleDirectory).deleteRole(ERASED, "reviewer");
        verify(roleDirectory).deleteRole(ERASED, "approver");
    }

    @Test
    void aRolledBackErasureDeletesNothingAndRemovesNoRole() {
        givenAWorkflowWithARunningInstanceFor(ERASED);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new OrganizationDeletedEvent(ERASED));
            status.setRollbackOnly();
        });

        assertThat(TABLES).allSatisfy(table ->
                assertThat(count(table, ERASED)).as(table).isEqualTo(expectedRows(table)));
        verify(roleDirectory, never()).deleteRole(anyString(), anyString());
    }

    /** A tenant with nothing here — or erased twice — is not an error. */
    @Test
    void erasingATenantWithNothingIsHarmless() {
        erase("nobody");

        assertThat(TABLES).allSatisfy(table -> assertThat(count(table, "nobody")).as(table).isZero());
        verify(roleDirectory, never()).deleteRole(anyString(), anyString());
    }

    private void erase(String orgKey) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new OrganizationDeletedEvent(orgKey)));
    }

    /** One row in every table, two roles; {@link #expectedRows} has to agree. */
    private void givenAWorkflowWithARunningInstanceFor(String orgKey) {
        roleDefinitions.saveAndFlush(role(orgKey, "reviewer"));
        roleDefinitions.saveAndFlush(role(orgKey, "approver"));
        taskDefinitions.saveAndFlush(TaskDefinition.builder().orgKey(orgKey).id("review").name("Review").build());
        artifactDefinitions.saveAndFlush(ArtifactDefinition.builder().orgKey(orgKey).id("order").name("Order")
                .artifactType(ArtifactType.ENTITY).build());
        toolDefinitions.saveAndFlush(ToolDefinition.builder().orgKey(orgKey).id("erp").name("ERP")
                .baseUrl("https://erp.example.com").build());
        workflows.saveAndFlush(Workflow.builder().orgKey(orgKey).id("approval").name("Approval").build());
        diagrams.saveAndFlush(WorkflowDiagram.builder().orgKey(orgKey).workflowId("approval").build());
        startTimerSchedules.saveAndFlush(StartTimerSchedule.builder().orgKey(orgKey).workflowId("approval")
                .startEventId("every-morning").dueAt(Instant.now()).specHash("hash").build());

        UUID instanceId = workflowInstances.saveAndFlush(WorkflowInstance.builder().orgKey(orgKey).instanceNumber(1L)
                .workflowId("approval").workflowName("Approval").status(WorkflowInstanceStatus.ACTIVE)
                .startedAt(Instant.now()).build()).getId();
        instanceCounters.saveAndFlush(new WorkflowInstanceCounter(orgKey, 1L));
        taskInstances.saveAndFlush(TaskInstance.builder().orgKey(orgKey).workflowInstanceId(instanceId)
                .taskDefinitionId("review").name("Review").status(TaskInstanceStatus.ACTIVE).build());
        artifactInstances.saveAndFlush(ArtifactInstance.builder().orgKey(orgKey).workflowInstanceId(instanceId)
                .artifactDefinitionId("order").name("Order").type(ArtifactType.ENTITY).updatedAt(Instant.now())
                .build());
        eventInstances.saveAndFlush(EventInstance.builder().orgKey(orgKey).workflowInstanceId(instanceId)
                .eventUseId("reminder").direction(EventDirection.CATCH).status(EventInstanceStatus.WAITING)
                .dueAt(Instant.now()).build());
        translations.saveAndFlush(new WorkflowTranslationBundle(orgKey, "workflow", "en", Map.of("title", "Workflows")));
    }

    private static long expectedRows(String table) {
        return "workflow_role_definition".equals(table) ? 2 : 1;
    }

    private static RoleDefinition role(String orgKey, String id) {
        return RoleDefinition.builder().orgKey(orgKey).id(id).name(id).build();
    }

    private long count(String table, String orgKey) {
        Long count = jdbc.queryForObject("select count(*) from " + table + " where org_key = ?", Long.class, orgKey);
        return count == null ? 0 : count;
    }

    @AfterEach
    void cleanUp() {
        TABLES.forEach(table -> jdbc.update("delete from " + table));
        reset(roleDirectory);
    }
}
