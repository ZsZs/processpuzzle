package com.processpuzzle.workflow.definition.adapters.inbound.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.DiagramViewport;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.RoleUse;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.TaskUse;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagram;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagramRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.event.RoleDefinitionChangedEvent;
import com.processpuzzle.workflow.definition.domain.event.RoleDefinitionDeletedEvent;
import com.processpuzzle.workflow.definition.domain.event.WorkflowChangedEvent;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Releasing the workflow definitions over a real repository and real transactions: the catalogue kinds share one
 * mechanism ({@link DefinitionReleaseParticipant}), the workflow carries its diagram, and the release, not a draft's
 * save, announces what the role directory and the start timers follow.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:workflow-release;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan({"com.processpuzzle.workflow.execution.domain", "com.processpuzzle.workflow.definition.domain"})
@EnableJpaRepositories({"com.processpuzzle.workflow.execution.domain", "com.processpuzzle.workflow.definition.domain"})
@Import({RoleReleaseParticipant.class, TaskReleaseParticipant.class, WorkflowReleaseParticipant.class,
        WorkflowReleaseParticipantsTest.Announcements.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowReleaseParticipantsTest {

    private static final String ORG = "my-org";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    /** What the release announced, in order. */
    static class Announcements {
        final List<Object> events = new ArrayList<>();

        @EventListener
        void on(RoleDefinitionChangedEvent event) {
            events.add(event);
        }

        @EventListener
        void on(RoleDefinitionDeletedEvent event) {
            events.add(event);
        }

        @EventListener
        void on(WorkflowChangedEvent event) {
            events.add(event);
        }
    }

    @Autowired private RoleReleaseParticipant roles;
    @Autowired private TaskReleaseParticipant tasks;
    @Autowired private WorkflowReleaseParticipant workflows;
    @Autowired private RoleDefinitionRepository roleRepository;
    @Autowired private TaskDefinitionRepository taskRepository;
    @Autowired private ArtifactDefinitionRepository artifactRepository;
    @Autowired private WorkflowRepository workflowRepository;
    @Autowired private WorkflowDiagramRepository diagramRepository;
    @Autowired private WorkflowInstanceRepository instanceRepository;
    @Autowired private TaskInstanceRepository taskInstanceRepository;
    @Autowired private Announcements announcements;
    @Autowired private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        taskInstanceRepository.deleteAll();
        instanceRepository.deleteAll();
        diagramRepository.deleteAll();
        workflowRepository.deleteAll();
        taskRepository.deleteAll();
        roleRepository.deleteAll();
        artifactRepository.deleteAll();
        announcements.events.clear();
    }

    @Test
    void aRoleIsReleasedInPlace_andTheReleaseAnnouncesItForTheDirectory() {
        save(roleRepository, role("reviewer", "Reviewer"));
        assertThat(roles.pending(ORG)).extracting(PendingChange::key, PendingChange::change)
                .containsExactly(tuple("reviewer", PendingChange.Change.ADDED));

        release(() -> roles.release(ORG));
        RoleDefinition released = roleRepository.findByOrgKeyAndIdAndChannel(ORG, "reviewer", Channel.RELEASED).orElseThrow();
        assertThat(released.getName()).isEqualTo("Reviewer");
        assertThat(roles.pending(ORG)).isEmpty();
        assertThat(announcements.events).containsExactly(new RoleDefinitionChangedEvent(ORG, "reviewer", "Reviewer", null));

        transaction.executeWithoutResult(status -> {
            RoleDefinition draft = roleRepository.findByOrgKeyAndIdAndChannel(ORG, "reviewer", Channel.DRAFT).orElseThrow();
            draft.setName("Senior reviewer");
            roleRepository.save(draft);
        });
        release(() -> roles.release(ORG));
        assertThat(roleRepository.findByOrgKeyAndIdAndChannel(ORG, "reviewer", Channel.RELEASED).orElseThrow().getVersion())
                .isEqualTo(released.getVersion() + 1);

        transaction.executeWithoutResult(status ->
                roleRepository.delete(roleRepository.findByOrgKeyAndIdAndChannel(ORG, "reviewer", Channel.DRAFT).orElseThrow()));
        release(() -> roles.release(ORG));
        assertThat(roleRepository.findByOrgKeyAndIdAndChannel(ORG, "reviewer", Channel.RELEASED)).isEmpty();
        assertThat(announcements.events).endsWith(new RoleDefinitionDeletedEvent(ORG, "reviewer"));
    }

    /** An unchanged draft is not written again, nor announced again. */
    @Test
    void releasingWhatIsAlreadyReleasedChangesAndAnnouncesNothing() {
        save(roleRepository, role("reviewer", "Reviewer"));
        release(() -> roles.release(ORG));
        announcements.events.clear();

        release(() -> roles.release(ORG));

        assertThat(announcements.events).isEmpty();
    }

    @Test
    void aWorkflowCarriesItsDiagram_andAMovedLayoutIsAPendingChange() {
        save(workflowRepository, workflow("approval"));
        save(diagramRepository, diagram("approval", 1));
        release(() -> workflows.release(ORG));

        assertThat(diagramRepository.findByOrgKeyAndWorkflowIdAndChannel(ORG, "approval", Channel.RELEASED)).get()
                .extracting(WorkflowDiagram::getViewport).isEqualTo(new DiagramViewport(1, 1, 1));
        assertThat(announcements.events).containsExactly(new WorkflowChangedEvent(ORG, "approval", false));
        assertThat(workflows.pending(ORG)).isEmpty();

        transaction.executeWithoutResult(status -> {
            WorkflowDiagram draft = diagramRepository.findByOrgKeyAndWorkflowIdAndChannel(ORG, "approval", Channel.DRAFT).orElseThrow();
            draft.replaceLayout(draft.getNodes(), draft.getEdges(), new DiagramViewport(2, 2, 1));
            diagramRepository.save(draft);
        });
        assertThat(workflows.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.CHANGED);

        release(() -> workflows.release(ORG));
        assertThat(diagramRepository.findByOrgKeyAndWorkflowIdAndChannel(ORG, "approval", Channel.RELEASED)).get()
                .extracting(WorkflowDiagram::getViewport).isEqualTo(new DiagramViewport(2, 2, 1));
    }

    @Test
    void refusesToRemoveAWorkflowWhileItRuns_andATaskWhileItIsOpen() {
        save(workflowRepository, workflow("approval"));
        save(taskRepository, TaskDefinition.builder().orgKey(ORG).id("review").name("Review").build());
        release(() -> {
            tasks.release(ORG);
            workflows.release(ORG);
        });
        UUID instanceId = instanceRepository.saveAndFlush(WorkflowInstance.builder().orgKey(ORG).instanceNumber(1L)
                .workflowId("approval").workflowName("Approval").status(WorkflowInstanceStatus.ACTIVE)
                .startedAt(Instant.now()).build()).getId();
        taskInstanceRepository.saveAndFlush(TaskInstance.builder().orgKey(ORG).workflowInstanceId(instanceId)
                .taskDefinitionId("review").name("Review").status(TaskInstanceStatus.ACTIVE).build());

        transaction.executeWithoutResult(status -> {
            workflowRepository.delete(workflowRepository.findByOrgKeyAndIdAndChannel(ORG, "approval", Channel.DRAFT).orElseThrow());
            taskRepository.delete(taskRepository.findByOrgKeyAndIdAndChannel(ORG, "review", Channel.DRAFT).orElseThrow());
        });

        assertThat(workflows.check(ORG, null)).extracting(ReleaseProblem::key, ReleaseProblem::errorId)
                .containsExactly(tuple("approval", "workflow.release.workflowRemovedWhileRunning"));
        assertThat(tasks.check(ORG, null)).extracting(ReleaseProblem::key, ReleaseProblem::errorId)
                .containsExactly(tuple("review", "workflow.release.taskRemovedWhileOpen"));
    }

    @Test
    void aWorkflowRefersToItsCatalogue_andATaskToItsRoles() {
        Workflow approval = workflow("approval");
        approval.setRoles(List.of(new RoleUse("reviewer")));
        approval.setTasks(List.of(TaskUse.builder().taskDefinitionId("review").performedBy("reviewer").build()));
        save(workflowRepository, approval);
        save(taskRepository, TaskDefinition.builder().orgKey(ORG).id("review").name("Review")
                .performedByRoles(List.of("reviewer")).inputs(List.of("order")).build());
        save(artifactRepository, ArtifactDefinition.builder().orgKey(ORG).id("order").name("Order")
                .artifactType(ArtifactType.ENTITY).artifactTypeId("Order").build());

        assertThat(workflows.references(ORG)).extracting(DefinitionReference::toKind, DefinitionReference::toKey)
                .containsExactlyInAnyOrder(tuple(DefinitionKinds.ROLE, "reviewer"), tuple(DefinitionKinds.TASK, "review"));
        assertThat(tasks.references(ORG)).extracting(DefinitionReference::toKind, DefinitionReference::toKey)
                .containsExactlyInAnyOrder(tuple(DefinitionKinds.ROLE, "reviewer"), tuple(DefinitionKinds.ARTIFACT, "order"));
    }

    private void release(Runnable release) {
        transaction.executeWithoutResult(status -> release.run());
    }

    private <T> void save(org.springframework.data.jpa.repository.JpaRepository<T, ?> repository, T row) {
        transaction.executeWithoutResult(status -> repository.save(row));
    }

    private static RoleDefinition role(String id, String name) {
        return RoleDefinition.builder().orgKey(ORG).id(id).name(name).build();
    }

    private static Workflow workflow(String id) {
        return Workflow.builder().orgKey(ORG).id(id).name(id).build();
    }

    private static WorkflowDiagram diagram(String workflowId, double zoom) {
        return WorkflowDiagram.builder().orgKey(ORG).workflowId(workflowId).viewport(new DiagramViewport(zoom, zoom, 1)).build();
    }
}
