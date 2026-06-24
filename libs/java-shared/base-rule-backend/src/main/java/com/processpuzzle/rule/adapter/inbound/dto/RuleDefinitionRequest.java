package com.processpuzzle.rule.adapter.inbound.dto;

import com.processpuzzle.rule.domain.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RuleDefinitionRequest {

    @NotBlank
    private String id;

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String context;

    @NotBlank
    private String expression;

    @NotNull
    private Severity severity;

    private String extendsRuleId;

    private boolean override;

    private boolean enabled = true;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
}
