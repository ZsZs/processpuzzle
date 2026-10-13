package com.processpuzzle.baseentity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.processpuzzle.baseentity.common.JpaAuditingConfig;
import com.processpuzzle.baseentity.common.SecurityAuditorAware;
import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.adapters.outbound.EntityDefinitionChannelBackfill;
import com.processpuzzle.baseentity.definition.adapters.outbound.EntityDefinitionStatusMigration;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.definition.domain.Multiplicity;
import com.processpuzzle.baseentity.definition.domain.ValueKind;
import com.processpuzzle.baseentity.definition.usecases.outbound.EntityInstanceExistenceCheckPort;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Releasing entity definitions over a real repository and real transactions, because what can go wrong is
 * Hibernate's: the order it flushes attribute inserts and orphan deletes in, against the
 * {@code (entity_definition_id, code)} key. H2 in PostgreSQL mode, for the {@code jsonb} columns.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:entity-release;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.baseentity.definition.domain")
@EnableJpaRepositories("com.processpuzzle.baseentity.definition.domain")
@Import({EntityDefinitionReleaseParticipant.class, EntityDefinitionFingerprint.class, EntityDefinitionMapper.class,
        EntityDefinitionChannelBackfill.class, EntityDefinitionStatusMigration.class, JpaAuditingConfig.class, EntityDefinitionReleaseParticipantTest.Instances.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EntityDefinitionReleaseParticipantTest {

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

        @Bean
        SecurityAuditorAware securityAuditorAware() {
            return new SecurityAuditorAware();
        }
    }

    /** Which definition codes have objects, as the test sets it. */
    static class Instances implements EntityInstanceExistenceCheckPort {
        final Set<String> withObjects = new HashSet<>();

        @Override
        public boolean existsAnyInstanceOf(String orgKey, String entityDefinitionCode) {
            return withObjects.contains(entityDefinitionCode);
        }
    }

    @Autowired
    private EntityDefinitionReleaseParticipant participant;
    @Autowired
    private EntityDefinitionRepository repository;
    @Autowired
    private EntityDefinitionChannelBackfill backfill;
    @Autowired
    private EntityDefinitionStatusMigration statusMigration;
    @Autowired
    private Instances instances;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        instances.withObjects.clear();
    }

    @Test
    void theFirstReleaseCopiesTheDraft_withIdsOfItsOwn() {
        BaseEntityDefinition draft = saveDraft(definition("claim", attribute("amount", ValueKind.NUMBER), attribute("note", ValueKind.TEXT)));
        assertThat(participant.pending(ORG)).extracting(PendingChange::key, PendingChange::change)
            .containsExactly(tuple("claim", PendingChange.Change.ADDED));

        release();

        BaseEntityDefinition released = released("claim");
        assertThat(released.getId()).isNotEqualTo(draft.getId());
        assertThat(released.getAttributes()).extracting(BaseEntityAttribute::getCode).containsExactly("amount", "note");
        assertThat(released.getAttributes()).extracting(BaseEntityAttribute::getId)
            .doesNotContainAnyElementsOf(draft.getAttributes().stream().map(BaseEntityAttribute::getId).toList());
        assertThat(participant.pending(ORG)).isEmpty();
    }

    @Test
    void aLaterReleaseUpdatesTheReleasedRowInPlace_attributeByAttribute() {
        saveDraft(definition("claim", attribute("amount", ValueKind.NUMBER), attribute("note", ValueKind.TEXT)));
        release();
        UUID releasedId = released("claim").getId();
        UUID amountId = released("claim").getAttributes().getFirst().getId();

        transaction.executeWithoutResult(status -> {
            BaseEntityDefinition draft = draft("claim");
            draft.setName("Insurance claim");
            draft.getAttributes().removeIf(attribute -> attribute.getCode().equals("note"));
            draft.getAttributes().getFirst().setName("Claimed amount");
            repository.save(draft);
        });
        // Added again under the same code, as a new row: the released row must be matched to it by code.
        transaction.executeWithoutResult(status -> {
            BaseEntityDefinition draft = draft("claim");
            draft.addAttribute(attribute("note", ValueKind.TEXT));
            draft.addAttribute(attribute("filed", ValueKind.DATE));
            repository.save(draft);
        });
        assertThat(participant.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.CHANGED);

        release();

        BaseEntityDefinition released = released("claim");
        assertThat(released.getId()).isEqualTo(releasedId);
        assertThat(released.getName()).isEqualTo("Insurance claim");
        assertThat(released.getAttributes()).extracting(BaseEntityAttribute::getCode, BaseEntityAttribute::getName)
            .containsExactlyInAnyOrder(tuple("amount", "Claimed amount"), tuple("note", "note"), tuple("filed", "filed"));
        assertThat(released.getAttributes()).filteredOn(attribute -> attribute.getCode().equals("amount"))
            .extracting(BaseEntityAttribute::getId).containsExactly(amountId);
        assertThat(participant.pending(ORG)).isEmpty();
    }

    @Test
    void releasingADeletedDraftDeletesTheReleasedRow() {
        saveDraft(definition("claim", attribute("amount", ValueKind.NUMBER)));
        release();
        transaction.executeWithoutResult(status -> repository.delete(draft("claim")));
        assertThat(participant.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.REMOVED);

        release();

        assertThat(repository.findByOrgKeyAndCodeAndChannel(ORG, "claim", Channel.RELEASED)).isEmpty();
    }

    @Test
    void refusesWhatExistingObjectsWouldNoLongerFit() {
        saveDraft(definition("claim", attribute("amount", ValueKind.NUMBER), attribute("note", ValueKind.TEXT),
            attribute("status", ValueKind.TEXT)));
        saveDraft(definition("order", attribute("number", ValueKind.TEXT)));
        release();
        transaction.executeWithoutResult(status -> {
            BaseEntityDefinition claim = draft("claim");
            claim.getAttributes().removeIf(attribute -> attribute.getCode().equals("note"));
            claim.getAttributes().stream().filter(attribute -> attribute.getCode().equals("amount"))
                .forEach(attribute -> attribute.setValueKind(ValueKind.TEXT));
            claim.getAttributes().stream().filter(attribute -> attribute.getCode().equals("status"))
                .forEach(attribute -> attribute.setRequired(true));
            BaseEntityAttribute reference = attribute("reference", ValueKind.TEXT);
            reference.setRequired(true);
            claim.addAttribute(reference);
            repository.save(claim);
            repository.delete(draft("order"));
        });
        instances.withObjects.addAll(Set.of("claim", "order"));

        assertThat(participant.check(ORG, EVERYTHING)).extracting(ReleaseProblem::severity, ReleaseProblem::key, ReleaseProblem::errorId)
            .containsExactlyInAnyOrder(
                tuple(ReleaseProblem.Severity.WARNING, "claim", "entity.release.attributeRemoved"),
                tuple(ReleaseProblem.Severity.ERROR, "claim", "entity.release.attributeTypeChanged"),
                tuple(ReleaseProblem.Severity.WARNING, "claim", "entity.release.attributeNowRequired"),
                tuple(ReleaseProblem.Severity.ERROR, "claim", "entity.release.requiredWithoutDefault"),
                tuple(ReleaseProblem.Severity.ERROR, "order", "entity.release.removedWithObjects"));
    }

    @Test
    void withoutObjectsAnyChangeIsFine() {
        saveDraft(definition("claim", attribute("amount", ValueKind.NUMBER)));
        release();
        transaction.executeWithoutResult(status -> repository.delete(draft("claim")));

        assertThat(participant.check(ORG, EVERYTHING)).isEmpty();
    }

    @Test
    void reportsWhatTheDraftsReferTo() {
        BaseEntityDefinition address = definition("address", attribute("street", ValueKind.TEXT));
        address.setComponentParents(List.of("customer"));
        address.setEmbedded(true);
        BaseEntityAttribute customer = attribute("customer", ValueKind.TEXT);
        customer.setLinkedEntityType("customer");
        saveDraft(address);
        saveDraft(definition("order", customer));

        assertThat(participant.references(ORG)).extracting(DefinitionReference::fromKey, DefinitionReference::toKind,
                DefinitionReference::toKey, DefinitionReference::optional)
            .containsExactlyInAnyOrder(tuple("address", DefinitionKinds.ENTITY, "customer", false),
                tuple("order", DefinitionKinds.ENTITY, "customer", false));
    }

    /** The table as it stood before channels: one row per definition, unique on (org_key, code). */
    @Test
    void theBackfillReleasesEveryExistingDefinitionOnce_andNeverAgain() {
        saveDraft(definition("claim", attribute("amount", ValueKind.NUMBER)));
        saveDraft(definition("order", attribute("number", ValueKind.TEXT)));
        jdbc.execute("ALTER TABLE base_entity_definition ADD CONSTRAINT uk_before_channels UNIQUE (org_key, code)");

        assertThat(backfill.migrateOnce()).isEqualTo(2);

        assertThat(backfill.oldUniqueKeys()).isEmpty();
        assertThat(released("claim").getAttributes()).extracting(BaseEntityAttribute::getCode).containsExactly("amount");
        assertThat(participant.pending(ORG)).isEmpty();

        saveDraft(definition("invoice", attribute("total", ValueKind.NUMBER)));
        assertThat(backfill.migrateOnce()).isZero();
        assertThat(repository.findByOrgKeyAndCodeAndChannel(ORG, "invoice", Channel.RELEASED)).isEmpty();
    }

    @Test
    void theBackfillLeavesATableCreatedWithChannelsAlone() {
        saveDraft(definition("claim", attribute("amount", ValueKind.NUMBER)));

        assertThat(backfill.migrateOnce()).isZero();
        assertThat(repository.findByOrgKeyAndCodeAndChannel(ORG, "claim", Channel.RELEASED)).isEmpty();
    }

    /** The retired DRAFT status is never stored: a release keeps a definition off the running application now. */
    @Test
    @SuppressWarnings("deprecation")
    void aDefinitionSavedAsDraftIsStoredActive() {
        BaseEntityDefinition definition = definition("claim");
        definition.setStatus(EntityDefinitionStatus.DRAFT);

        saveDraft(definition);

        assertThat(draft("claim").getStatus()).isEqualTo(EntityDefinitionStatus.ACTIVE);
    }

    /**
     * A definition stored as DRAFT before releases existed was never live: it stays a draft, now ACTIVE, and loses the
     * released copy the channel backfill gave it, so it waits for its first release.
     */
    @Test
    void theStatusMigrationTurnsARetiredDraftIntoAnUnreleasedDefinition_once() {
        saveDraft(definition("claim", attribute("amount", ValueKind.NUMBER)));
        saveDraft(definition("order", attribute("number", ValueKind.TEXT)));
        release();
        jdbc.update("UPDATE base_entity_definition SET status = 'DRAFT' WHERE code = 'claim'");

        assertThat(statusMigration.migrateOnce()).isEqualTo(1);

        assertThat(draft("claim").getStatus()).isEqualTo(EntityDefinitionStatus.ACTIVE);
        assertThat(repository.findByOrgKeyAndCodeAndChannel(ORG, "claim", Channel.RELEASED)).isEmpty();
        assertThat(released("order").getStatus()).isEqualTo(EntityDefinitionStatus.ACTIVE);
        assertThat(participant.pending(ORG)).extracting(PendingChange::key, PendingChange::change)
            .containsExactly(tuple("claim", PendingChange.Change.ADDED));
        assertThat(statusMigration.migrateOnce()).isZero();
    }

    private void release() {
        transaction.executeWithoutResult(status -> participant.release(ORG));
    }

    private BaseEntityDefinition saveDraft(BaseEntityDefinition definition) {
        return transaction.execute(status -> repository.save(definition));
    }

    private BaseEntityDefinition draft(String code) {
        return repository.findByOrgKeyAndCodeAndChannel(ORG, code, Channel.DRAFT).orElseThrow();
    }

    private BaseEntityDefinition released(String code) {
        return repository.findByOrgKeyAndCodeAndChannel(ORG, code, Channel.RELEASED).orElseThrow();
    }

    private static BaseEntityDefinition definition(String code, BaseEntityAttribute... attributes) {
        BaseEntityDefinition definition = BaseEntityDefinition.builder().orgKey(ORG).code(code).name(code).build();
        for (int index = 0; index < attributes.length; index++) {
            attributes[index].setDisplayOrder(index);
            definition.addAttribute(attributes[index]);
        }
        return definition;
    }

    private static BaseEntityAttribute attribute(String code, ValueKind kind) {
        return BaseEntityAttribute.builder().code(code).name(code).valueKind(kind)
            .formControlType(FormControlType.TEXT_BOX).multiplicity(Multiplicity.ZERO_TO_ONE).build();
    }
}
