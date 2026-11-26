package com.processpuzzle.rule.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.port.RuleContext;
import com.processpuzzle.rule.port.RuleContext.ContextAttribute;
import com.processpuzzle.rule.port.RuleContextDirectory;
import com.processpuzzle.rule.usecase.CreateRule;
import com.processpuzzle.rule.usecase.UpdateRule;
import com.processpuzzle.rule.usecase.assist.ExpressionChecker;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.exception.RuleAlreadyExistsException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class RuleAuthoringParticipantTest {

    private static final String GOOD = """
        {"id":"positive-total","name":"Total","context":"Order","expression":"entity.total > 0","severity":"ERROR"}""";

    private final RuleDefinitionRepository repository = mock(RuleDefinitionRepository.class);
    private final CreateRule create = mock(CreateRule.class);
    private final UpdateRule update = mock(UpdateRule.class);
    private final RuleEngine engine = new RuleEngine();
    private final RuleContextDirectory directory = (orgKey, context) -> "Order".equals(context)
        ? Optional.of(new RuleContext("Order", List.of(new ContextAttribute("total", "NUMBER", true, false, false, null, List.of(), List.of()))))
        : Optional.empty();
    private final RuleAuthoringParticipant participant = new RuleAuthoringParticipant(repository, new RuleMapper(), create, update,
        new ExpressionChecker(engine), provider(RuleContextDirectory.class, directory));

    @AfterEach
    void close() {
        engine.close();
    }

    private static RuleDefinition rule(String id, String expression) {
        return new RuleDefinition("acme", id, "Name", null, "Order", expression, Severity.ERROR, null, null, null, false, true);
    }

    @Test
    void theKindIsRuleAndTheSchemaIsTheContractsRuleInput() {
        assertThat(participant.kind()).isEqualTo("rule");
        assertThat(participant.schema()).contains("expression", "context", "severity");
    }

    @Test
    void theDraftsAreListedByIdInOrder() {
        when(repository.findByOrgKeyAndChannel("acme", Channel.DRAFT)).thenReturn(List.of(rule("b", "true"), rule("a", "true")));

        assertThat(participant.list("acme")).containsExactly("a", "b");
    }

    @Test
    void aDraftIsReadAsJsonAndAnAbsentOneIsEmpty() {
        when(repository.findByOrgKeyAndIdAndChannel("acme", "positive", Channel.DRAFT)).thenReturn(Optional.of(rule("positive", "entity.total > 0")));

        assertThat(participant.get("acme", "positive")).get().asString().contains("\"expression\":\"entity.total > 0\"");
        assertThat(participant.get("acme", "missing")).isEmpty();
    }

    @Test
    void aValidDraftIsCreatedWhenThereIsNoneAndNothingIsReturned() {
        assertThat(participant.saveDraft("acme", "positive-total", GOOD)).isEmpty();

        verify(create).execute(org.mockito.ArgumentMatchers.eq("acme"), any());
        verify(update, never()).execute(anyString(), anyString(), any());
    }

    @Test
    void aValidDraftReplacesTheExistingOne() {
        when(repository.existsByOrgKeyAndIdAndChannel("acme", "positive-total", Channel.DRAFT)).thenReturn(true);

        assertThat(participant.saveDraft("acme", "positive-total", GOOD)).isEmpty();

        verify(update).execute(org.mockito.ArgumentMatchers.eq("acme"), org.mockito.ArgumentMatchers.eq("positive-total"), any());
        verify(create, never()).execute(anyString(), any());
    }

    @Test
    void anExpressionWithAnErrorIsRefusedAndNothingIsWritten() {
        String bad = GOOD.replace("entity.total > 0", "entity.totl > 0");

        List<AuthoringIssue> issues = participant.saveDraft("acme", "positive-total", bad);

        assertThat(issues).isNotEmpty().allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(AuthoringIssue.Severity.ERROR));
        assertThat(issues.get(0).text()).contains("entity.totl");
        verify(create, never()).execute(anyString(), any());
        verify(update, never()).execute(anyString(), anyString(), any());
    }

    @Test
    void aSyntaxErrorIsRefused() {
        assertThat(participant.saveDraft("acme", "positive-total", GOOD.replace("entity.total > 0", "entity.total >")))
            .singleElement().satisfies(issue -> assertThat(issue.text()).startsWith("Syntax error"));
    }

    @Test
    void aBadIdIsACallerBugNotAnIssue() {
        assertThatThrownBy(() -> participant.saveDraft("acme", "other-key", GOOD))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("other-key");
    }

    @Test
    void notJsonIsAnIssueWithoutWriting() {
        assertThat(participant.saveDraft("acme", "positive-total", "{not json")).singleElement()
            .satisfies(issue -> assertThat(issue.text()).contains("not valid JSON"));
        verify(create, never()).execute(anyString(), any());
    }

    @Test
    void aRefusalFromTheUseCaseComesBackAsAnIssue() {
        when(create.execute(anyString(), any())).thenThrow(new RuleAlreadyExistsException("acme", "positive-total"));

        assertThat(participant.saveDraft("acme", "positive-total", GOOD)).singleElement()
            .satisfies(issue -> assertThat(issue.severity()).isEqualTo(AuthoringIssue.Severity.ERROR));
    }

    @Test
    void validateReportsTheStoredDraftsExpressionProblems() {
        when(repository.findByOrgKeyAndIdAndChannel("acme", "positive-total", Channel.DRAFT))
            .thenReturn(Optional.of(rule("positive-total", "entity.totl > 0")));

        assertThat(participant.validate("acme", "positive-total")).anySatisfy(issue -> assertThat(issue.text()).contains("entity.totl"));
    }

    @Test
    void validateOfAnAbsentDraftHasNothingToSay() {
        assertThat(participant.validate("acme", "missing")).isEmpty();
    }

    @Test
    void anUnknownContextIsOnlyAWarningOnSave() {
        String unknown = GOOD.replace("\"Order\"", "\"Ghost\"");

        List<AuthoringIssue> issues = participant.saveDraft("acme", "positive-total", unknown);

        assertThat(issues).isEmpty();
        verify(create).execute(org.mockito.ArgumentMatchers.eq("acme"), any());
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean(type.getSimpleName(), bean);
        return factory.getBeanProvider(type);
    }
}
