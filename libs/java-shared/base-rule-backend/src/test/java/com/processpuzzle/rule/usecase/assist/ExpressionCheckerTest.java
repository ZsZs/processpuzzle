package com.processpuzzle.rule.usecase.assist;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.AuthoringIssue.Severity;
import com.processpuzzle.rule.port.RuleContext;
import com.processpuzzle.rule.port.RuleContext.ContextAttribute;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExpressionCheckerTest {

    private final RuleEngine engine = new RuleEngine();
    private final ExpressionChecker checker = new ExpressionChecker(engine);
    private final Optional<RuleContext> order = Optional.of(Orders.context());

    @AfterEach
    void close() {
        engine.close();
    }

    @Test
    void aGoodExpressionHasNoIssues() {
        assertThat(checker.check("acme", order, "entity.total > 0")).isEmpty();
    }

    @Test
    void anExpressionThatGuardsItsOptionalAttributesHasNoIssues() {
        assertThat(checker.check("acme", order,
            "!entity.lineItems || entity.lineItems.every(li => qty(li.quantity).gt(qty('0 pc')))")).isEmpty();
    }

    @Test
    void anEmptyExpressionIsAnError() {
        assertThat(checker.check("acme", order, "  ")).singleElement()
            .satisfies(issue -> assertThat(issue.severity()).isEqualTo(Severity.ERROR));
        assertThat(checker.check("acme", order, null)).hasSize(1);
    }

    @Test
    void aSyntaxErrorIsTheOnlyIssueAndSaysSo() {
        List<AuthoringIssue> issues = checker.check("acme", order, "entity.total > ;");

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.ERROR);
            assertThat(issue.text()).startsWith("Syntax error: ");
        });
    }

    @Test
    void aTrailingSemicolonIsASyntaxError() {
        assertThat(checker.check("acme", order, "entity.total > 0;")).singleElement()
            .satisfies(issue -> assertThat(issue.text()).startsWith("Syntax error: "));
    }

    @Test
    void aMisspeltAttributeIsAnErrorEvenThoughTheEngineWouldRunItQuietly() {
        List<AuthoringIssue> issues = checker.check("acme", order, "entity.totl > 0");

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.ERROR);
            assertThat(issue.text()).contains("entity.totl");
        });
    }

    @Test
    void anExpressionThatIsNotABooleanIsAnErrorOnTheFullObject() {
        List<AuthoringIssue> issues = checker.check("acme", order, "entity.status");

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.ERROR);
            assertThat(issue.text()).contains("an object with every attribute filled", "true or false");
        });
    }

    @Test
    void anUnguardedOptionalListIsAnErrorWhenTheListIsMissingOnlyFromSparserObjects() {
        List<AuthoringIssue> issues = checker.check("acme", order, "entity.lineItems.every(li => li.amount > 0)");

        assertThat(issues).isNotEmpty().allSatisfy(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.text()).contains("an object with only its required attributes");
        });
    }

    @Test
    void aFailureOnTheFullObjectIsAnError() {
        List<AuthoringIssue> issues = checker.check("acme", order, "entity.total.nothing.here > 0");

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.ERROR);
            assertThat(issue.text()).contains("an object with every attribute filled");
        });
    }

    @Test
    void aFailureOnAnAttributeTheSamplesCouldNotFillIsOnlyAWarning() {
        RuleContext context = new RuleContext("Thing", List.of(
            new ContextAttribute("mass", "TEXT", false, false, false, "QUANTITY", List.of(), List.of())));

        List<AuthoringIssue> issues = checker.check("acme", Optional.of(context), "qty(entity.mass).gt(qty('1 kg'))");

        assertThat(issues).isNotEmpty().allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(Severity.WARNING));
    }

    @Test
    void withoutAnEntityOnlyTheSyntaxIsCheckedAndRuntimeFailuresAreWarnings() {
        assertThat(checker.check("acme", Optional.empty(), "entity.total > 0")).isEmpty();
        assertThat(checker.check("acme", Optional.empty(), "entity.a.b.c > 0")).allSatisfy(issue ->
            assertThat(issue.severity()).isEqualTo(Severity.WARNING));
        assertThat(checker.check("acme", Optional.empty(), "entity.total >")).singleElement()
            .satisfies(issue -> assertThat(issue.severity()).isEqualTo(Severity.ERROR));
    }

    @Test
    void theSameFailureOnSeveralSamplesIsReportedOnce() {
        List<AuthoringIssue> issues = checker.check("acme", order, "entity.total.nothing.here > 0");

        assertThat(issues.stream().map(AuthoringIssue::text).distinct()).hasSameSizeAs(issues);
    }
}
