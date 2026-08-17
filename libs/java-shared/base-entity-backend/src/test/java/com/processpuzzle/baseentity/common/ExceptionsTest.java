package com.processpuzzle.baseentity.common;

import com.processpuzzle.baseentity.common.ValidationException.Violation;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionsTest {

    @Test
    void notFoundException_containsMessage() {
        NotFoundException ex = new NotFoundException("Not found: 123");
        assertThat(ex.getMessage()).isEqualTo("Not found: 123");
    }

    @Test
    void conflictException_containsMessage() {
        ConflictException ex = new ConflictException("Conflict occurred");
        assertThat(ex.getMessage()).isEqualTo("Conflict occurred");
    }

    @Test
    void validationException_constructorsAndProperties() {
        Violation v1 = new Violation("name", "required");
        Violation v2 = new Violation("age", "must be positive");
        List<Violation> list = List.of(v1, v2);

        ValidationException ex1 = new ValidationException(list);
        assertThat(ex1.getViolations()).containsExactly(v1, v2);
        assertThat(ex1.getMessage()).isEqualTo("Payload failed validation");

        assertThat(v1.attributeCode()).isEqualTo("name");
        assertThat(v1.message()).isEqualTo("required");
        assertThat(v1.equals(new Violation("name", "required"))).isTrue();
        assertThat(v1.hashCode()).isEqualTo(new Violation("name", "required").hashCode());
        assertThat(v1.toString()).contains("name").contains("required");
    }
}
