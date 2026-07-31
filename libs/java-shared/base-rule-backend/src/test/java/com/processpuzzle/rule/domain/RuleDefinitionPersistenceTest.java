package com.processpuzzle.rule.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Proves the tenant-scoped mapping actually binds: the composite {@code @IdClass}, the
 * {@code fields} element collection (whose join needs <em>both</em> key columns), and
 * Specification queries over the flat {@code @Id} fields that {@code FindAllRules} relies on.
 */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.rule.domain")
@EnableJpaRepositories("com.processpuzzle.rule.domain")
class RuleDefinitionPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private RuleDefinitionRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
    }

    @Test
    void fullRowRoundTrips() {
        repository.saveAndFlush(rule("demo", "positive-quantities", "Order",
                "entity.lineItems.every(li => li.quantity > 0)", List.of("lineItems", "total")));

        RuleDefinition reloaded = repository.findById(new RuleDefinitionKey("demo", "positive-quantities"))
                .orElseThrow();

        assertThat(reloaded.getOrgKey()).isEqualTo("demo");
        assertThat(reloaded.getId()).isEqualTo("positive-quantities");
        assertThat(reloaded.getContext()).isEqualTo("Order");
        assertThat(reloaded.getExpression()).isEqualTo("entity.lineItems.every(li => li.quantity > 0)");
        assertThat(reloaded.getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(reloaded.getFields()).containsExactly("lineItems", "total");
        assertThat(reloaded.isEnabled()).isTrue();
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void anUpdateRestampsUpdatedAtButNotCreatedAt() {
        RuleDefinition saved = repository.saveAndFlush(
                rule("demo", "max-quantity", "Order", "true", List.of()));
        Instant createdAt = saved.getCreatedAt();
        Instant firstUpdatedAt = saved.getUpdatedAt();

        saved.setExpression("false");
        repository.saveAndFlush(saved);

        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(firstUpdatedAt);
        assertThat(saved.getVersion()).isEqualTo(1L);
    }

    @Test
    void sameRuleIdCoexistsInDifferentOrganizations() {
        repository.saveAndFlush(rule("org-a", "max-quantity", "Order",
                "entity.lineItems.every(li => li.quantity <= 1)", List.of("lineItems")));
        repository.saveAndFlush(rule("org-b", "max-quantity", "Order",
                "entity.lineItems.every(li => li.quantity <= 5)", List.of("total")));

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByOrgKeyAndId("org-a", "max-quantity"))
                .map(RuleDefinition::getExpression)
                .contains("entity.lineItems.every(li => li.quantity <= 1)");
        assertThat(repository.findByOrgKeyAndId("org-b", "max-quantity"))
                .map(RuleDefinition::getExpression)
                .contains("entity.lineItems.every(li => li.quantity <= 5)");
        assertThat(repository.existsByOrgKeyAndId("org-a", "max-quantity")).isTrue();
        assertThat(repository.existsByOrgKeyAndId("org-c", "max-quantity")).isFalse();
    }

    @Test
    void elementCollectionStaysWithItsOwnTenantRow() {
        // rule_id alone would collide in rule_definition_fields; the join carries org_key too.
        repository.saveAndFlush(rule("org-a", "max-quantity", "Order", "true", List.of("lineItems")));
        repository.saveAndFlush(rule("org-b", "max-quantity", "Order", "true", List.of("total", "status")));

        assertThat(repository.findByOrgKeyAndId("org-a", "max-quantity").orElseThrow().getFields())
                .containsExactly("lineItems");
        assertThat(repository.findByOrgKeyAndId("org-b", "max-quantity").orElseThrow().getFields())
                .containsExactly("total", "status");
    }

    @Test
    void finderMethodsAreScopedToOneOrganization() {
        repository.saveAndFlush(rule("org-a", "base", "Order", "true", List.of()));
        repository.saveAndFlush(rule("org-a", "child", "Order", "true", List.of(), "base"));
        repository.saveAndFlush(rule("org-a", "other-context", "Invoice", "true", List.of()));
        repository.saveAndFlush(rule("org-b", "base", "Order", "true", List.of()));
        repository.saveAndFlush(rule("org-b", "child", "Order", "true", List.of(), "base"));

        assertThat(repository.findByOrgKey("org-a")).extracting(RuleDefinition::getId)
                .containsExactlyInAnyOrder("base", "child", "other-context");
        assertThat(repository.findByOrgKeyAndContext("org-a", "Order")).extracting(RuleDefinition::getId)
                .containsExactlyInAnyOrder("base", "child");
        assertThat(repository.findByOrgKeyAndExtendsRuleId("org-a", "base"))
                .extracting(RuleDefinition::getOrgKey, RuleDefinition::getId)
                .containsExactly(tuple("org-a", "child"));
    }

    @Test
    void specificationQueriesResolveFlatIdFields() {
        repository.saveAndFlush(rule("org-a", "rule-1", "Order", "true", List.of()));
        repository.saveAndFlush(rule("org-a", "rule-2", "Invoice", "true", List.of()));
        repository.saveAndFlush(rule("org-b", "rule-1", "Order", "true", List.of()));

        Specification<RuleDefinition> byOrg = (root, query, cb) -> cb.equal(root.get("orgKey"), "org-a");
        assertThat(repository.findAll(byOrg)).extracting(RuleDefinition::getId)
                .containsExactlyInAnyOrder("rule-1", "rule-2");

        Specification<RuleDefinition> byId = (root, query, cb) -> cb.equal(root.get("id"), "rule-1");
        assertThat(repository.findAll(byOrg.and(byId))).hasSize(1);
    }

    @Test
    void deleteByOrgKeyRemovesOnlyThatOrganizationsRules() {
        repository.saveAndFlush(rule("org-a", "rule-1", "Order", "true", List.of("total")));
        repository.saveAndFlush(rule("org-a", "rule-2", "Order", "true", List.of()));
        repository.saveAndFlush(rule("org-b", "rule-1", "Order", "true", List.of("total")));

        repository.deleteByOrgKey("org-a");
        repository.flush();

        assertThat(repository.findByOrgKey("org-a")).isEmpty();
        assertThat(repository.findByOrgKey("org-b")).hasSize(1);
    }

    private static RuleDefinition rule(String orgKey, String id, String context,
                                       String expression, List<String> fields) {
        return rule(orgKey, id, context, expression, fields, null);
    }

    private static RuleDefinition rule(String orgKey, String id, String context,
                                       String expression, List<String> fields, String extendsRuleId) {
        return new RuleDefinition(orgKey, id, id, "desc of " + id, context, expression,
                Severity.ERROR, "violated", "rule." + id, extendsRuleId, false, true, fields);
    }
}
