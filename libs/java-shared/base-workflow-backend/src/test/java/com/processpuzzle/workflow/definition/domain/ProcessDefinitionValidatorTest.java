package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.workflow.common.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessDefinitionValidatorTest {

    private final ProcessDefinitionValidator validator = new ProcessDefinitionValidator();

    @Test
    void acceptsAConsistentProcess() {
        ProcessDefinition process = ProcessDefinition.builder().orgKey("acme").id("delivery").build();
        process.addRole(RoleDefinition.builder().id("developer").name("Developer").build());
        process.addTask(TaskDefinition.builder().id("code").name("Write code").performedBy("developer").build());
        process.addTask(TaskDefinition.builder().id("review").name("Review code").performedBy("developer")
                .dependsOn(java.util.List.of("code")).build());

        validator.validate(process);
    }

    @Test
    void rejectsATaskPerformedByAnUnknownRole() {
        ProcessDefinition process = ProcessDefinition.builder().orgKey("acme").id("delivery").build();
        process.addTask(TaskDefinition.builder().id("code").name("Write code").performedBy("ghost").build());

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void rejectsATaskThatDependsOnItself() {
        ProcessDefinition process = ProcessDefinition.builder().orgKey("acme").id("delivery").build();
        process.addRole(RoleDefinition.builder().id("developer").name("Developer").build());
        process.addTask(TaskDefinition.builder().id("code").name("Write code").performedBy("developer")
                .dependsOn(java.util.List.of("code")).build());

        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot depend on itself");
    }

    @Test
    void rejectsDuplicateTaskIds() {
        ProcessDefinition process = ProcessDefinition.builder().orgKey("acme").id("delivery").build();
        process.addRole(RoleDefinition.builder().id("developer").name("Developer").build());
        process.addTask(TaskDefinition.builder().id("code").name("Write code").performedBy("developer").build());
        process.addTask(TaskDefinition.builder().id("code").name("Write code again").performedBy("developer").build());

        assertThat(process.getTasks()).hasSize(2);
        assertThatThrownBy(() -> validator.validate(process))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Duplicate task id");
    }
}
