package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for the cross-tenant defect this org-scoping step fixes: rule ids are unique
 * only within an organization, so two organizations may hold the same id with contradicting
 * expressions. Before {@link RuleKey}, the engine cached compiled expressions under the bare id
 * and whichever organization registered first decided the verdict for both.
 */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.rule.domain")
@EnableJpaRepositories("com.processpuzzle.rule.domain")
class RuleTenantIsolationTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    private static final String RULE_ID = "max-quantity";

    @Autowired
    private RuleDefinitionRepository repository;

    private RuleEngine ruleEngine;
    private RuleEngineSync ruleEngineSync;
    private EvaluateObject evaluateObject;
    private DeleteRule deleteRule;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        ruleEngine = new RuleEngine();
        ruleEngineSync = new RuleEngineSync(ruleEngine);
        evaluateObject = new EvaluateObject(repository, ruleEngine);
        deleteRule = new DeleteRule(repository, ruleEngineSync);
    }

    @AfterEach
    void tearDown() {
        ruleEngine.close();
    }

    @Test
    void eachOrganizationIsEvaluatedAgainstItsOwnExpression() {
        RuleDefinition strict = save("demo", RULE_ID, "Order", "entity.quantity <= 1");
        RuleDefinition lenient = save("other", RULE_ID, "Order", "entity.quantity <= 5");
        // Registration order matters for the original defect: demo registered first, so a bare-id
        // cache would have made 'other' evaluate demo's stricter expression.
        ruleEngineSync.register(strict);
        ruleEngineSync.register(lenient);

        Map<String, Object> order = entityWithQuantity(3);

        EvaluationOutcome demoOutcome = evaluateObject.execute("demo", "Order", order);
        EvaluationOutcome otherOutcome = evaluateObject.execute("other", "Order", order);

        assertThat(demoOutcome.passed()).isFalse();
        assertThat(demoOutcome.violations()).extracting(RuleViolation::ruleId).containsExactly(RULE_ID);
        assertThat(otherOutcome.passed()).isTrue();
        assertThat(otherOutcome.violations()).isEmpty();
    }

    @Test
    void lazyRegistrationAlsoStaysWithinTheOrganization() {
        // Nothing is registered up-front here: EvaluateObject compiles on demand, which is the
        // path a freshly-restarted instance takes before its engine state has been re-synced.
        save("demo", RULE_ID, "Order", "entity.quantity <= 1");
        save("other", RULE_ID, "Order", "entity.quantity <= 5");

        Map<String, Object> order = entityWithQuantity(3);

        assertThat(evaluateObject.execute("demo", "Order", order).passed()).isFalse();
        assertThat(evaluateObject.execute("other", "Order", order).passed()).isTrue();
        assertThat(ruleEngine.isRegistered(RuleKey.of("demo", RULE_ID))).isTrue();
        assertThat(ruleEngine.isRegistered(RuleKey.of("other", RULE_ID))).isTrue();
    }

    @Test
    void anotherOrganizationsRulesAreNeverEvaluated() {
        save("other", RULE_ID, "Order", "false");

        EvaluationOutcome outcome = evaluateObject.execute("demo", "Order", entityWithQuantity(3));

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.violations()).isEmpty();
    }

    @Test
    void deleteIgnoresASameIdDependentInAnotherOrganization() {
        RuleDefinition demoBase = save("demo", "base", "Order", "true");
        RuleDefinition otherBase = save("other", "base", "Order", "true");
        save("other", "child", "Order", "true", "base");
        ruleEngineSync.register(demoBase);
        ruleEngineSync.register(otherBase);

        deleteRule.execute("demo", "base");

        assertThat(repository.findByOrgKeyAndId("demo", "base")).isEmpty();
        assertThat(repository.findByOrgKeyAndId("other", "base")).isPresent();
        assertThat(ruleEngine.isRegistered(RuleKey.of("demo", "base"))).isFalse();
        assertThat(ruleEngine.isRegistered(RuleKey.of("other", "base"))).isTrue();
    }

    @Test
    void deleteStillRefusesWhenTheDependentIsInTheSameOrganization() {
        save("other", "base", "Order", "true");
        save("other", "child", "Order", "true", "base");

        assertThatThrownBy(() -> deleteRule.execute("other", "base"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("child");
    }

    private RuleDefinition save(String orgKey, String id, String context, String expression) {
        return save(orgKey, id, context, expression, null);
    }

    private RuleDefinition save(String orgKey, String id, String context, String expression,
                                String extendsRuleId) {
        return repository.saveAndFlush(new RuleDefinition(orgKey, id, id, null, context, expression,
                Severity.ERROR, "violated: " + id, null, extendsRuleId, false, true, List.of()));
    }

    private static Map<String, Object> entityWithQuantity(int quantity) {
        Map<String, Object> entity = new HashMap<>();
        entity.put("quantity", quantity);
        return entity;
    }
}
