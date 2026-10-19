package com.processpuzzle.event.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PlatformSubject;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Releasing event definitions over a real repository and real transactions. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:event-release;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.event.domain")
@EnableJpaRepositories("com.processpuzzle.event.domain")
@Import(EventDefinitionReleaseParticipant.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EventDefinitionReleaseParticipantTest {

    private static final String ORG = "my-org";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        PlatformSubject calculationRuns() {
            return new PlatformSubject("calculation-run", Set.of("calculationId"));
        }
    }

    @Autowired
    private EventDefinitionReleaseParticipant participant;
    @Autowired
    private EventDefinitionRepository repository;
    @Autowired
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void theFirstReleaseCopiesTheDraft_andALaterOneUpdatesTheReleasedRowInPlace() {
        saveDraft(created("OrderCreated"));
        assertThat(participant.pending(ORG)).extracting(PendingChange::key, PendingChange::change)
                .containsExactly(tuple("OrderCreated", PendingChange.Change.ADDED));

        release();
        EventDefinition released = released("OrderCreated");
        assertThat(released.getAction()).isEqualTo(PlatformEventAction.CREATED);
        assertThat(participant.pending(ORG)).isEmpty();

        transaction.executeWithoutResult(status -> {
            EventDefinition draft = draft("OrderCreated");
            draft.setDescription("An order was placed.");
            repository.save(draft);
        });
        assertThat(participant.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.CHANGED);
        assertThat(released("OrderCreated").getDescription()).isNull();

        release();
        assertThat(released("OrderCreated").getDescription()).isEqualTo("An order was placed.");
        assertThat(released("OrderCreated").getVersion()).isEqualTo(released.getVersion() + 1);
        assertThat(participant.pending(ORG)).isEmpty();
    }

    @Test
    void releasingADeletedDraftDeletesTheReleasedRow() {
        saveDraft(created("OrderCreated"));
        release();
        transaction.executeWithoutResult(status -> repository.delete(draft("OrderCreated")));
        assertThat(participant.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.REMOVED);

        release();

        assertThat(repository.findByOrgKeyAndIdAndChannel(ORG, "OrderCreated", Channel.RELEASED)).isEmpty();
        assertThat(participant.pending(ORG)).isEmpty();
    }

    /** What a seed loader does: release what it wrote, and not a designer's other drafts. */
    @Test
    void releasingChosenDraftsLeavesTheOthersDrafts() {
        saveDraft(created("OrderCreated"));
        saveDraft(created("InvoiceCreated"));

        transaction.executeWithoutResult(status -> participant.release(ORG, List.of("OrderCreated")));

        assertThat(repository.findByOrgKeyAndIdAndChannel(ORG, "OrderCreated", Channel.RELEASED)).isPresent();
        assertThat(participant.pending(ORG)).extracting(PendingChange::key).containsExactly("InvoiceCreated");
    }

    /** A SYSTEM event is about an entity type; a MESSAGE event names none. */
    @Test
    void aSystemEventRefersToTheEntityTypeItsFactsAreAbout() {
        saveDraft(created("OrderCreated"));
        saveDraft(EventDefinition.builder().orgKey(ORG).id("PaymentReceived").name("Payment received")
                .kind(EventKind.MESSAGE).build());

        assertThat(participant.references(ORG)).extracting(DefinitionReference::fromKey, DefinitionReference::toKind,
                        DefinitionReference::toKey)
                .containsExactly(tuple("OrderCreated", DefinitionKinds.ENTITY, "order"));
        assertThat(participant.drafts(ORG)).containsExactly("OrderCreated", "PaymentReceived");
    }

    /** A subject a feature declares as a platform subject is no entity type, so the event refers to nothing. */
    @Test
    void anEventAboutADeclaredPlatformSubjectRefersToNoEntityType() {
        saveDraft(EventDefinition.builder().orgKey(ORG).id("CalculationFrozen").name("Calculation frozen")
                .kind(EventKind.SYSTEM).subjectType("calculation-run").action(PlatformEventAction.STATE_CHANGED).state("FROZEN").build());

        assertThat(participant.references(ORG)).isEmpty();
    }

    private void release() {
        transaction.executeWithoutResult(status -> participant.release(ORG));
    }

    private void saveDraft(EventDefinition definition) {
        transaction.executeWithoutResult(status -> repository.save(definition));
    }

    private EventDefinition draft(String id) {
        return repository.findByOrgKeyAndIdAndChannel(ORG, id, Channel.DRAFT).orElseThrow();
    }

    private EventDefinition released(String id) {
        return repository.findByOrgKeyAndIdAndChannel(ORG, id, Channel.RELEASED).orElseThrow();
    }

    private static EventDefinition created(String id) {
        return EventDefinition.builder().orgKey(ORG).id(id).name(id).kind(EventKind.SYSTEM)
                .subjectType("order").action(PlatformEventAction.CREATED).build();
    }
}
