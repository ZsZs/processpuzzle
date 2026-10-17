package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentSignature;
import com.processpuzzle.document.domain.DocumentSignatureRepository;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.i18n.DocumentUiTranslationBundle;
import com.processpuzzle.document.i18n.DocumentUiTranslationRepository;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
        "spring.datasource.url=jdbc:h2:mem:document-erasure;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan({"com.processpuzzle.document.domain", "com.processpuzzle.document.i18n"})
@EnableJpaRepositories({"com.processpuzzle.document.domain", "com.processpuzzle.document.i18n"})
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
    private DocumentRepository documents;

    @Autowired
    private DocumentDraftRepository drafts;

    @Autowired
    private PublishedDocumentRepository published;

    @Autowired
    private DocumentUiTranslationRepository translations;

    @Autowired
    private DocumentSignatureRepository signatures;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void erasesEveryRowOfTheTenantAndNothingOfAnother() {
        givenDocumentsAndTranslationsFor(ERASED);
        givenDocumentsAndTranslationsFor(KEPT);

        erase(ERASED);

        assertThat(documents.findByOrgKey(ERASED)).isEmpty();
        assertThat(drafts.findAll()).extracting(DocumentDraft::getOrgKey).containsOnly(KEPT).hasSize(4);
        assertThat(published.findAll()).extracting(PublishedDocument::getOrgKey).containsOnly(KEPT).hasSize(2);
        assertThat(translations.findAll()).extracting(bundle -> bundle.getKey().getOrgKey())
                .containsOnly(KEPT);
        assertThat(signatures.findAll()).extracting(DocumentSignature::getOrgKey).containsOnly(KEPT).hasSize(2);

        assertThat(documents.findByOrgKey(KEPT)).hasSize(2);
    }

    @Test
    void aRolledBackErasureDeletesNothing() {
        givenDocumentsAndTranslationsFor(ERASED);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new OrganizationDeletedEvent(ERASED));
            status.setRollbackOnly();
        });

        assertThat(documents.findByOrgKey(ERASED)).hasSize(2);
        assertThat(drafts.count()).isEqualTo(4);
        assertThat(published.count()).isEqualTo(2);
        assertThat(translations.count()).isEqualTo(1);
        assertThat(signatures.count()).isEqualTo(2);
    }

    /** A tenant with nothing here — or erased twice — is not an error. */
    @Test
    void erasingATenantWithNoDocumentsIsHarmless() {
        erase("nobody");

        assertThat(documents.count()).isZero();
    }

    private void erase(String orgKey) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> events.publishEvent(new OrganizationDeletedEvent(orgKey)));
    }

    /** Two documents, each with an English and a German draft and a published English version. */
    private void givenDocumentsAndTranslationsFor(String orgKey) {
        for (String slug : new String[] {"getting-started", "q3-plan"}) {
            String id = UUID.nameUUIDFromBytes((orgKey + "/" + slug).getBytes()).toString();
            documents.saveAndFlush(new Document(orgKey, id, slug, slug, "en", "ada"));
            drafts.saveAndFlush(new DocumentDraft(orgKey, id, "en", DocumentContent.empty(), null));
            drafts.saveAndFlush(new DocumentDraft(orgKey, id, "de", DocumentContent.empty(), null));
            published.saveAndFlush(new PublishedDocument(orgKey, id, "en", DocumentContent.empty(), 1L,
                    Instant.now(), "ada"));
            signatures.saveAndFlush(new DocumentSignature(UUID.randomUUID().toString(), orgKey, id, slug, "en", 1L,
                    "hash", "employee", "e-1", "ada", Instant.now()));
        }
        translations.saveAndFlush(new DocumentUiTranslationBundle(orgKey, "document", "en",
                Map.of("title", "Documents")));
    }

    @AfterEach
    void cleanUp() {
        signatures.deleteAll();
        published.deleteAll();
        drafts.deleteAll();
        documents.deleteAll();
        translations.deleteAll();
    }
}
