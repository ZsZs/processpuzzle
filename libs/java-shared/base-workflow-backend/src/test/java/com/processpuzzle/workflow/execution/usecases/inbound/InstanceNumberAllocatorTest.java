package com.processpuzzle.workflow.execution.usecases.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.workflow.execution.domain.WorkflowInstanceCounter;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceCounterRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstanceNumberAllocatorTest {

    private WorkflowInstanceCounterRepository counters;
    private WorkflowInstanceRepository instances;
    private InstanceNumberAllocator allocator;

    @BeforeEach
    void setUp() {
        counters = mock(WorkflowInstanceCounterRepository.class);
        instances = mock(WorkflowInstanceRepository.class);
        when(counters.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        allocator = new InstanceNumberAllocator(counters, instances);
    }

    @Test
    void continuesTheOrganizationsCounter() {
        WorkflowInstanceCounter counter = new WorkflowInstanceCounter("org-1", 41);
        when(counters.findByOrgKey("org-1")).thenReturn(Optional.of(counter));

        assertThat(allocator.next("org-1")).isEqualTo(42);
        assertThat(counter.getLastNumber()).isEqualTo(42);
        verify(counters, never()).saveAndFlush(any());
    }

    /** A missing counter starts after the highest number in use, so a reset row cannot hand out a duplicate. */
    @Test
    void seedsAMissingCounterFromTheNumbersInUse() {
        when(counters.findByOrgKey("org-1")).thenReturn(Optional.empty());
        when(instances.findMaxInstanceNumber("org-1")).thenReturn(Optional.of(5L));
        when(counters.findByOrgKey("org-2")).thenReturn(Optional.empty());
        when(instances.findMaxInstanceNumber("org-2")).thenReturn(Optional.empty());

        assertThat(allocator.next("org-1")).isEqualTo(6);
        assertThat(allocator.next("org-2")).isEqualTo(1);
    }
}
