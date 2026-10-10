package com.processpuzzle.baseentity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.baseentity.common.JpaAuditingConfig;
import com.processpuzzle.baseentity.common.SecurityAuditorAware;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.definition.domain.Multiplicity;
import com.processpuzzle.baseentity.definition.domain.ValueKind;
import com.processpuzzle.baseentity.i18n.EntityTranslationBundle;
import com.processpuzzle.baseentity.i18n.EntityTranslationRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import com.processpuzzle.shared.event.StoredObjectsReleasedEvent;
import com.processpuzzle.shared.event.StoredObjectsReleasedEvent.StoredObjectRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Erasing one tenant removes every row this feature holds for it, and nothing of another's, and
 * releases the files its objects referred to once that has committed.
 *
 * <p>Against a real transaction, because that is the whole correctness argument: the listener is
 * {@code BEFORE_COMMIT}, and an {@code AFTER_COMMIT} one would have its deletes discarded without an
 * error. Publishing the event inside a committed transaction and finding the rows gone is what proves
 * the phase, which is why the class suspends {@code @DataJpaTest}'s own rolled-back transaction. The
 * released files are observed the way processpuzzle-store observes them, by an {@code AFTER_COMMIT}
 * listener, so a release that would reach the store on a rolled-back erasure fails here.
 *
 * <p>H2 in PostgreSQL mode, because payloads, attribute settings and translation bundles are
 * {@code jsonb}.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:entity-erasure;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan({"com.processpuzzle.baseentity.definition.domain", "com.processpuzzle.baseentity.instances.domain",
        "com.processpuzzle.baseentity.i18n"})
@EnableJpaRepositories({"com.processpuzzle.baseentity.definition.domain",
        "com.processpuzzle.baseentity.instances.domain", "com.processpuzzle.baseentity.i18n"})
