package com.processpuzzle.workflow.common;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowCommonTest {

    static class TestAuditable extends Auditable {}

    @Test
    void auditable_gettersAndSetters_workAsExpected() {
        TestAuditable auditable = new TestAuditable();
        Instant now = Instant.now();

        auditable.setCreatedAt(now);
        auditable.setCreatedBy("user-1");
        auditable.setUpdatedAt(now.plusSeconds(10));
        auditable.setUpdatedBy("user-2");

        assertThat(auditable.getCreatedAt()).isEqualTo(now);
        assertThat(auditable.getCreatedBy()).isEqualTo("user-1");
        assertThat(auditable.getUpdatedAt()).isEqualTo(now.plusSeconds(10));
        assertThat(auditable.getUpdatedBy()).isEqualTo("user-2");
    }

    @Test
    void exceptions_constructors_shouldSetMessagesAndCauses() {
        ConflictException conflict = new ConflictException("conflict message");
        assertThat(conflict.getMessage()).isEqualTo("conflict message");

        NotFoundException notFound = new NotFoundException("not found message");
        assertThat(notFound.getMessage()).isEqualTo("not found message");

        ValidationException validation = new ValidationException("validation message");
        assertThat(validation.getMessage()).isEqualTo("validation message");
    }
}
