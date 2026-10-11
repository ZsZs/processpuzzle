package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.adapter.inbound.RuleMapper;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.model.RuleDefinitionInput;
import com.processpuzzle.rule.model.Severity;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import com.processpuzzle.rule.usecase.service.RuleEngineSync;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.rule.domain")
@EnableJpaRepositories("com.processpuzzle.rule.domain")
@Import({CreateRule.class, UpdateRule.class, DeleteRule.class, ImportRules.class, EvaluateObject.class,
        RuleEngineSync.class, RuleMapper.class, RuleExtendsValidator.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RuleTransactionSynchronizationTest {

    private static final String ORG = "transaction-org";
    private static final String ID = "editable";
    private static final RuleKey KEY = RuleKey.of(ORG, ID);

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
        @Bean
        RuleEngine ruleEngine() {
            return new RuleEngine();
        }
    }

    @Autowired private RuleDefinitionRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private RuleEngine engine;
    @Autowired private CreateRule create;
    @Autowired private UpdateRule update;
    @Autowired private DeleteRule delete;
    @Autowired private ImportRules importer;
    @Autowired private EvaluateObject evaluate;

    private TransactionTemplate transactions;
    private ExecutorService worker;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        engine.unregisterRule(KEY);
        transactions = new TransactionTemplate(transactionManager);
        worker = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        worker.close();
        repository.deleteAll();
        engine.unregisterRule(KEY);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void creationIsPublishedOnlyOnCommit(boolean rollback) {
        transactions.executeWithoutResult(status -> {
            create.execute(ORG, input("false"));
            assertThat(engine.isRegistered(KEY)).isFalse();
            assertThat(evaluateOnWorker()).isTrue();
            if (rollback) status.setRollbackOnly();
        });

        assertThat(repository.existsByOrgKeyAndId(ORG, ID)).isEqualTo(!rollback);
        assertThat(engine.isRegistered(KEY)).isEqualTo(!rollback);
        assertThat(evaluateOnWorker()).isEqualTo(rollback);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void editsDoNotEscapeUntilCommitAndRefreshBothWarmedThreads(boolean rollback) {
        create.execute(ORG, input("true"));
        assertThat(engine.evaluate(KEY, Map.of())).isTrue();
        assertThat(evaluateOnWorker()).isTrue();

        transactions.executeWithoutResult(status -> {
            update.execute(ORG, ID, input("false"));
            assertThat(engine.evaluate(KEY, Map.of())).isTrue();
            assertThat(evaluateOnWorker()).isTrue();
            if (rollback) status.setRollbackOnly();
        });

        assertThat(repository.findByOrgKeyAndId(ORG, ID).orElseThrow().getExpression())
                .isEqualTo(rollback ? "true" : "false");
        assertThat(engine.evaluate(KEY, Map.of())).isEqualTo(rollback);
        assertThat(evaluateOnWorker()).isEqualTo(rollback);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deletionIsPublishedOnlyOnCommit(boolean rollback) {
        create.execute(ORG, input("false"));
        assertThat(evaluateOnWorker()).isFalse();

        transactions.executeWithoutResult(status -> {
            delete.execute(ORG, ID);
            assertThat(engine.isRegistered(KEY)).isTrue();
            assertThat(evaluateOnWorker()).isFalse();
            if (rollback) status.setRollbackOnly();
        });

        assertThat(repository.existsByOrgKeyAndId(ORG, ID)).isEqualTo(rollback);
        assertThat(engine.isRegistered(KEY)).isEqualTo(rollback);
        assertThat(evaluateOnWorker()).isEqualTo(!rollback);
    }

    @Test
    void disableAndReEnablePublishOnlyCommittedExpressions() {
        create.execute(ORG, input("true"));
        assertThat(evaluateOnWorker()).isTrue();
        transactions.executeWithoutResult(status -> {
            update.execute(ORG, ID, input("false").enabled(false));
            assertThat(engine.evaluate(KEY, Map.of())).isTrue();
            assertThat(evaluateOnWorker()).isTrue();
        });
        assertThat(engine.isRegistered(KEY)).isFalse();
        assertThat(evaluateOnWorker()).isTrue();

        transactions.executeWithoutResult(status -> {
            update.execute(ORG, ID, input("false").enabled(true));
            assertThat(engine.isRegistered(KEY)).isFalse();
            assertThat(evaluateOnWorker()).isTrue();
        });
        assertThat(engine.evaluate(KEY, Map.of())).isFalse();
        assertThat(evaluateOnWorker()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failedCommitFlushDoesNotPublishTheSavedExpression(boolean existing) {
        if (existing) create.execute(ORG, input("true"));
        RuleDefinitionInput invalid = input("false").name("x".repeat(201));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            if (existing) update.execute(ORG, ID, invalid);
            else create.execute(ORG, invalid);
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(repository.existsByOrgKeyAndId(ORG, ID)).isEqualTo(existing);
        assertThat(engine.isRegistered(KEY)).isEqualTo(existing);
        if (existing) {
            assertThat(repository.findByOrgKeyAndId(ORG, ID).orElseThrow().getExpression()).isEqualTo("true");
            assertThat(engine.evaluate(KEY, Map.of())).isTrue();
        }
        assertThat(evaluateOnWorker()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void importsFollowTheSameCommitAndRollbackPolicy(boolean rollback) {
        create.execute(ORG, input("true"));
        assertThat(evaluateOnWorker()).isTrue();
        transactions.executeWithoutResult(status -> {
            importFalseExpression();
            assertThat(engine.evaluate(KEY, Map.of())).isTrue();
            assertThat(evaluateOnWorker()).isTrue();
            if (rollback) status.setRollbackOnly();
        });
        assertThat(engine.evaluate(KEY, Map.of())).isEqualTo(rollback);
        assertThat(evaluateOnWorker()).isEqualTo(rollback);
    }

    @Test
    void evaluatingAnUncommittedRowDoesNotPublishItToOtherTransactions() {
        transactions.executeWithoutResult(status -> {
            create.execute(ORG, input("false"));
            assertThat(evaluate.execute(ORG, "Order", Map.of()).passed()).isFalse();
            assertThat(engine.isRegistered(KEY)).isFalse();
            assertThat(evaluateOnWorker()).isTrue();
            status.setRollbackOnly();
        });
        assertThat(engine.isRegistered(KEY)).isFalse();
        assertThat(evaluateOnWorker()).isTrue();
    }

    @Test
    void evaluatingAPendingEditDoesNotReplaceTheCommittedRegistration() {
        create.execute(ORG, input("true"));
        assertThat(evaluateOnWorker()).isTrue();
        transactions.executeWithoutResult(status -> {
            update.execute(ORG, ID, input("false"));
            assertThat(evaluate.execute(ORG, "Order", Map.of()).passed()).isFalse();
            assertThat(engine.evaluate(KEY, Map.of())).isTrue();
            assertThat(evaluateOnWorker()).isTrue();
            status.setRollbackOnly();
        });
        assertThat(engine.evaluate(KEY, Map.of())).isTrue();
        assertThat(evaluateOnWorker()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void replacementImportsKeepDeleteAndRecreateInCommitOrder(boolean rollback) {
        create.execute(ORG, input("true"));
        assertThat(evaluateOnWorker()).isTrue();
        transactions.executeWithoutResult(status -> {
            assertThat(importer.removeAll(ORG)).containsExactly(ID);
            importFalseExpression();
            assertThat(engine.evaluate(KEY, Map.of())).isTrue();
            assertThat(evaluateOnWorker()).isTrue();
            if (rollback) status.setRollbackOnly();
        });
        assertThat(repository.findByOrgKeyAndId(ORG, ID).orElseThrow().getExpression())
                .isEqualTo(rollback ? "true" : "false");
        assertThat(engine.evaluate(KEY, Map.of())).isEqualTo(rollback);
        assertThat(evaluateOnWorker()).isEqualTo(rollback);
    }

    @Test
    void evaluationUsesItsDatabaseSnapshotEvenIfRegistrationHasAnotherRevision() {
        create.execute(ORG, input("false"));
        engine.registerRule(KEY, "true");

        assertThat(evaluateOnWorker()).isFalse();
        assertThat(evaluate.execute(ORG, "Order", Map.of()).passed()).isFalse();
    }

    private boolean evaluateOnWorker() {
        return CompletableFuture.supplyAsync(
                () -> evaluate.execute(ORG, "Order", Map.of()).passed(), worker)
                .orTimeout(30, TimeUnit.SECONDS).join();
    }

    private void importFalseExpression() {
        String yaml = """
                rules:
                  - id: editable
                    name: Editable
                    context: Order
                    expression: "false"
                    severity: ERROR
                """;
        try {
            ImportOutcome result = importer.execute(ORG,
                    new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
            assertThat(result.errors()).isEmpty();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static RuleDefinitionInput input(String expression) {
        return new RuleDefinitionInput(ID, "Editable", "Order", expression, Severity.ERROR);
    }
}
