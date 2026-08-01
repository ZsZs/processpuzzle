package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExportRulesTest {

    private RuleDefinitionRepository repository;
    private ExportRules exportRules;

    @BeforeEach
    void setUp() {
        repository = mock(RuleDefinitionRepository.class);
        exportRules = new ExportRules(repository);
    }

    @Test
    void exportsEveryRuleOfTheOrganizationWhenNoContextIsGiven() throws IOException {
        when(repository.findByOrgKey("demo")).thenReturn(List.of(
                rule("max-quantity", "Order", true, true, List.of("quantity")),
                rule("has-customer", "Invoice", false, true, List.of())));

        String yaml = export(null);

        assertThat(yaml).contains("id: \"max-quantity\"", "id: \"has-customer\"");
        verify(repository, never()).findByOrgKeyAndContext(anyString(), anyString());
    }

    @Test
    void narrowsToOneContextWhenAsked() throws IOException {
        when(repository.findByOrgKeyAndContext("demo", "Order"))
                .thenReturn(List.of(rule("max-quantity", "Order", false, true, List.of())));

        String yaml = export("Order");

        assertThat(yaml).contains("id: \"max-quantity\"");
        verify(repository, never()).findByOrgKey(anyString());
    }

    @Test
    void omitsTheOrgKeySoTheFileCanBeImportedIntoAnotherOrganization() throws IOException {
        when(repository.findByOrgKey("demo"))
                .thenReturn(List.of(rule("max-quantity", "Order", false, true, List.of("quantity"))));

        String yaml = export(null);

        assertThat(yaml).doesNotContain("orgKey").doesNotContain("demo");
        assertThat(yaml).contains("severity: \"ERROR\"", "context: \"Order\"", "- \"quantity\"");
    }

    @Test
    void writesDefaultValuedFlagsAsAbsentRatherThanExplicitDefaults() throws IOException {
        when(repository.findByOrgKey("demo"))
                .thenReturn(List.of(rule("plain", "Order", false, true, List.of())));

        String yaml = export(null);

        // override=false, enabled=true and an empty fields list are the defaults an importer
        // assumes, so they are left out entirely.
        assertThat(yaml).doesNotContain("override").doesNotContain("enabled").doesNotContain("fields");
    }

    @Test
    void writesTheFlagsOnlyWhenTheyDivergeFromTheDefaults() throws IOException {
        when(repository.findByOrgKey("demo"))
                .thenReturn(List.of(rule("odd", "Order", true, false, List.of("total"))));

        String yaml = export(null);

        assertThat(yaml).contains("override: true", "enabled: false", "- \"total\"");
    }

    private String export(String context) throws IOException {
        return new String(exportRules.execute("demo", context), StandardCharsets.UTF_8);
    }

    private static RuleDefinition rule(String id, String context, boolean override,
                                       boolean enabled, List<String> fields) {
        return new RuleDefinition("demo", id, id, "desc of " + id, context, "true",
                Severity.ERROR, "violated", "rule." + id, null, override, enabled, fields);
    }
}
