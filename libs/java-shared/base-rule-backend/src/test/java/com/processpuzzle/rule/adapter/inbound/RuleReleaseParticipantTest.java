package com.processpuzzle.rule.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.RuleReleaser;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Releasing rules over a real repository and real transactions: the released row follows the draft, fields included,
 * and the engine's registry follows the release — once it has committed.
 */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.rule.domain")
@EnableJpaRepositories("com.processpuzzle.rule.domain")
@Import({RuleReleaseParticipant.class, RuleReleaser.class, RuleEngineSync.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RuleReleaseParticipantTest {

    private static final String ORG = "my-org";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
        @Bean
        RuleEngine ruleEngine() {
            return new RuleEngine();
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    @Autowired
    private RuleReleaseParticipant participant;
    @Autowired
    private RuleDefinitionRepository repository;
    @Autowired
    private RuleEngine engine;
    @Autowired
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        engine.unregisterRule(RuleKey.of(ORG, "max-quantity"));
    }

    @Test
    void releasingCopiesTheDraft_fieldsIncluded_andRegistersItOnCommit() {
        saveDraft(rule("max-quantity", "entity.quantity <= 5", List.of("quantity")));
        assertThat(participant.pending(ORG)).extracting(PendingChange::key, PendingChange::change)
                .containsExactly(tuple("max-quantity", PendingChange.Change.ADDED));
        assertThat(engine.isRegistered(RuleKey.of(ORG, "max-quantity"))).isFalse();

        release();

        RuleDefinition released = released("max-quantity");
        assertThat(released.getExpression()).isEqualTo("entity.quantity <= 5");
        assertThat(released.getFields()).containsExactly("quantity");
        assertThat(engine.isRegistered(RuleKey.of(ORG, "max-quantity"))).isTrue();
        assertThat(participant.pending(ORG)).isEmpty();
    }

    @Test
    void aLaterReleaseUpdatesTheReleasedRowInPlace_andTheDraftsFieldsStayTheDrafts() {
        saveDraft(rule("max-quantity", "entity.quantity <= 5", List.of("quantity")));
        release();
        Long releasedVersion = released("max-quantity").getVersion();

        transaction.executeWithoutResult(status -> {
            RuleDefinition draft = repository.findByOrgKeyAndIdAndChannel(ORG, "max-quantity", Channel.DRAFT).orElseThrow();
            draft.setExpression("entity.quantity <= 9");
            draft.setFields(List.of("quantity", "unit"));
            repository.save(draft);
        });
        assertThat(released("max-quantity").getFields()).containsExactly("quantity");
        assertThat(participant.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.CHANGED);

        release();

        RuleDefinition released = released("max-quantity");
        assertThat(released.getExpression()).isEqualTo("entity.quantity <= 9");
        assertThat(released.getFields()).containsExactly("quantity", "unit");
        assertThat(released.getVersion()).isGreaterThan(releasedVersion);
    }

    @Test
    void releasingADeletedDraftRemovesTheReleasedRuleAndItsRegistration() {
        saveDraft(rule("max-quantity", "true", List.of()));
        release();
        transaction.executeWithoutResult(status ->
                repository.delete(repository.findByOrgKeyAndIdAndChannel(ORG, "max-quantity", Channel.DRAFT).orElseThrow()));
        assertThat(engine.isRegistered(RuleKey.of(ORG, "max-quantity"))).isTrue();

        release();

        assertThat(repository.findByOrgKeyAndIdAndChannel(ORG, "max-quantity", Channel.RELEASED)).isEmpty();
        assertThat(engine.isRegistered(RuleKey.of(ORG, "max-quantity"))).isFalse();
    }

    @Test
    void aRuleRefersToTheRuleItExtends() {
        saveDraft(rule("base", "true", List.of()));
        RuleDefinition child = rule("child", "true", List.of());
        child.setExtendsRuleId("base");
        saveDraft(child);

        assertThat(participant.references(ORG)).extracting(DefinitionReference::fromKey, DefinitionReference::toKind,
                        DefinitionReference::toKey)
                .containsExactly(tuple("child", DefinitionKinds.RULE, "base"));
    }

    private void release() {
        transaction.executeWithoutResult(status -> participant.release(ORG));
    }

    private void saveDraft(RuleDefinition rule) {
        transaction.executeWithoutResult(status -> repository.save(rule));
    }

    private RuleDefinition released(String id) {
        return repository.findByOrgKeyAndIdAndChannel(ORG, id, Channel.RELEASED).orElseThrow();
    }

    private static RuleDefinition rule(String id, String expression, List<String> fields) {
        return new RuleDefinition(ORG, id, id, null, "Order", expression, Severity.ERROR, "violated: " + id, null, null,
                false, true, fields);
    }
}
