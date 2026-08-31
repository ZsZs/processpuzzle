package com.processpuzzle.workflow.definition.domain;

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
        when(repository.findByOrgKeyAndId("org1", "parent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate("org1", "proc1", "parent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("extends unknown workflow");
    }

    @Test
    void validate_validExtendsChain_shouldPass() {
        Workflow parent = Workflow.builder().id("parent").extendsWorkflowId("grandparent").build();
        Workflow grandparent = Workflow.builder().id("grandparent").extendsWorkflowId(null).build();

        when(repository.findByOrgKeyAndId("org1", "parent")).thenReturn(Optional.of(parent));
        when(repository.findByOrgKeyAndId("org1", "grandparent")).thenReturn(Optional.of(grandparent));

        assertThatCode(() -> validator.validate("org1", "proc1", "parent"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_cycleInExtendsChain_shouldThrow() {
        Workflow parent = Workflow.builder().id("parent").extendsWorkflowId("grandparent").build();
        Workflow grandparent = Workflow.builder().id("grandparent").extendsWorkflowId("proc1").build();

        when(repository.findByOrgKeyAndId("org1", "parent")).thenReturn(Optional.of(parent));
        when(repository.findByOrgKeyAndId("org1", "grandparent")).thenReturn(Optional.of(grandparent));

        assertThatThrownBy(() -> validator.validate("org1", "proc1", "parent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("would create a cycle");
    }

    @Test
    void validate_chainTooDeep_shouldThrow() {
        Workflow p1 = Workflow.builder().id("p1").extendsWorkflowId("loop").build();
        Workflow loop = Workflow.builder().id("loop").extendsWorkflowId("loop").build();

        when(repository.findByOrgKeyAndId("org1", "p1")).thenReturn(Optional.of(p1));
        when(repository.findByOrgKeyAndId("org1", "loop")).thenReturn(Optional.of(loop));

        assertThatThrownBy(() -> validator.validate("org1", "proc1", "p1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Extends chain too deep");
    }
}
