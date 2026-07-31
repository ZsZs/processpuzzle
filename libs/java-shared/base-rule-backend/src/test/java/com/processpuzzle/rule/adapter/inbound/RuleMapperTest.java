package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionTestFactory;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.model.RuleDefinitionInput;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleMapperTest {

    private final RuleMapper mapper = new RuleMapper();

    @Test
    void toDomainTakesTheOrganizationFromThePathNotTheBody() {
        RuleDefinition rule = mapper.toDomain("demo", fullInput());

        assertThat(rule.getOrgKey()).isEqualTo("demo");
        assertThat(rule.getId()).isEqualTo("max-quantity");
        assertThat(rule.getName()).isEqualTo("Max quantity");
        assertThat(rule.getDescription()).isEqualTo("desc");
        assertThat(rule.getContext()).isEqualTo("Order");
        assertThat(rule.getExpression()).isEqualTo("entity.quantity <= 5");
        assertThat(rule.getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(rule.getMessage()).isEqualTo("too many");
        assertThat(rule.getTranslocoId()).isEqualTo("rule.max");
        assertThat(rule.getExtendsRuleId()).isEqualTo("base");
        assertThat(rule.isOverride()).isTrue();
        assertThat(rule.isEnabled()).isFalse();
        assertThat(rule.getFields()).containsExactly("quantity");
    }

    @Test
    void toDomainDefaultsSeverityToErrorAndEnabledToTrue() {
        RuleDefinitionInput input = new RuleDefinitionInput("plain", "Plain", "Order", "true", null);

        RuleDefinition rule = mapper.toDomain("demo", input);

        assertThat(rule.getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(rule.isEnabled()).isTrue();
        assertThat(rule.isOverride()).isFalse();
        assertThat(rule.getFields()).isEmpty();
    }

    @Test
    void applyToDomainOverwritesEveryMutableFieldButKeepsTheIdentity() {
        RuleDefinition target = new RuleDefinition("demo", "max-quantity", "Old", "old desc",
                "Invoice", "false", Severity.ERROR, "old", "old.id", null, false, true,
                List.of("stale"));

        mapper.applyToDomain(fullInput(), target);

        assertThat(target.getOrgKey()).isEqualTo("demo");
        assertThat(target.getId()).isEqualTo("max-quantity");
        assertThat(target.getName()).isEqualTo("Max quantity");
        assertThat(target.getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(target.getExtendsRuleId()).isEqualTo("base");
        assertThat(target.isOverride()).isTrue();
        assertThat(target.isEnabled()).isFalse();
        assertThat(target.getFields()).containsExactly("quantity");
    }

    @Test
    void applyToDomainAlsoDefaultsAnAbsentSeverityAndEnabledFlag() {
        RuleDefinition target = new RuleDefinition("demo", "max-quantity", "Old", null, "Order",
                "false", Severity.WARNING, null, null, null, true, false, List.of("stale"));

        mapper.applyToDomain(new RuleDefinitionInput("max-quantity", "New", "Order", "true", null),
                target);

        assertThat(target.getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(target.isEnabled()).isTrue();
        assertThat(target.isOverride()).isFalse();
        assertThat(target.getFields()).isEmpty();
    }

    @Test
    void anExplicitlyEnabledFlagIsHonouredJustLikeTheSchemaDefault() {
        RuleDefinitionInput input = new RuleDefinitionInput("plain", "Plain", "Order", "true", null)
                .enabled(true);

        assertThat(mapper.toDomain("demo", input).isEnabled()).isTrue();
        assertThat(applyTo(disabledRule(), input).isEnabled()).isTrue();
    }

    @Test
    void anEnabledFlagSentAsNullFallsBackToEnabled() {
        // The schema default is true, but a client can still put an explicit null in the body —
        // that must mean "default", not "disabled".
        RuleDefinitionInput input = new RuleDefinitionInput("plain", "Plain", "Order", "true", null)
                .enabled(null);

        assertThat(mapper.toDomain("demo", input).isEnabled()).isTrue();
        assertThat(applyTo(disabledRule(), input).isEnabled()).isTrue();
    }

    private RuleDefinition applyTo(RuleDefinition target, RuleDefinitionInput input) {
        mapper.applyToDomain(input, target);
        return target;
    }

    private static RuleDefinition disabledRule() {
        return new RuleDefinition("demo", "plain", "Plain", null, "Order", "true",
                Severity.ERROR, null, null, null, false, false, List.of());
    }

    @Test
    void aRuleWithoutASeverityIsReportedAsAnError() {
        // Severity is non-null in the schema and in the column, so this is the defensive path:
        // a violation must never surface without a severity the client can act on.
        com.processpuzzle.rule.model.RuleViolation model = mapper.toModel(
                new com.processpuzzle.rule.usecase.RuleViolation("max-quantity", "Max quantity",
                        null, "too many", "rule.max"));

        assertThat(model.getSeverity()).isEqualTo(com.processpuzzle.rule.model.Severity.ERROR);
    }

    @Test
    void toModelCarriesTheOrganizationAndTheAuditColumns() {
        RuleDefinition rule = mapper.toDomain("demo", fullInput());

        com.processpuzzle.rule.model.RuleDefinition model = mapper.toModel(rule);

        assertThat(model.getOrgKey()).isEqualTo("demo");
        assertThat(model.getId()).isEqualTo("max-quantity");
        assertThat(model.getSeverity()).isEqualTo(com.processpuzzle.rule.model.Severity.WARNING);
        assertThat(model.getExtendsRuleId()).isEqualTo("base");
        assertThat(model.getOverride()).isTrue();
        assertThat(model.getEnabled()).isFalse();
        assertThat(model.getFields()).containsExactly("quantity");
        // Not persisted yet, so the audit columns are still empty rather than defaulted.
        assertThat(model.getVersion()).isNull();
        assertThat(model.getCreatedAt()).isNull();
        assertThat(model.getUpdatedAt()).isNull();
    }

    @Test
    void toModelRendersStampedInstantsAsUtcOffsets() {
        RuleDefinition persisted = RuleDefinitionTestFactory.persisted("demo", "max-quantity");

        com.processpuzzle.rule.model.RuleDefinition model = mapper.toModel(persisted);

        assertThat(model.getCreatedAt()).isNotNull();
        assertThat(model.getCreatedAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(model.getCreatedAt().toInstant()).isEqualTo(persisted.getCreatedAt());
        assertThat(model.getUpdatedAt()).isNotNull();
        assertThat(model.getUpdatedAt().toInstant()).isEqualTo(persisted.getUpdatedAt());
    }

    private static RuleDefinitionInput fullInput() {
        return new RuleDefinitionInput("max-quantity", "Max quantity", "Order",
                "entity.quantity <= 5", com.processpuzzle.rule.model.Severity.WARNING)
                .description("desc")
                .message("too many")
                .translocoId("rule.max")
                .extendsRuleId("base")
                .override(true)
                .enabled(false)
                .fields(List.of("quantity"));
    }
}
