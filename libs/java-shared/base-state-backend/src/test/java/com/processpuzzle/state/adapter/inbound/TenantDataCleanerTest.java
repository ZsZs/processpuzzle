package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import com.processpuzzle.state.domain.DiagramDefinition;
import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.i18n.StateTranslationBundle;
import com.processpuzzle.state.i18n.StateTranslationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Erasing one tenant removes every row this feature holds for it, and nothing of another's.
 *
 * <p>Against a real transaction, because that is the whole correctness argument: the listener is
 * {@code BEFORE_COMMIT}, and an {@code AFTER_COMMIT} one would have its deletes discarded without an
 * error. Publishing the event inside a committed transaction and finding the rows gone is what proves
 * the phase, which is why the class suspends {@code @DataJpaTest}'s own rolled-back transaction.
 *
 * <p>H2 in PostgreSQL mode, because the translation bundles map their messages as {@code jsonb}. Only
 * this feature's packages are scanned, so base-entity — on the classpath, not under test — stays out.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:state-erasure;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan({"com.processpuzzle.state.domain", "com.processpuzzle.state.i18n"})
@EnableJpaRepositories({"com.processpuzzle.state.domain", "com.processpuzzle.state.i18n"})
@Import(TenantDataCleaner.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantDataCleanerTest {

    private static final String ERASED = "erased-org";
    private static final String KEPT = "kept-org";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private StateMachineDefinitionRepository stateMachines;

    @Autowired
    private DiagramDefinitionRepository diagrams;

    @Autowired
    private StateTranslationRepository translations;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void erasesEveryRowOfTheTenantAndNothingOfAnother() {
        givenStateMachinesDiagramsAndTranslationsFor(ERASED);
        givenStateMachinesDiagramsAndTranslationsFor(KEPT);

        erase(ERASED);

        assertThat(stateMachines.findByOrgKey(ERASED)).isEmpty();
        assertThat(diagrams.findByOrgKey(ERASED)).isEmpty();
        assertThat(translations.findAll()).extracting(bundle -> bundle.getKey().getOrgKey())
                .containsOnly(KEPT);

        assertThat(stateMachines.findByOrgKey(KEPT)).hasSize(2);
        assertThat(diagrams.findByOrgKey(KEPT)).hasSize(2);
    }

    @Test
    void aRolledBackErasureDeletesNothing() {
        givenStateMachinesDiagramsAndTranslationsFor(ERASED);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new OrganizationDeletedEvent(ERASED));
            status.setRollbackOnly();
        });

        assertThat(stateMachines.findByOrgKey(ERASED)).hasSize(2);
        assertThat(diagrams.findByOrgKey(ERASED)).hasSize(2);
        assertThat(translations.count()).isEqualTo(1);
    }

    /** A tenant with nothing here — or erased twice — is not an error. */
    @Test
    void erasingATenantWithNoStateMachinesIsHarmless() {
        erase("nobody");

        assertThat(stateMachines.count()).isZero();
    }

    private void erase(String orgKey) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new OrganizationDeletedEvent(orgKey)));
    }

    private void givenStateMachinesDiagramsAndTranslationsFor(String orgKey) {
        for (String entityName : new String[] {"Order", "Invoice"}) {
            stateMachines.saveAndFlush(stateMachine(orgKey, entityName));
            diagrams.saveAndFlush(DiagramDefinition.builder().orgKey(orgKey).entityName(entityName).build());
        }
        translations.saveAndFlush(new StateTranslationBundle(orgKey, "state", "en", Map.of("title", "States")));
    }

    private static StateMachineDefinition stateMachine(String orgKey, String entityName) {
        return StateMachineDefinition.builder().orgKey(orgKey).entityName(entityName)
                .name(entityName + " lifecycle").stateAttributeKey("status").initialStateKey("draft").build();
    }

    @AfterEach
    void cleanUp() {
        stateMachines.deleteAll();
        diagrams.deleteAll();
        translations.deleteAll();
    }
}
