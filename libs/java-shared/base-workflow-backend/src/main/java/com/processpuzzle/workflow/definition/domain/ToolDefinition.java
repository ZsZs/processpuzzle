package com.processpuzzle.workflow.definition.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A callable external tool (a REST API) that task steps invoke, shared across process definitions
 * within a tenant. Composite key mirrors {@link Workflow}'s (orgKey, id).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, of = {"orgKey", "id"})
@ToString
@Entity
@Table(name = "workflow_tool_definition")
@IdClass(ToolDefinitionKey.class)
public class ToolDefinition extends com.processpuzzle.workflow.common.Auditable {

    @Id
    @Column(nullable = false)
    private String orgKey;

    @Id
    @Column(nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String baseUrl;

    @Embedded
    @Builder.Default
    private ToolAuthConfig auth = ToolAuthConfig.builder().build();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<ToolOperation> operations = new ArrayList<>();

    @Version
    private Long version;

    public Optional<ToolOperation> findOperation(String operationId) {
        return operations.stream().filter(o -> o.getId().equals(operationId)).findFirst();
    }
}
