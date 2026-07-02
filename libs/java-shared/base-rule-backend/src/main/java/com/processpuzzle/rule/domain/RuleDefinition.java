package com.processpuzzle.rule.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persisted PPCL rule definition.
 *
 * <p>{@code extendsRuleId} is deliberately a plain string column, not a JPA
 * {@code @ManyToOne} self-reference: a YAML import batch may contain a child rule in the
 * same file as (or even before) its parent, so enforcing this at the database level via a
 * foreign key would make import ordering matter. Referential integrity (the parent exists,
 * no cycles) is validated at the service layer instead — see {@code RuleDefinitionService}
 * and {@code RuleYamlService}.
 */
@Entity
@Table(name = "rule_definitions")
public class RuleDefinition {

    @Id
    @Column(length = 100)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 100)
    private String context;

    @Lob
    @Column(nullable = false)
    private String expression;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity = Severity.ERROR;

    @Column(length = 1000)
    private String message;

    @Column(name = "transloco_id", length = 200)
    private String translocoId;

    @Column(name = "extends_rule_id", length = 100)
    private String extendsRuleId;

    @Column(nullable = false)
    private boolean override = false;

    @Column(nullable = false)
    private boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rule_definition_fields", joinColumns = @JoinColumn(name = "rule_id"))
    @Column(name = "field_name", length = 100)
    private List<String> fields = new ArrayList<>();

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected RuleDefinition() {
        // required by JPA
    }

    public RuleDefinition(String id, String name, String description, String context,
                           String expression, Severity severity, String message,
                           String translocoId, String extendsRuleId,
                           boolean override, boolean enabled) {
        this(id, name, description, context, expression, severity, message,
                translocoId, extendsRuleId, override, enabled, null);
    }

    public RuleDefinition(String id, String name, String description, String context,
                           String expression, Severity severity, String message,
                           String translocoId, String extendsRuleId,
                           boolean override, boolean enabled, List<String> fields) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.context = context;
        this.expression = expression;
        this.severity = severity;
        this.message = message;
        this.translocoId = translocoId;
        this.extendsRuleId = extendsRuleId;
        this.override = override;
        this.enabled = enabled;
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTranslocoId() {
        return translocoId;
    }

    public void setTranslocoId(String translocoId) {
        this.translocoId = translocoId;
    }

    public String getExtendsRuleId() {
        return extendsRuleId;
    }

    public void setExtendsRuleId(String extendsRuleId) {
        this.extendsRuleId = extendsRuleId;
    }

    public boolean isOverride() {
        return override;
    }

    public void setOverride(boolean override) {
        this.override = override;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public void setFields(List<String> fields) {
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
