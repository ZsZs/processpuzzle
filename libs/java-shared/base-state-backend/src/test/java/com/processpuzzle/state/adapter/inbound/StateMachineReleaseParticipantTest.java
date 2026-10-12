package com.processpuzzle.state.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.state.domain.DiagramDefinition;
import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.domain.DiagramViewport;
import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.StateMachineReleaser;
import com.processpuzzle.state.usecase.port.EntityObjectGateway;
import com.processpuzzle.state.usecase.port.EntityObjectSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * Releasing state machines over real repositories and real transactions: a machine and its layout go out together,
 * and what objects already hold decides what may not.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:state-release;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.state.domain")
@EnableJpaRepositories("com.processpuzzle.state.domain")
@Import({StateMachineReleaseParticipant.class, StateMachineReleaser.class, StateMachineReleaseParticipantTest.Objects.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StateMachineReleaseParticipantTest {

    private static final String ORG = "my-org";
    private static final ReleaseCandidate EVERYTHING = new ReleaseCandidate() {
        @Override
        public boolean exists(String kind, String key) {
            return true;
        }

        @Override
        public boolean manages(String kind) {
            return true;
        }
    };

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    /** The objects of the 'order' type, as the test sets them. */
    static class Objects implements EntityObjectGateway {
        final List<EntityObjectSnapshot> orders = new ArrayList<>();

        @Override
        public EntityObjectSnapshot findObject(String orgKey, String entityName, UUID objectId) {
            return null;
        }

        @Override
        public List<EntityObjectSnapshot> findObjects(String orgKey, String entityName) {
            return "order".equals(entityName) ? orders : List.of();
        }

        @Override
        public long updateStateAttribute(String orgKey, String entityName, UUID objectId, String attributeKey,
                                         String stateKey, long expectedVersion) {
            return 0;
        }
    }

    @Autowired
    private StateMachineReleaseParticipant participant;
    @Autowired
    private StateMachineDefinitionRepository machines;
    @Autowired
    private DiagramDefinitionRepository diagrams;
    @Autowired
    private Objects objects;
    @Autowired
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        diagrams.deleteAll();
        machines.deleteAll();
        objects.orders.clear();
    }

    @Test
    void releasesTheMachineTogetherWithItsLayout() {
        saveDraft(machine("status", "NEW", "SHIPPED"));
        transaction.executeWithoutResult(status -> diagrams.save(diagram()));
        assertThat(participant.pending(ORG)).extracting(PendingChange::key, PendingChange::change)
                .containsExactly(tuple("order", PendingChange.Change.ADDED));

        release();

        assertThat(machines.findByOrgKeyAndEntityNameAndChannel(ORG, "order", Channel.RELEASED)).get()
                .extracting(StateMachineDefinition::getInitialStateKey).isEqualTo("NEW");
        assertThat(diagrams.findByOrgKeyAndEntityNameAndChannel(ORG, "order", Channel.RELEASED)).get()
                .extracting(DiagramDefinition::getViewport).isEqualTo(new DiagramViewport(1, 2, 0.5));
        assertThat(participant.pending(ORG)).isEmpty();
    }

    @Test
    void aMovedLayoutAloneIsAPendingChange_andNeverAProblem() {
        saveDraft(machine("status", "NEW", "SHIPPED"));
        transaction.executeWithoutResult(status -> diagrams.save(diagram()));
        release();
        objects.orders.add(order("NEW"));

        transaction.executeWithoutResult(status -> {
            DiagramDefinition draft = diagrams.findByOrgKeyAndEntityNameAndChannel(ORG, "order", Channel.DRAFT).orElseThrow();
            draft.replaceLayout(draft.getNodes(), draft.getEdges(), new DiagramViewport(5, 5, 1));
            diagrams.save(draft);
        });

        assertThat(participant.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.CHANGED);
        assertThat(participant.check(ORG, EVERYTHING)).isEmpty();
    }

    @Test
    void refusesToRemoveAStateObjectsAreIn_butNotOneNobodyIsIn() {
        saveDraft(machine("status", "NEW", "PACKED", "SHIPPED"));
        release();
        objects.orders.addAll(List.of(order("PACKED"), order("PACKED"), order("NEW")));
        transaction.executeWithoutResult(status -> {
            StateMachineDefinition draft = machines.findByOrgKeyAndEntityNameAndChannel(ORG, "order", Channel.DRAFT).orElseThrow();
            draft.replaceTopology("Order", null, "status", "NEW", List.of(state("NEW")), List.of());
            machines.save(draft);
        });

        assertThat(participant.check(ORG, EVERYTHING))
                .extracting(ReleaseProblem::severity, ReleaseProblem::errorId)
                .containsExactly(tuple(ReleaseProblem.Severity.ERROR, "state.release.stateRemovedWithObjects"));
        assertThat(participant.check(ORG, EVERYTHING).getFirst().message()).contains("PACKED").contains("2 object");
    }

    @Test
    void refusesAChangedStateAttribute_andWarnsOfARemovedMachine_whileObjectsExist() {
        saveDraft(machine("status", "NEW"));
        release();
        objects.orders.add(order("NEW"));
        transaction.executeWithoutResult(status -> {
            StateMachineDefinition draft = machines.findByOrgKeyAndEntityNameAndChannel(ORG, "order", Channel.DRAFT).orElseThrow();
            draft.setStateAttributeKey("phase");
            machines.save(draft);
        });
        assertThat(participant.check(ORG, EVERYTHING)).extracting(ReleaseProblem::errorId)
                .containsExactly("state.release.stateAttributeChanged");

        transaction.executeWithoutResult(status ->
                machines.delete(machines.findByOrgKeyAndEntityNameAndChannel(ORG, "order", Channel.DRAFT).orElseThrow()));
        assertThat(participant.check(ORG, EVERYTHING)).extracting(ReleaseProblem::severity, ReleaseProblem::errorId)
                .containsExactly(tuple(ReleaseProblem.Severity.WARNING, "state.release.machineRemoved"));

        release();
        assertThat(machines.findByOrgKeyAndEntityNameAndChannel(ORG, "order", Channel.RELEASED)).isEmpty();
    }

    @Test
    void aMachineReferToTheEntityTypeItGoverns() {
        saveDraft(machine("status", "NEW"));

        assertThat(participant.references(ORG)).extracting(DefinitionReference::toKind, DefinitionReference::toKey)
                .containsExactly(tuple(DefinitionKinds.ENTITY, "order"));
    }

    @Test
    void releasingOneMachineLeavesTheOthersDrafts() {
        saveDraft(machine("status", "NEW"));
        StateMachineDefinition invoice = StateMachineDefinition.builder().orgKey(ORG).entityName("invoice").name("Invoice")
                .stateAttributeKey("status").initialStateKey("OPEN").states(List.of(state("OPEN"))).transitions(List.of()).build();
        saveDraft(invoice);

        transaction.executeWithoutResult(status -> new StateMachineReleaser(machines, diagrams).release(ORG, "order"));

        assertThat(machines.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).extracting(StateMachineDefinition::getEntityName)
                .containsExactly("order");
    }

    private void release() {
        transaction.executeWithoutResult(status -> participant.release(ORG));
    }

    private void saveDraft(StateMachineDefinition machine) {
        transaction.executeWithoutResult(status -> machines.save(machine));
    }

    private static StateMachineDefinition machine(String stateAttributeKey, String... states) {
        return StateMachineDefinition.builder().orgKey(ORG).entityName("order").name("Order")
                .stateAttributeKey(stateAttributeKey).initialStateKey(states[0])
                .states(java.util.Arrays.stream(states).map(StateMachineReleaseParticipantTest::state).toList())
                .transitions(List.of()).build();
    }

    private static State state(String key) {
        return new State(key, key, null, false, false, null);
    }

    private static DiagramDefinition diagram() {
        return DiagramDefinition.builder().orgKey(ORG).entityName("order").viewport(new DiagramViewport(1, 2, 0.5)).build();
    }

    private static EntityObjectSnapshot order(String status) {
        return new EntityObjectSnapshot(UUID.randomUUID(), 0, Map.of("status", status));
    }
}
