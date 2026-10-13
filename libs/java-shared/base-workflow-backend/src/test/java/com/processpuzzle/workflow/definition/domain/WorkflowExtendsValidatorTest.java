package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowExtendsValidatorTest {

    private WorkflowRepository repository;
    private WorkflowExtendsValidator validator;

    @BeforeEach
    void setUp() {
        repository = mock(WorkflowRepository.class);
        validator = new WorkflowExtendsValidator(repository);
    }

    @Test
    void validate_nullExtends_shouldPass() {
        assertThatCode(() -> validator.validate("org1", "proc1", null))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_selfExtends_shouldThrow() {
        assertThatThrownBy(() -> validator.validate("org1", "proc1", "proc1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Workflow cannot extend itself");
    }

    @Test
    void validate_unknownParent_shouldThrow() {
        when(repository.findByOrgKeyAndIdAndChannel("org1", "parent", Channel.DRAFT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate("org1", "proc1", "parent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extends unknown workflow");
    }

    @Test
    void validate_validExtendsChain_shouldPass() {
        Workflow parent = Workflow.builder().id("parent").extendsWorkflowId("grandparent").build();
        Workflow grandparent = Workflow.builder().id("grandparent").extendsWorkflowId(null).build();

        when(repository.findByOrgKeyAndIdAndChannel("org1", "parent", Channel.DRAFT)).thenReturn(Optional.of(parent));
        when(repository.findByOrgKeyAndIdAndChannel("org1", "grandparent", Channel.DRAFT)).thenReturn(Optional.of(grandparent));

        assertThatCode(() -> validator.validate("org1", "proc1", "parent"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_cycleInExtendsChain_shouldThrow() {
        Workflow parent = Workflow.builder().id("parent").extendsWorkflowId("grandparent").build();
        Workflow grandparent = Workflow.builder().id("grandparent").extendsWorkflowId("proc1").build();

        when(repository.findByOrgKeyAndIdAndChannel("org1", "parent", Channel.DRAFT)).thenReturn(Optional.of(parent));
        when(repository.findByOrgKeyAndIdAndChannel("org1", "grandparent", Channel.DRAFT)).thenReturn(Optional.of(grandparent));

        assertThatThrownBy(() -> validator.validate("org1", "proc1", "parent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("would create a cycle");
    }

    @Test
    void validate_chainTooDeep_shouldThrow() {
        Workflow p1 = Workflow.builder().id("p1").extendsWorkflowId("loop").build();
        Workflow loop = Workflow.builder().id("loop").extendsWorkflowId("loop").build();

        when(repository.findByOrgKeyAndIdAndChannel("org1", "p1", Channel.DRAFT)).thenReturn(Optional.of(p1));
        when(repository.findByOrgKeyAndIdAndChannel("org1", "loop", Channel.DRAFT)).thenReturn(Optional.of(loop));

        assertThatThrownBy(() -> validator.validate("org1", "proc1", "p1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Extends chain too deep");
    }
}
