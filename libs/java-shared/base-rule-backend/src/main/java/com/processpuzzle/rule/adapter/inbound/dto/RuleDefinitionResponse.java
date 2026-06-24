package com.processpuzzle.rule.adapter.inbound.dto;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.Severity;

import java.time.Instant;

public class RuleDefinitionResponse {

    private String id;
    private String name;
    private String description;
    private String context;
    private String expression;
    private Severity severity;
    private String extendsRuleId;
    private boolean override;
    private boolean enabled;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public static RuleDefinitionResponse from(RuleDefinition rule) {
        RuleDefinitionResponse response = new RuleDefinitionResponse();
        response.id = rule.getId();
        response.name = rule.getName();
        response.description = rule.getDescription();
        response.context = rule.getContext();
        response.expression = rule.getExpression();
        response.severity = rule.getSeverity();
        response.extendsRuleId = rule.getExtendsRuleId();
        response.override = rule.isOverride();
        response.enabled = rule.isEnabled();
        response.version = rule.getVersion();
        response.createdAt = rule.getCreatedAt();
        response.updatedAt = rule.getUpdatedAt();
        return response;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getContext() {
        return context;
    }

    public String getExpression() {
        return expression;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getExtendsRuleId() {
        return extendsRuleId;
    }

    public boolean isOverride() {
        return override;
    }

    public boolean isEnabled() {
        return enabled;
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
