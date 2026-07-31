package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ImportRulesTest {

    private RuleDefinitionRepository repository;
    private RuleEngineSync ruleEngineSync;
    private ImportRules importRules;

    @BeforeEach
    void setUp() {
        repository = mock(RuleDefinitionRepository.class);
        ruleEngineSync = mock(RuleEngineSync.class);
        importRules = new ImportRules(repository, ruleEngineSync);
        when(repository.findByOrgKey(anyString())).thenReturn(List.of());
        when(repository.findByOrgKeyAndId(anyString(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void createsRulesScopedToTheTargetOrganizationRegardlessOfTheFile() throws IOException {
        ImportOutcome outcome = importRules.execute("demo", yaml("""
                rules:
                  - id: max-quantity
                    name: Max quantity
                    context: Order
                    expression: "entity.quantity <= 5"
                    severity: warning
                    fields: [quantity]
                """));

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.errors()).isEmpty();

        RuleDefinition saved = savedRule();
        assertThat(saved.getOrgKey()).isEqualTo("demo");
        assertThat(saved.getId()).isEqualTo("max-quantity");
        // Severity is case-insensitive in the file.
        assertThat(saved.getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.isOverride()).isFalse();
        assertThat(saved.getFields()).containsExactly("quantity");
        verify(ruleEngineSync).register(saved);
    }

    @Test
    void updatesEveryFieldOfAnExistingRuleInPlace() throws IOException {
        RuleDefinition existing = new RuleDefinition("demo", "max-quantity", "Old", "old desc",
                "Invoice", "false", Severity.INFO, "old msg", "old.id", null, false, true,
                List.of("stale"));
        when(repository.findByOrgKeyAndId("demo", "max-quantity")).thenReturn(Optional.of(existing));

        ImportOutcome outcome = importRules.execute("demo", yaml("""
                rules:
                  - id: max-quantity
                    name: Max quantity
                    description: New desc
                    context: Order
                    expression: "entity.quantity <= 5"
                    severity: WARNING
                    message: too many
                    translocoId: rule.max
                    override: true
                    enabled: false
                    fields: [quantity, total]
                """));

        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isEqualTo(1);
        assertThat(existing.getName()).isEqualTo("Max quantity");
        assertThat(existing.getDescription()).isEqualTo("New desc");
        assertThat(existing.getContext()).isEqualTo("Order");
        assertThat(existing.getExpression()).isEqualTo("entity.quantity <= 5");
        assertThat(existing.getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(existing.getMessage()).isEqualTo("too many");
        assertThat(existing.getTranslocoId()).isEqualTo("rule.max");
        assertThat(existing.isOverride()).isTrue();
        assertThat(existing.isEnabled()).isFalse();
        assertThat(existing.getFields()).containsExactly("quantity", "total");
        verify(repository).save(existing);
    }

    @Test
    void defaultsSeverityToErrorAndEnabledToTrueWhenTheEntryIsSilent() throws IOException {
        importRules.execute("demo", yaml("""
                rules:
                  - id: plain
                    name: Plain
                    context: Order
                    expression: "true"
                    severity: "  "
                """));

        RuleDefinition saved = savedRule();
        assertThat(saved.getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(saved.isEnabled()).isTrue();
    }

    @Test
    void anExplicitEnabledFlagIsHonouredOnBothTheCreateAndTheUpdatePath() throws IOException {
        importRules.execute("demo", yaml("""
                rules:
                  - id: fresh
                    name: Fresh
                    context: Order
                    expression: "true"
                    enabled: true
                """));
        assertThat(savedRule().isEnabled()).isTrue();

        reset(repository);
        RuleDefinition existing = new RuleDefinition("demo", "known", "Known", null, "Order",
                "true", Severity.ERROR, null, null, null, false, false, List.of());
        when(repository.findByOrgKey("demo")).thenReturn(List.of(existing));
        when(repository.findByOrgKeyAndId("demo", "known")).thenReturn(Optional.of(existing));

        importRules.execute("demo", yaml("""
                rules:
                  - id: known
                    name: Known
                    context: Order
                    expression: "true"
                    enabled: true
                """));

        assertThat(existing.isEnabled()).isTrue();
    }

    @Test
    void aDisabledEntryIsImportedAsDisabled() throws IOException {
        importRules.execute("demo", yaml("""
                rules:
                  - id: fresh
                    name: Fresh
                    context: Order
                    expression: "true"
                    enabled: false
                """));

        assertThat(savedRule().isEnabled()).isFalse();
    }

    @Test
    void anExistingDisabledRuleIsReEnabledWhenTheEntryIsSilent() throws IOException {
        RuleDefinition existing = new RuleDefinition("demo", "known", "Known", null, "Order",
                "true", Severity.ERROR, null, null, null, false, false, List.of());
        when(repository.findByOrgKey("demo")).thenReturn(List.of(existing));
        when(repository.findByOrgKeyAndId("demo", "known")).thenReturn(Optional.of(existing));

        importRules.execute("demo", yaml("""
                rules:
                  - id: known
                    name: Known
                    context: Order
                    expression: "true"
                """));

        assertThat(existing.isEnabled()).isTrue();
    }

    @Test
    void detectsACycleThatDoesNotRunThroughTheEntryBeingChecked() throws IOException {
        // 'a' is not itself part of the b -> c -> b loop, but walking its chain never terminates,
        // so the visited set — not the start-id comparison — is what catches it.
        ImportOutcome outcome = importRules.execute("demo", yaml("""
                rules:
                  - id: a
                    name: A
                    context: Order
                    expression: "true"
                    extends: b
                  - id: b
                    name: B
                    context: Order
                    expression: "true"
                    extends: c
                  - id: c
                    name: C
                    context: Order
                    expression: "true"
                    extends: b
                """));

        assertThat(outcome.errors()).contains("Rule 'a' is part of an extends cycle.");
    }

    @Test
    void anEmptyDocumentIsANoOpRatherThanAFailure() throws IOException {
        ImportOutcome outcome = importRules.execute("demo", yaml("{}"));

        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.errors()).isEmpty();
        verify(repository, never()).save(any(RuleDefinition.class));
    }

    @Test
    void resolvesAnExtendsLinkAgainstAnEntryLaterInTheSameFile() throws IOException {
        // A child may appear before its parent, which is why extends is not a foreign key.
        ImportOutcome outcome = importRules.execute("demo", yaml("""
                rules:
                  - id: child
                    name: Child
                    context: Order
                    expression: "true"
                    extends: base
                  - id: base
                    name: Base
                    context: Order
                    expression: "true"
                """));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isEqualTo(2);
    }

    @Test
    void resolvesAnExtendsLinkAgainstARuleTheOrganizationAlreadyHas() throws IOException {
        when(repository.findByOrgKey("demo")).thenReturn(List.of(
                new RuleDefinition("demo", "base", "Base", null, "Order", "true", Severity.ERROR,
                        null, null, null, false, true, List.of())));

        ImportOutcome outcome = importRules.execute("demo", yaml("""
                rules:
                  - id: child
                    name: Child
                    context: Order
                    expression: "true"
                    extends: base
                """));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isEqualTo(1);
    }

    @Test
    void reportsAnEntryWithoutAnIdAndSkipsIt() throws IOException {
        ImportOutcome outcome = importRules.execute("demo", yaml("""
                rules:
                  - name: Nameless
                    context: Order
                    expression: "true"
                  - id: "  "
                    name: Blank
                    context: Order
                    expression: "true"
                """));

        assertThat(outcome.errors())
                .containsExactly("A rule entry is missing 'id' and was skipped.",
                        "A rule entry is missing 'id' and was skipped.");
        verify(repository, never()).save(any(RuleDefinition.class));
    }

    @Test
    void reportsADuplicateIdWithinTheFile() throws IOException {
        ImportOutcome outcome = importRules.execute("demo", yaml("""
                rules:
                  - id: dup
                    name: First
                    context: Order
                    expression: "true"
                  - id: dup
                    name: Second
                    context: Order
                    expression: "false"
                """));

        assertThat(outcome.errors())
                .containsExactly("Duplicate rule id within the import file: 'dup'.");
    }

    @Test
    void reportsAnExtendsReferenceNothingSatisfies() throws IOException {
        ImportOutcome outcome = importRules.execute("demo", yaml("""
                rules:
                  - id: child
                    name: Child
                    context: Order
                    expression: "true"
                    extends: nowhere
                """));

        assertThat(outcome.errors())
                .containsExactly("Rule 'child' extends unknown rule 'nowhere'.");
    }

    @Test
    void reportsAnExtendsCycleAcrossSeveralEntries() throws IOException {
        ImportOutcome outcome = importRules.execute("demo", yaml("""
                rules:
                  - id: a
                    name: A
                    context: Order
                    expression: "true"
                    extends: b
                  - id: b
                    name: B
                    context: Order
                    expression: "true"
                    extends: a
                """));

        assertThat(outcome.errors())
                .containsExactly("Rule 'a' is part of an extends cycle.",
                        "Rule 'b' is part of an extends cycle.");
    }

    @Test
    void nothingIsWrittenWhenValidationProducedAnyError() throws IOException {
        ImportOutcome outcome = importRules.execute("demo", yaml("""
                rules:
                  - id: good
                    name: Good
                    context: Order
                    expression: "true"
                  - id: bad
                    name: Bad
                    context: Order
                    expression: "true"
                    extends: nowhere
                """));

        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.errors()).hasSize(1);
        verify(repository, never()).save(any(RuleDefinition.class));
        verifyNoInteractions(ruleEngineSync);
    }

    @Test
    void anUnknownSeverityIsRejectedRatherThanSilentlyDowngraded() {
        assertThatThrownBy(() -> importRules.execute("demo", yaml("""
                rules:
                  - id: odd
                    name: Odd
                    context: Order
                    expression: "true"
                    severity: FATAL
                """)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RuleDefinition savedRule() {
        ArgumentCaptor<RuleDefinition> saved = ArgumentCaptor.forClass(RuleDefinition.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private static ByteArrayInputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
