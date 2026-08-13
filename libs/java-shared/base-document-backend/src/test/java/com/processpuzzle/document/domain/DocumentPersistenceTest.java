package com.processpuzzle.document.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the mapping decisions the three entities rest on: the tenant-scoped composite keys, the
 * JSON columns, the slug uniqueness constraint, and — the one most worth pinning — that a draft's
 * {@code revision} and its {@code lockVersion} move independently.
 */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.document.domain")
@EnableJpaRepositories("com.processpuzzle.document.domain")
class DocumentPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private DocumentRepository repository;

    @Autowired
    private DocumentDraftRepository draftRepository;

    @Autowired
    private PublishedDocumentRepository publishedRepository;

    @BeforeEach
    void seed() {
        publishedRepository.deleteAll();
        draftRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void portsAndRolesColumnsRoundTrip() {
        Document document = document("demo", "getting-started", "Getting started");
        document.replaceProperties("getting-started", "Getting started", "Onboarding", "How to begin",
                "Ada", "en", true,
                new DocumentRoles(List.of("reader"), List.of("editor"), List.of()),
                new DocumentPorts(List.of(claimsFilterPort()), List.of()));
        repository.saveAndFlush(document);

        Document reloaded = repository.findByOrgKeyAndSlug("demo", "getting-started").orElseThrow();
        assertThat(reloaded.getSubject()).isEqualTo("Onboarding");
        assertThat(reloaded.getAuthor()).isEqualTo("Ada");
        assertThat(reloaded.isPublic()).isTrue();
        assertThat(reloaded.getRoles().readerRoles()).containsExactly("reader");
        // Empty publisher list falls back to the editors rather than to "any member".
        assertThat(reloaded.getRoles().effectivePublisherRoles()).containsExactly("editor");
        assertThat(reloaded.getPorts().inputPorts()).extracting(DocumentInputPort::name)
                .containsExactly("claimsFilter");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getPublishedAt()).isNull();
    }

    @Test
    void contentColumnRoundTripsBlocks() {
        repository.saveAndFlush(document("demo", "q3-plan", "Q3 Plan"));
        DocumentBlock text = new DocumentBlock("intro", BlockKind.TEXT, true, null, null, null, null, null, null);
        DocumentBlock chart = new DocumentBlock("chart-1", BlockKind.WIDGET, null, null,
                WidgetPlacement.REFERENCED, "entity-grid",
                Map.of("entityType", "Claim"), Map.of("rsqlFilter", "claimsFilter"), Map.of());

        draftRepository.saveAndFlush(new DocumentDraft("demo", documentId("demo", "q3-plan"), "en",
                DocumentContent.of(List.of(text, chart)), null));

        DocumentDraft reloaded = draftRepository
                .findByOrgKeyAndDocumentIdAndLocale("demo", documentId("demo", "q3-plan"), "en").orElseThrow();
        assertThat(reloaded.getBlocks()).hasSize(2);
        assertThat(reloaded.getContent().findBlock("chart-1")).isPresent()
                .get().extracting(DocumentBlock::placement).isEqualTo(WidgetPlacement.REFERENCED);
        assertThat(reloaded.getContent().findBlock("chart-1").orElseThrow().inputBindings())
                .containsEntry("rsqlFilter", "claimsFilter");
        assertThat(reloaded.getContent().widgetBlockIds()).containsExactly("chart-1");
        assertThat(reloaded.getRevision()).isEqualTo(1L);
    }

    @Test
    void aDraftsRevisionAndItsLockVersionMoveIndependently() {
        // The distinction the whole publishing model rests on: revision counts content edits and is
        // compared against publishedRevision, while lockVersion is Hibernate's and only guards
        // concurrent writes. If revision were the @Version, publishing would bump the very counter
        // it had just matched.
        repository.saveAndFlush(document("demo", "plan", "Plan"));
        String id = documentId("demo", "plan");
        DocumentDraft draft = draftRepository.saveAndFlush(
                new DocumentDraft("demo", id, "en", DocumentContent.empty(), null));
        assertThat(draft.getRevision()).isEqualTo(1L);
        assertThat(draft.getLockVersion()).isZero();

        draft.replaceBlocks(List.of(new DocumentBlock("intro", BlockKind.TEXT, true, null, null, null, null, null, null)));
        draftRepository.saveAndFlush(draft);

        assertThat(draft.getRevision()).isEqualTo(2L);
        assertThat(draft.getLockVersion()).isEqualTo(1L);
    }

    @Test
    void publishingTheDraftLeavesTheRevisionAloneSoStatusReadsPublished() {
        repository.saveAndFlush(document("demo", "plan", "Plan"));
        String id = documentId("demo", "plan");
        DocumentDraft draft = draftRepository.saveAndFlush(
                new DocumentDraft("demo", id, "en", DocumentContent.empty(), null));
        draft.replaceBlocks(List.of(new DocumentBlock("intro", BlockKind.TEXT, true, null, null, null, null, null, null)));
        draftRepository.saveAndFlush(draft);

        publishedRepository.saveAndFlush(new PublishedDocument("demo", id, "en", draft.getContent(),
                draft.getRevision(), Instant.now(), "ada"));

        PublishedDocument snapshot = publishedRepository
                .findByOrgKeyAndDocumentIdAndLocale("demo", id, "en").orElseThrow();
        assertThat(DocumentStatus.derive(draft.getRevision(), snapshot.getPublishedRevision()))
                .isEqualTo(DocumentStatus.PUBLISHED);

        draft.replaceBlocks(List.of());
        draftRepository.saveAndFlush(draft);
        assertThat(DocumentStatus.derive(draft.getRevision(), snapshot.getPublishedRevision()))
                .isEqualTo(DocumentStatus.PUBLISHED_WITH_DRAFT_CHANGES);
    }

    @Test
    void draftAndPublishedContentAreSeparateRows() {
        // Not a redundant assertion: the whole "a draft is never publicly readable" guarantee rests
        // on published content being addressable on its own, so a store that can only authorize
        // whole records can expose one without the other.
        repository.saveAndFlush(document("demo", "plan", "Plan"));
        String id = documentId("demo", "plan");
        DocumentBlock published = new DocumentBlock("live", BlockKind.TEXT, true, null, null, null, null, null, null);
        DocumentBlock edited = new DocumentBlock("wip", BlockKind.TEXT, true, null, null, null, null, null, null);

        publishedRepository.saveAndFlush(new PublishedDocument("demo", id, "en",
                DocumentContent.of(List.of(published)), 1L, Instant.now(), "ada"));
        draftRepository.saveAndFlush(new DocumentDraft("demo", id, "en",
                DocumentContent.of(List.of(edited)), null));

        assertThat(publishedRepository.findByOrgKeyAndDocumentIdAndLocale("demo", id, "en").orElseThrow()
                .getBlocks()).extracting(DocumentBlock::id).containsExactly("live");
        assertThat(draftRepository.findByOrgKeyAndDocumentIdAndLocale("demo", id, "en").orElseThrow()
                .getBlocks()).extracting(DocumentBlock::id).containsExactly("wip");
    }

    @Test
    void aTranslationIsOutOfDateOnceTheSourceMovesOn() {
        repository.saveAndFlush(document("demo", "plan", "Plan"));
        String id = documentId("demo", "plan");
        DocumentDraft german = draftRepository.saveAndFlush(
                new DocumentDraft("demo", id, "de", DocumentContent.empty(), 3L));

        assertThat(german.isOutOfDate(3L)).isFalse();
        assertThat(german.isOutOfDate(4L)).isTrue();
        // The source locale itself records no base and must never report itself stale.
        assertThat(new DocumentDraft("demo", id, "en", DocumentContent.empty(), null).isOutOfDate(99L)).isFalse();
    }

    @Test
    void theSameSlugCoexistsInDifferentOrganizationsButNotWithinOne() {
        repository.saveAndFlush(document("org-a", "plan", "Plan A"));
        repository.saveAndFlush(document("org-b", "plan", "Plan B"));

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findByOrgKeyAndSlug("org-a", "plan").orElseThrow().getTitle()).isEqualTo("Plan A");
        assertThat(repository.existsByOrgKeyAndSlug("org-a", "plan")).isTrue();
        assertThat(repository.existsByOrgKeyAndSlug("org-c", "plan")).isFalse();

        assertThatThrownBy(() -> repository.saveAndFlush(document("org-a", "plan", "Duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void metadataLockVersionIsHibernateManaged() {
        Document saved = repository.saveAndFlush(document("demo", "plan", "Plan"));
        assertThat(saved.getLockVersion()).isZero();

        saved.replaceProperties("plan", "Plan v2", null, null, null, "en", false, null, null);
        repository.saveAndFlush(saved);

        assertThat(saved.getLockVersion()).isEqualTo(1L);
    }

    @Test
    void markFirstPublicationRecordsOnlyTheFirst() {
        Document document = repository.saveAndFlush(document("demo", "plan", "Plan"));
        Instant first = Instant.parse("2026-01-01T00:00:00Z");

        document.markFirstPublication(first);
        document.markFirstPublication(Instant.parse("2026-06-01T00:00:00Z"));

        assertThat(document.getPublishedAt()).isEqualTo(first);
    }

    @Test
    void deleteByOrgKeyRemovesOnlyThatOrganizationsDocuments() {
        repository.saveAndFlush(document("org-a", "plan-1", "P1"));
        repository.saveAndFlush(document("org-a", "plan-2", "P2"));
        repository.saveAndFlush(document("org-b", "plan-1", "P1"));

        repository.deleteByOrgKey("org-a");
        repository.flush();

        assertThat(repository.findByOrgKey("org-a")).isEmpty();
        assertThat(repository.findByOrgKey("org-b")).hasSize(1);
    }

    // region fixtures
    private static Document document(String orgKey, String slug, String title) {
        // A deterministic id derived from the key pair: these tests assert on persistence, and a
        // random UUID would make the fixtures unaddressable from the assertions.
        return new Document(orgKey, documentId(orgKey, slug), slug, title, "en", "ada");
    }

    private static String documentId(String orgKey, String slug) {
        return java.util.UUID.nameUUIDFromBytes((orgKey + "/" + slug).getBytes()).toString();
    }

    private static DocumentInputPort claimsFilterPort() {
        return new DocumentInputPort("claimsFilter", PortType.ENTITY_COLLECTION, false,
                "Filter applied to the claims grid", null, "Claim", AttributeVisibility.all(), null);
    }
    // endregion
}
