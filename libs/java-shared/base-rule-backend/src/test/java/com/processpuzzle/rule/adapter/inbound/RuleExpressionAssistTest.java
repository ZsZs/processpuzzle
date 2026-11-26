package com.processpuzzle.rule.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.processpuzzle.core.assist.AssistRequest;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.AuthoringIssue.Severity;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.rule.port.RuleContextDirectory;
import com.processpuzzle.rule.usecase.assist.ExpressionChecker;
import com.processpuzzle.rule.usecase.assist.Orders;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class RuleExpressionAssistTest {

    private final RuleEngine engine = new RuleEngine();
    private final OrganizationGuard guard = mock(OrganizationGuard.class);
    private final RuleContextDirectory directory = (orgKey, context) ->
        "acme".equals(orgKey) && "Order".equals(context) ? Optional.of(Orders.context()) : Optional.empty();
    private final RuleExpressionAssist assist = new RuleExpressionAssist(provider(OrganizationGuard.class, guard),
        provider(RuleContextDirectory.class, directory), new ExpressionChecker(engine));

    @AfterEach
    void close() {
        engine.close();
    }

    @Test
    void theTaskIsNamedRuleExpression() {
        assertThat(assist.task()).isEqualTo("rule.expression");
    }

    @Test
    void accessIsWhoeverMayDesignInTheOrganization() {
        assist.checkAccess("acme");
        verify(guard).requireDesign("acme");

        doThrow(new SecurityException("no")).when(guard).requireDesign("other");
        assertThatThrownBy(() -> assist.checkAccess("other")).isInstanceOf(SecurityException.class);
    }

    @Test
    void theContextListsTheNamedEntitysAttributes() {
        assertThat(assist.context("acme", request("Order"))).contains("Entity: Order", "- total: NUMBER, required");
    }

    @Test
    void anUnknownOrUnnamedEntityGivesAContextThatSaysItIsNotKnown() {
        assertThat(assist.context("acme", request("Nope"))).contains("not known");
        assertThat(assist.context("acme", new AssistRequest("x", Map.of(), ""))).contains("not known");
        assertThat(assist.context("acme", request(" "))).contains("not known");
    }

    @Test
    void aParameterOtherThanContextIsRefused() {
        assertThatThrownBy(() -> assist.context("acme", new AssistRequest("x", Map.of("severity", "ERROR"), "")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("severity");
    }

    @Test
    void withoutADirectoryTheEntityIsUnknown() {
        RuleExpressionAssist bare = new RuleExpressionAssist(provider(OrganizationGuard.class, guard),
            new StaticListableBeanFactory().getBeanProvider(RuleContextDirectory.class), new ExpressionChecker(engine));

        assertThat(bare.context("acme", request("Order"))).contains("not known");
    }

    @Test
    void aCandidateIsCheckedAgainstTheEntity() {
        assertThat(assist.validate("acme", request("Order"), "entity.total > 0")).isEmpty();
        assertThat(assist.validate("acme", request("Order"), "entity.totl > 0")).singleElement()
            .satisfies(issue -> assertThat(issue.text()).contains("entity.totl"));
    }

    @Test
    void anEntityThatIsNamedButUnknownIsAWarningBesideTheSyntaxCheck() {
        List<AuthoringIssue> issues = assist.validate("acme", request("Nope"), "entity.total > 0");

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.text()).contains("Nope");
        });
    }

    @Test
    void theGuideIsLoadedFromTheClasspath() {
        assertThat(assist.guide()).contains("ONE JavaScript expression", "qty(", "strict boolean");
        assertThat(assist.guide()).isSameAs(assist.guide());
    }

    /** The examples the model learns from must themselves be well formed, or the guide teaches a fault. */
    @Test
    void everyExampleInTheGuideIsASyntacticallyValidExpression() {
        Matcher examples = Pattern.compile("(?m)^Description: .*\\n`(.+)`$").matcher(assist.guide());
        int count = 0;
        while (examples.find()) {
            count++;
            String expression = examples.group(1);
            assertThat(engineIssues(expression)).as(expression).noneMatch(issue -> issue.text().startsWith("Syntax error"));
        }
        assertThat(count).isGreaterThanOrEqualTo(8);
    }

    private List<AuthoringIssue> engineIssues(String expression) {
        return new ExpressionChecker(engine).check("acme", Optional.empty(), expression);
    }

    private static AssistRequest request(String context) {
        return new AssistRequest("The total must be positive", Map.of("context", context), "");
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean(type.getSimpleName(), bean);
        return factory.getBeanProvider(type);
    }
}
