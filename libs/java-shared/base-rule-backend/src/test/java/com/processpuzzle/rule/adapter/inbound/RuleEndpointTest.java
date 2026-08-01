package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.model.EvaluationRequest;
import com.processpuzzle.rule.model.EvaluationResult;
import com.processpuzzle.rule.model.PageOfRuleDefinition;
import com.processpuzzle.rule.model.RuleDefinitionInput;
import com.processpuzzle.rule.usecase.*;
import com.processpuzzle.shared.model.ImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The endpoint is a thin adapter: every test here pins the delegation and the HTTP status, not
 * business behaviour. {@code orgKey} always comes from the path — never from the body.
 */
class RuleEndpointTest {

    private CreateRule createRule;
    private UpdateRule updateRule;
    private DeleteRule deleteRule;
    private FindRule findRule;
    private FindAllRules findAllRules;
    private ImportRules importRules;
    private ExportRules exportRules;
    private EvaluateObject evaluateObject;
    private RuleEndpoint endpoint;

    @BeforeEach
    void setUp() {
        createRule = mock(CreateRule.class);
        updateRule = mock(UpdateRule.class);
        deleteRule = mock(DeleteRule.class);
        findRule = mock(FindRule.class);
        findAllRules = mock(FindAllRules.class);
        importRules = mock(ImportRules.class);
        exportRules = mock(ExportRules.class);
        evaluateObject = mock(EvaluateObject.class);
        endpoint = new RuleEndpoint(createRule, updateRule, deleteRule, findRule, findAllRules,
                importRules, exportRules, evaluateObject, new RuleMapper());
    }

    @Test
    void createAnswers201WithTheCreatedRule() {
        RuleDefinitionInput input = new RuleDefinitionInput("max-quantity", "Max quantity", "Order",
                "entity.quantity <= 5", com.processpuzzle.rule.model.Severity.ERROR);
        when(createRule.execute("demo", input)).thenReturn(rule("max-quantity"));

        ResponseEntity<com.processpuzzle.rule.model.RuleDefinition> response =
                endpoint.createRule("demo", input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo("max-quantity");
        assertThat(response.getBody().getOrgKey()).isEqualTo("demo");
    }

    @Test
    void updateAnswers200AndPassesTheIdFromThePath() {
        RuleDefinitionInput input = new RuleDefinitionInput("body-id", "Max quantity", "Order",
                "entity.quantity <= 5", com.processpuzzle.rule.model.Severity.ERROR);
        when(updateRule.execute("demo", "path-id", input)).thenReturn(rule("path-id"));

        ResponseEntity<com.processpuzzle.rule.model.RuleDefinition> response =
                endpoint.updateRule("demo", "path-id", input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo("path-id");
    }

    @Test
    void deleteAnswers204WithNoBody() {
        ResponseEntity<Void> response = endpoint.deleteRule("demo", "max-quantity");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(deleteRule).execute("demo", "max-quantity");
    }

    @Test
    void getAnswers200WithTheMappedRule() {
        when(findRule.execute("demo", "max-quantity")).thenReturn(rule("max-quantity"));

        ResponseEntity<com.processpuzzle.rule.model.RuleDefinition> response =
                endpoint.getRule("demo", "max-quantity");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo("max-quantity");
    }

    @Test
    void listForwardsEveryQueryParameterAndAnswersAPage() {
        when(findAllRules.execute("demo", "Order", "enabled==true", "name,asc", 2, 5))
                .thenReturn(new PageImpl<>(List.of(rule("max-quantity")), PageRequest.of(2, 5), 11));

        ResponseEntity<PageOfRuleDefinition> response =
                endpoint.listRules("demo", "Order", "enabled==true", "name,asc", 2, 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PageOfRuleDefinition body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getContent()).extracting(com.processpuzzle.rule.model.RuleDefinition::getId)
                .containsExactly("max-quantity");
        assertThat(body.getTotalElements()).isEqualTo(11);
        assertThat(body.getTotalPages()).isEqualTo(3);
        assertThat(body.getNumber()).isEqualTo(2);
        assertThat(body.getSize()).isEqualTo(5);
    }

    @Test
    void importAnswers200WithTheCountsAndErrors() throws IOException {
        when(importRules.execute(eq("demo"), any())).thenReturn(new ImportOutcome(2, 1, List.of("nope")));

        ResponseEntity<ImportResult> response = endpoint.importRules("demo", uploadedFile());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCreated()).isEqualTo(2);
        assertThat(response.getBody().getUpdated()).isEqualTo(1);
        assertThat(response.getBody().getErrors()).containsExactly("nope");
    }

    @Test
    void importSurfacesAnIoFailureAsUnchecked() throws IOException {
        when(importRules.execute(eq("demo"), any())).thenThrow(new IOException("unreadable"));

        assertThatThrownBy(() -> endpoint.importRules("demo", uploadedFile()))
                .isInstanceOf(UncheckedIOException.class)
                .hasRootCauseMessage("unreadable");
    }

    @Test
    void evaluateAnswers200WithTheOutcome() {
        when(evaluateObject.execute("demo", "Order", Map.of("quantity", 3)))
                .thenReturn(new EvaluationOutcome(false, List.of(new RuleViolation(
                        "max-quantity", "Max quantity", Severity.WARNING, "too many", "rule.max"))));

        ResponseEntity<EvaluationResult> response = endpoint.evaluateObject("demo",
                new EvaluationRequest("Order", Map.of("quantity", 3)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        EvaluationResult body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getPassed()).isFalse();
        assertThat(body.getViolations()).singleElement().satisfies(violation -> {
            assertThat(violation.getRuleId()).isEqualTo("max-quantity");
            assertThat(violation.getRuleName()).isEqualTo("Max quantity");
            assertThat(violation.getSeverity())
                    .isEqualTo(com.processpuzzle.rule.model.Severity.WARNING);
            assertThat(violation.getMessage()).isEqualTo("too many");
            assertThat(violation.getTranslocoId()).isEqualTo("rule.max");
        });
    }

    @Test
    void exportAnswersAYamlAttachmentNamedAfterTheOrganization() throws IOException {
        when(exportRules.execute("demo", "Order")).thenReturn("rules: []".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<Resource> response = endpoint.exportRules("demo", "Order");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"demo-rules-export.yaml\"");
        assertThat(response.getHeaders().getContentType()).hasToString("application/x-yaml");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContentAsByteArray())
                .isEqualTo("rules: []".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void exportSurfacesAnIoFailureAsUnchecked() throws IOException {
        when(exportRules.execute(anyString(), any())).thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> endpoint.exportRules("demo", null))
                .isInstanceOf(UncheckedIOException.class)
                .hasRootCauseMessage("disk full");
    }

    private static MockMultipartFile uploadedFile() {
        return new MockMultipartFile("file", "rules.yaml", "application/x-yaml",
                "rules: []".getBytes(StandardCharsets.UTF_8));
    }

    private static com.processpuzzle.rule.domain.RuleDefinition rule(String id) {
        return new com.processpuzzle.rule.domain.RuleDefinition("demo", id, "Max quantity", "desc",
                "Order", "entity.quantity <= 5", Severity.ERROR, "violated", "rule.max", null,
                false, true, List.of("quantity"));
    }
}
