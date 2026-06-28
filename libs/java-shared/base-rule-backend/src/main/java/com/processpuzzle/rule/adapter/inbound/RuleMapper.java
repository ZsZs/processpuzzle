package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.model.EvaluationResult;
import com.processpuzzle.rule.model.ImportResult;
import com.processpuzzle.rule.model.RuleDefinitionInput;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import com.processpuzzle.rule.usecase.ImportOutcome;
import com.processpuzzle.rule.usecase.RuleViolation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class RuleMapper {

    public RuleDefinition toDomain(RuleDefinitionInput input) {
        return new RuleDefinition(
                input.getId(),
                input.getName(),
                input.getDescription(),
                input.getContext(),
                input.getExpression(),
                toDomainSeverity(input.getSeverity()),
                input.getMessage(),
                input.getTranslocoId(),
                input.getExtendsRuleId(),
                Boolean.TRUE.equals(input.getOverride()),
                input.getEnabled() == null || input.getEnabled());
    }

    public void applyToDomain(RuleDefinitionInput input, RuleDefinition target) {
        target.setName(input.getName());
        target.setDescription(input.getDescription());
        target.setContext(input.getContext());
        target.setExpression(input.getExpression());
        target.setSeverity(toDomainSeverity(input.getSeverity()));
        target.setMessage(input.getMessage());
        target.setTranslocoId(input.getTranslocoId());
        target.setExtendsRuleId(input.getExtendsRuleId());
        target.setOverride(Boolean.TRUE.equals(input.getOverride()));
        target.setEnabled(input.getEnabled() == null || input.getEnabled());
    }

    public com.processpuzzle.rule.model.RuleDefinition toModel(RuleDefinition rule) {
        com.processpuzzle.rule.model.RuleDefinition model = new com.processpuzzle.rule.model.RuleDefinition(
                rule.getId(),
                rule.getName(),
                rule.getContext(),
                rule.getExpression(),
                toModelSeverity(rule.getSeverity()));
        model.setDescription(rule.getDescription());
        model.setMessage(rule.getMessage());
        model.setTranslocoId(rule.getTranslocoId());
        model.setExtendsRuleId(rule.getExtendsRuleId());
        model.setOverride(rule.isOverride());
        model.setEnabled(rule.isEnabled());
        model.setVersion(rule.getVersion());
        model.setCreatedAt(toOffsetDateTime(rule.getCreatedAt()));
        model.setUpdatedAt(toOffsetDateTime(rule.getUpdatedAt()));
        return model;
    }

    public ImportResult toModel(ImportOutcome outcome) {
        return new ImportResult()
                .created(outcome.created())
                .updated(outcome.updated())
                .errors(outcome.errors());
    }

    public EvaluationResult toModel(EvaluationOutcome outcome) {
        List<com.processpuzzle.rule.model.RuleViolation> violations = outcome.violations().stream()
                .map(this::toModel)
                .toList();
        return new EvaluationResult(outcome.passed(), violations);
    }

    public com.processpuzzle.rule.model.RuleViolation toModel(RuleViolation violation) {
        com.processpuzzle.rule.model.RuleViolation model = new com.processpuzzle.rule.model.RuleViolation(
                violation.ruleId(),
                toModelSeverity(violation.severity()));
        model.setRuleName(violation.ruleName());
        model.setMessage(violation.message());
        model.setTranslocoId(violation.translocoId());
        return model;
    }

    private com.processpuzzle.rule.domain.Severity toDomainSeverity(com.processpuzzle.rule.model.Severity severity) {
        return severity == null
                ? com.processpuzzle.rule.domain.Severity.ERROR
                : com.processpuzzle.rule.domain.Severity.valueOf(severity.getValue());
    }

    private com.processpuzzle.rule.model.Severity toModelSeverity(com.processpuzzle.rule.domain.Severity severity) {
        return severity == null
                ? com.processpuzzle.rule.model.Severity.ERROR
                : com.processpuzzle.rule.model.Severity.fromValue(severity.name());
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
