package com.processpuzzle.event.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
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

/**
 * Erasing one tenant removes its event catalog, and nothing of another's.
 *
 * <p>Against a real transaction, because that is the whole correctness argument: the listener is
 * {@code BEFORE_COMMIT}, and an {@code AFTER_COMMIT} one would have its delete discarded without an
 * error. Publishing the event inside a committed transaction and finding the rows gone is what proves
 * the phase, which is why the class suspends {@code @DataJpaTest}'s own rolled-back transaction.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:event-erasure;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.event.domain")
@EnableJpaRepositories("com.processpuzzle.event.domain")
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
    private EventDefinitionRepository definitions;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void erasesEveryRowOfTheTenantAndNothingOfAnother() {
        givenEventDefinitionsFor(ERASED);
        givenEventDefinitionsFor(KEPT);

        erase(ERASED);

        assertThat(definitions.findByOrgKeyOrderByIdAsc(ERASED)).isEmpty();
        assertThat(definitions.findByOrgKeyOrderByIdAsc(KEPT))
                .extracting(EventDefinition::getId).containsExactly("OrderCreatedEvent", "PaymentReceived");
    }

    @Test
    void aRolledBackErasureDeletesNothing() {
        givenEventDefinitionsFor(ERASED);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new OrganizationDeletedEvent(ERASED));
            status.setRollbackOnly();
        });

        assertThat(definitions.findByOrgKeyOrderByIdAsc(ERASED)).hasSize(2);
    }

    /** A tenant with nothing here — or erased twice — is not an error. */
    @Test
    void erasingATenantWithNoEventDefinitionsIsHarmless() {
        erase("nobody");

        assertThat(definitions.count()).isZero();
    }

    private void erase(String orgKey) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new OrganizationDeletedEvent(orgKey)));
    }

    private void givenEventDefinitionsFor(String orgKey) {
        definitions.saveAndFlush(EventDefinition.builder()
                .orgKey(orgKey).id("OrderCreatedEvent").name("Order created").kind(EventKind.SYSTEM)
                .subjectType("order").action(PlatformEventAction.CREATED)
                .build());
        definitions.saveAndFlush(EventDefinition.builder()
                .orgKey(orgKey).id("PaymentReceived").name("Payment received").kind(EventKind.MESSAGE)
                .build());
    }

    @AfterEach
    void cleanUp() {
        definitions.deleteAll();
    }
}
