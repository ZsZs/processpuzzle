package com.processpuzzle.rule.adapter.inbound;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.i18n.RuleTranslationBundle;
import com.processpuzzle.rule.i18n.RuleTranslationRepository;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Erasing one tenant removes every row this feature holds for it, and nothing of another's.
 *
 * <p>Against a real transaction, because that is the whole correctness argument: the listener is
 * {@code BEFORE_COMMIT}, and an {@code AFTER_COMMIT} one would have its deletes discarded without an
 * error. Publishing the event inside a committed transaction and finding the rows gone is what proves
 * the phase, which is why the class suspends {@code @DataJpaTest}'s own rolled-back transaction.
 *
 * <p>H2 in PostgreSQL mode, because the translation bundles map their messages as {@code jsonb}.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rule-erasure;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan({"com.processpuzzle.rule.domain", "com.processpuzzle.rule.i18n"})
@EnableJpaRepositories({"com.processpuzzle.rule.domain", "com.processpuzzle.rule.i18n"})
@Import(TenantDataCleaner.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantDataCleanerTest {

    private static final String ERASED = "erased-org";
    private static final String KEPT = "kept-org";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {

        @Bean
        RuleEngine ruleEngine() {
            return mock(RuleEngine.class);
        }

        @Bean
        RuleEngineSync ruleEngineSync(RuleEngine ruleEngine) {
            return new RuleEngineSync(ruleEngine);
        }
    }

    @Autowired
    private RuleDefinitionRepository rules;

    @Autowired
    private RuleTranslationRepository translations;

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void erasesEveryRowOfTheTenantAndNothingOfAnother() {
        givenRulesAndTranslationsFor(ERASED);
        givenRulesAndTranslationsFor(KEPT);

        erase(ERASED);

        assertThat(rules.findByOrgKey(ERASED)).isEmpty();
        assertThat(countFields(ERASED)).as("the collection table goes too").isZero();
        assertThat(translations.findAll()).extracting(bundle -> bundle.getKey().getOrgKey())
                .containsOnly(KEPT);

        assertThat(rules.findByOrgKey(KEPT)).hasSize(2);
        assertThat(countFields(KEPT)).isEqualTo(4);
    }

    /** Only once the rows are committed gone; a rolled-back erasure must leave the engine as it was. */
    @Test
    void takesTheTenantsRulesOutOfTheEngineAfterCommit() {
        givenRulesAndTranslationsFor(ERASED);

        erase(ERASED);

        verify(ruleEngine).unregisterRule(RuleKey.of(ERASED, "positive-quantity"));
        verify(ruleEngine).unregisterRule(RuleKey.of(ERASED, "has-total"));
    }

    @Test
    void aRolledBackErasureDeletesNothingAndUnregistersNothing() {
        givenRulesAndTranslationsFor(ERASED);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new OrganizationDeletedEvent(ERASED));
            status.setRollbackOnly();
        });

        assertThat(rules.findByOrgKey(ERASED)).hasSize(2);
        verify(ruleEngine, never()).unregisterRule(RuleKey.of(ERASED, "positive-quantity"));
    }

    /** A tenant with nothing here — or erased twice — is not an error. */
    @Test
    void erasingATenantWithNoRulesIsHarmless() {
        erase("nobody");

        assertThat(rules.count()).isZero();
    }

    private void erase(String orgKey) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new OrganizationDeletedEvent(orgKey)));
    }

    private void givenRulesAndTranslationsFor(String orgKey) {
        rules.saveAndFlush(rule(orgKey, "positive-quantity", List.of("quantity", "unit")));
        rules.saveAndFlush(rule(orgKey, "has-total", List.of("total", "currency")));
        translations.saveAndFlush(new RuleTranslationBundle(orgKey, "rule", "en", Map.of("title", "Rules")));
    }

    private int countFields(String orgKey) {
        Integer count = jdbc.queryForObject("select count(*) from rule_definition_fields where org_key = ?",
                Integer.class, orgKey);
        return count == null ? 0 : count;
    }

    private static RuleDefinition rule(String orgKey, String id, List<String> fields) {
        return new RuleDefinition(orgKey, id, id, "desc of " + id, "Order", "true", Severity.ERROR,
                "violated", "rule." + id, null, false, true, fields);
    }

    @AfterEach
    void cleanUp() {
        rules.deleteAll();
        translations.deleteAll();
        reset(ruleEngine);
    }
}
