package com.processpuzzle.workflow.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultWorkflowInstanceAccessTest {

    private static final String ORG = "org-1";

    private final WorkflowInstanceRepository repository = mock(WorkflowInstanceRepository.class);
    private final DefaultWorkflowInstanceAccess access = new DefaultWorkflowInstanceAccess(repository);

    private WorkflowInstance instance(UUID id, String workflowId, WorkflowInstanceStatus status) {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(id);
        instance.setWorkflowId(workflowId);
        instance.setStatus(status);
        when(repository.findByOrgKeyAndId(ORG, id)).thenReturn(Optional.of(instance));
        return instance;
    }

    @Test
    void findsAnInstanceOfTheNamedWorkflowWithItsStatus() {
        UUID id = UUID.randomUUID();
        instance(id, "onboarding", WorkflowInstanceStatus.COMPLETED);

        assertThat(access.find(ORG, "onboarding", id)).contains(new WorkflowInstanceView(id, "onboarding", "COMPLETED"));
    }

    @Test
    void anInstanceOfAnotherWorkflowIsNotFound() {
        UUID id = UUID.randomUUID();
        instance(id, "offboarding", WorkflowInstanceStatus.ACTIVE);

        assertThat(access.find(ORG, "onboarding", id)).isEmpty();
    }

    @Test
    void anUnknownInstanceIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByOrgKeyAndId(ORG, id)).thenReturn(Optional.empty());

        assertThat(access.find(ORG, "onboarding", id)).isEmpty();
    }

    @Test
    void listsEveryStatusAnInstanceCanHave() {
        assertThat(access.statuses()).containsExactlyInAnyOrder("ACTIVE", "COMPLETED", "CANCELLED", "SUSPENDED");
    }
}
