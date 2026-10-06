package com.processpuzzle.workflow.execution.adapters.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import org.junit.jupiter.api.Test;

class WorkflowInstanceDataProbeTest {

    @Test
    void countsTheWorkflowInstancesOfTheOrganization() {
        WorkflowInstanceRepository repository = mock(WorkflowInstanceRepository.class);
        when(repository.countByOrgKey("acme")).thenReturn(2L);
        WorkflowInstanceDataProbe probe = new WorkflowInstanceDataProbe(repository);

        assertThat(probe.count("acme")).isEqualTo(2L);
        assertThat(probe.label()).isEqualTo("workflow instances");
    }
}
