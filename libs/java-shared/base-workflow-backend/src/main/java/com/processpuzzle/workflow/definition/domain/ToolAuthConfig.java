package com.processpuzzle.workflow.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Auth config for a {@link ToolDefinition}. A real {@code @Embeddable} (not a JSONB POJO like
 * {@link ToolOperation}): it is a single fixed-shape value, not a variable-length list, so plain
 * mapped columns on the owning table are simpler and directly queryable/indexable if ever needed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ToolAuthConfig {

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false)
    @Builder.Default
    private AuthType type = AuthType.NONE;

    /**
     * Key name of the secret in the application's secret store (environment variable name or
     * Vault path). base-workflow never stores credential values directly — only the reference.
     */
    @Column(name = "auth_secret_ref")
    private String secretRef;
}