@Import({TenantDataCleaner.class, JpaAuditingConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TenantDataCleanerTest {

    private static final String ERASED = "erased-org";
    private static final String KEPT = "kept-org";

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {

        /** By the name {@link JpaAuditingConfig} refers to it. */
        @Bean
        SecurityAuditorAware securityAuditorAware() {
            return new SecurityAuditorAware();
        }

        @Bean
        ReleasedFiles releasedFiles() {
            return new ReleasedFiles();
        }
    }

    /** Stands in for processpuzzle-store: hears a release only once the erasure has committed. */
    static class ReleasedFiles {

        private final List<StoredObjectsReleasedEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onObjectsReleased(StoredObjectsReleasedEvent event) {
            events.add(event);
        }

        List<StoredObjectRef> refs() {
            return events.stream().flatMap(event -> event.objects().stream()).toList();
        }
    }

    @Autowired
    private EntityDefinitionRepository definitions;

    @Autowired
    private EntityObjectRepository objects;

    @Autowired
    private EntityTranslationRepository translations;

    @Autowired
    private ReleasedFiles releasedFiles;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void erasesEveryRowOfTheTenantAndNothingOfAnother() {
        givenEntitiesFor(ERASED);
        givenEntitiesFor(KEPT);

        erase(ERASED);

        assertThat(definitions.findAllByOrgKey(ERASED)).isEmpty();
        assertThat(countAttributes(ERASED)).as("the child table goes too").isZero();
        assertThat(objects.countByOrgKey(ERASED)).isZero();
        assertThat(translations.findAll()).extracting(bundle -> bundle.getKey().getOrgKey()).containsOnly(KEPT);

        assertThat(definitions.findAllByOrgKey(KEPT)).hasSize(2);
        assertThat(countAttributes(KEPT)).isEqualTo(4);
        assertThat(objects.countByOrgKey(KEPT)).isEqualTo(3);
    }

    /**
     * Single and list-valued ARTIFACT values alike, each file once; malformed values and a look-alike map
     * under an attribute that is not ARTIFACT are skipped; nothing of the other tenant.
     */
    @Test
    void releasesTheFilesOfTheTenantsArtifactValuesAfterCommit() {
        givenEntitiesFor(ERASED);
        givenEntitiesFor(KEPT);

        erase(ERASED);

        assertThat(releasedFiles.events).singleElement()
                .extracting(StoredObjectsReleasedEvent::orgKey).isEqualTo(ERASED);
        assertThat(releasedFiles.refs()).containsExactlyInAnyOrder(
                new StoredObjectRef(bucket(ERASED), "cover-1"),
                new StoredObjectRef(bucket(ERASED), "attachment-1"),
                new StoredObjectRef(bucket(ERASED), "attachment-2"),
                new StoredObjectRef(bucket(ERASED), "cover-2"));
    }

    @Test
    void aRolledBackErasureDeletesNothingAndReleasesNothing() {
        givenEntitiesFor(ERASED);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new OrganizationDeletedEvent(ERASED));
            status.setRollbackOnly();
        });

        assertThat(definitions.findAllByOrgKey(ERASED)).hasSize(2);
        assertThat(countAttributes(ERASED)).isEqualTo(4);
        assertThat(objects.countByOrgKey(ERASED)).isEqualTo(3);
        assertThat(translations.count()).isEqualTo(1);
        assertThat(releasedFiles.events).isEmpty();
    }

    /** A tenant with nothing here — or erased twice — is not an error, and has no files to release. */
    @Test
    void erasingATenantWithNothingIsHarmless() {
        erase("nobody");

        assertThat(definitions.count()).isZero();
        assertThat(releasedFiles.events).isEmpty();
    }

    /** Objects whose ARTIFACT values are all empty release nothing — the event is not published empty. */
    @Test
    void aTenantWithoutFilesPublishesNoRelease() {
        BaseEntityDefinition document = definition(ERASED, "document");
        document.addAttribute(attribute("cover", FormControlType.ARTIFACT, null));
        definitions.saveAndFlush(document);
        objects.saveAndFlush(object(ERASED, "document", Map.of("title", "no cover")));

        erase(ERASED);

        assertThat(objects.countByOrgKey(ERASED)).isZero();
        assertThat(releasedFiles.events).isEmpty();
    }

    private void erase(String orgKey) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new OrganizationDeletedEvent(orgKey)));
    }

    /**
     * Two definitions — {@code document} with a single and a list-valued ARTIFACT attribute, {@code note}
     * with none — three objects and a translation bundle.
     */
    private void givenEntitiesFor(String orgKey) {
        BaseEntityDefinition document = definition(orgKey, "document");
        document.addAttribute(attribute("title", FormControlType.TEXT, null));
        document.addAttribute(attribute("cover", FormControlType.ARTIFACT, null));
        document.addAttribute(attribute("attachments", FormControlType.ARTIFACT, Multiplicity.ZERO_TO_N));
        definitions.saveAndFlush(document);

        BaseEntityDefinition note = definition(orgKey, "note");
        note.addAttribute(attribute("cover", FormControlType.TEXT, null));
        definitions.saveAndFlush(note);

        List<Object> attachments = new ArrayList<>();
        attachments.add(artifact(orgKey, "attachment-1"));
        attachments.add(artifact(orgKey, "attachment-2"));
        attachments.add(artifact(orgKey, "cover-1"));
        attachments.add("not an artifact");
        attachments.add(Map.of("bucket", bucket(orgKey)));
        objects.saveAndFlush(object(orgKey, "document",
                Map.of("title", "first", "cover", artifact(orgKey, "cover-1"), "attachments", attachments)));
        objects.saveAndFlush(object(orgKey, "document",
                Map.of("title", "second", "cover", artifact(orgKey, "cover-2"), "attachments", "malformed")));
        objects.saveAndFlush(object(orgKey, "note", Map.of("cover", artifact(orgKey, "not-a-file"))));

        translations.saveAndFlush(new EntityTranslationBundle(orgKey, "entity", "en", Map.of("title", "Entities")));
    }

    private int countAttributes(String orgKey) {
        Integer count = jdbc.queryForObject("select count(*) from base_entity_attribute a "
                + "join base_entity_definition d on d.id = a.entity_definition_id where d.org_key = ?",
                Integer.class, orgKey);
        return count == null ? 0 : count;
    }

    private static BaseEntityDefinition definition(String orgKey, String code) {
        return BaseEntityDefinition.builder().orgKey(orgKey).code(code).name(code).build();
    }

    private static BaseEntityAttribute attribute(String code, FormControlType controlType, Multiplicity multiplicity) {
        return BaseEntityAttribute.builder().code(code).name(code)
                .valueKind(controlType == FormControlType.ARTIFACT ? ValueKind.REFERENCE : ValueKind.TEXT)
                .formControlType(controlType).multiplicity(multiplicity).build();
    }

    private static EntityObject object(String orgKey, String definitionCode, Map<String, Object> payload) {
        return EntityObject.builder().orgKey(orgKey).entityDefinitionCode(definitionCode)
                .payload(new HashMap<>(payload)).build();
    }

    private static Map<String, Object> artifact(String orgKey, String objectId) {
        return Map.of("bucket", bucket(orgKey), "objectId", objectId, "name", objectId + ".png",
                "mimeType", "image/png");
    }

    private static String bucket(String orgKey) {
        return orgKey + "-files";
    }

    @AfterEach
    void cleanUp() {
        objects.deleteAll();
        definitions.deleteAll();
        translations.deleteAll();
        releasedFiles.events.clear();
    }
}
