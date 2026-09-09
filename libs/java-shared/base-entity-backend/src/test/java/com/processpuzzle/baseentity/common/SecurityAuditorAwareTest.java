package com.processpuzzle.baseentity.common;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditorAwareTest {

    @Test
    void getCurrentAuditor_returnsSystem() {
        SecurityAuditorAware auditorAware = new SecurityAuditorAware();
        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).isPresent().contains("system");
    }
}
