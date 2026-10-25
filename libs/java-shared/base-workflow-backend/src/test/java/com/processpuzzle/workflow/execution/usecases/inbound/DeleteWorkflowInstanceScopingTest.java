package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.domain.EventDirection;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting an instance removes its tasks, events and artifacts and leaves every other instance's rows alone —
 * the scoping lives in JPQL, which the unit test's mocks cannot check. H2 in PostgreSQL mode, because the
 * instance tables map their documents as {@code jsonb}.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:workflow-instance-delete;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.workflow.execution.domain")
@EnableJpaRepositories("com.processpuzzle.workflow.execution.domain")
@Import(DeleteWorkflowInstanceUseCase.class)
class DeleteWorkflowInstanceScopingTest {

    private static final String ORG = "acme";

    private long lastNumber;

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired private DeleteWorkflowInstanceUseCase useCase;
    @Autowired private WorkflowInstanceRepository instances;
    @Autowired private TaskInstanceRepository tasks;
    @Autowired private EventInstanceRepository events;
    @Autowired private ArtifactInstanceRepository artifacts;

    @Test
    void deletesOneInstanceWithItsRowsAndNoOtherInstancesRows() {
        UUID doomed = finishedInstanceWithRows(ORG);
        UUID sibling = finishedInstanceWithRows(ORG);
        UUID ofAnotherOrg = finishedInstanceWithRows("other-org");

        useCase.delete(ORG, doomed);

        assertThat(instances.findByOrgKeyAndId(ORG, doomed)).isEmpty();
        assertThat(tasks.findByOrgKeyAndWorkflowInstanceId(ORG, doomed)).isEmpty();
        assertThat(events.findByOrgKeyAndWorkflowInstanceId(ORG, doomed)).isEmpty();
        assertThat(artifacts.findByOrgKeyAndWorkflowInstanceId(ORG, doomed)).isEmpty();

        assertThat(instances.findByOrgKeyAndId(ORG, sibling)).isPresent();
        assertThat(tasks.findByOrgKeyAndWorkflowInstanceId(ORG, sibling)).hasSize(1);
        assertThat(events.findByOrgKeyAndWorkflowInstanceId(ORG, sibling)).hasSize(1);
        assertThat(artifacts.findByOrgKeyAndWorkflowInstanceId(ORG, sibling)).hasSize(1);
        assertThat(instances.findByOrgKeyAndId("other-org", ofAnotherOrg)).isPresent();
        assertThat(tasks.findByOrgKeyAndWorkflowInstanceId("other-org", ofAnotherOrg)).hasSize(1);
    }

    private UUID finishedInstanceWithRows(String orgKey) {
        UUID id = instances.saveAndFlush(WorkflowInstance.builder().orgKey(orgKey).instanceNumber(++lastNumber)
                .workflowId("approval").workflowName("Approval").status(WorkflowInstanceStatus.COMPLETED)
                .startedAt(Instant.now()).build()).getId();
        tasks.saveAndFlush(TaskInstance.builder().orgKey(orgKey).workflowInstanceId(id)
                .taskDefinitionId("review").name("Review").status(TaskInstanceStatus.COMPLETED).build());
        artifacts.saveAndFlush(ArtifactInstance.builder().orgKey(orgKey).workflowInstanceId(id)
                .artifactDefinitionId("order").name("Order").type(ArtifactType.ENTITY).updatedAt(Instant.now())
                .build());
        events.saveAndFlush(EventInstance.builder().orgKey(orgKey).workflowInstanceId(id)
                .eventUseId("reminder").direction(EventDirection.CATCH).status(EventInstanceStatus.OCCURRED)
                .build());
        return id;
    }
}
