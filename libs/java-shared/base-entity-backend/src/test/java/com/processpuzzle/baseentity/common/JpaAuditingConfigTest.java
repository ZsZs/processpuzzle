package com.processpuzzle.baseentity.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JpaAuditingConfigTest {

    @Test
    void configCanBeInstantiated() {
        JpaAuditingConfig config = new JpaAuditingConfig();
        assertThat(config).isNotNull();
    }
}
