package com.processpuzzle.workflow.execution.adapters.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import java.util.List;
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

    @Test
    void countsOnlyTheInstancesOfTheGivenWorkflows() {
        WorkflowInstanceRepository repository = mock(WorkflowInstanceRepository.class);
        when(repository.countByOrgKeyAndWorkflowIdIn("acme", List.of("inv-restock"))).thenReturn(5L);
        WorkflowInstanceDataProbe probe = new WorkflowInstanceDataProbe(repository);

        assertThat(probe.definitionKind()).isEqualTo("workflow");
        assertThat(probe.count("acme", List.of("inv-restock"))).isEqualTo(5L);
        assertThat(probe.count("acme", List.of())).isZero();
    }
}
