package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindPublishedContentTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";
    private static final String SLUG = "getting-started";

    private DocumentRepository repository;
    private PublishedDocumentRepository publishedRepository;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
    }

    @Test
    void theUseCaseCannotReachADraftAtAll() {
        // The guarantee is structural, so this test is structural too: no collaborator of this class
        // can return draft content. A future change that injects the draft repository here would turn
        // "a draft is never publicly readable" back into a convention, and this is what notices.
        List<Class<?>> dependencies = java.util.Arrays.stream(FindPublishedContent.class.getDeclaredFields())
                .map(Field::getType)
                .toList();

        assertThat(dependencies).doesNotContain(DocumentDraftRepository.class);
        assertThat(dependencies)
                .noneMatch(type -> type.getSimpleName().equals("DocumentTranslationAssembler"));
    }

    @Test
    void servesTheRequestedLocalesPublishedSnapshot() {
        stub(publicDocument(), List.of(snapshot("en", "intro-en"), snapshot("de", "intro-de")));

        FindPublishedContent.PublishedContentView view = useCase(anonymous()).execute(ORG, SLUG, "de");

        assertThat(view.served().locale()).isEqualTo("de");
        assertThat(view.served().content().blocks()).extracting(DocumentBlock::id).containsExactly("intro-de");
        assertThat(view.isFallback()).isFalse();
        assertThat(view.availableLocales()).containsExactly("de", "en");
    }

    @Test
    void fallsBackToTheSourceLocaleAndSaysSo() {
        // Better than a 404 for a reader whose language is not translated yet — but the UI has to be
        // able to tell them why they are looking at English, hence isFallback.
        stub(publicDocument(), List.of(snapshot("en", "intro-en")));

        FindPublishedContent.PublishedContentView view = useCase(anonymous()).execute(ORG, SLUG, "fr");

        assertThat(view.served().locale()).isEqualTo("en");
        assertThat(view.isFallback()).isTrue();
    }

    @Test
    void defaultsToTheSourceLocaleWhenNoneIsRequested() {
        stub(publicDocument(), List.of(snapshot("en", "intro-en"), snapshot("de", "intro-de")));

        FindPublishedContent.PublishedContentView view = useCase(anonymous()).execute(ORG, SLUG, null);

        assertThat(view.served().locale()).isEqualTo("en");
        assertThat(view.isFallback()).isFalse();
    }

    @Test
    void aDocumentWithNothingPublishedIsNotFound() {
        stub(publicDocument(), List.of());

        assertThatThrownBy(() -> useCase(anonymous()).execute(ORG, SLUG, "en"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void anUnknownSlugIsNotFound() {
        when(repository.findByOrgKeyAndSlug(ORG, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase(anonymous()).execute(ORG, "nope", null))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void aRestrictedDocumentIsNotFoundRatherThanForbidden() {
        // Answering 403 would confirm to an anonymous caller that a document with this slug exists,
        // which is exactly what a restricted document is meant to withhold.
        stub(restrictedDocument(), List.of(snapshot("en", "intro-en")));

        assertThatThrownBy(() -> useCase(anonymous()).execute(ORG, SLUG, "en"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void aRestrictedDocumentIsServedToSomeoneHoldingItsReaderRole() {
        stub(restrictedDocument(), List.of(snapshot("en", "intro-en")));

        FindPublishedContent.PublishedContentView view = useCase(holding("secret-reader")).execute(ORG, SLUG, "en");

        assertThat(view.served().content().blocks()).extracting(DocumentBlock::id).containsExactly("intro-en");
    }

    @Test
    void theAnonymousViewCarriesNoDraftRevisionOrStaleness() {
        stub(publicDocument(), List.of(snapshot("en", "intro-en")));

        FindPublishedContent.PublishedContentView view = useCase(anonymous()).execute(ORG, SLUG, "en");

        assertThat(view.served().basedOnRevision()).isNull();
        assertThat(view.served().outOfDate()).isFalse();
        assertThat(view.served().revision()).isEqualTo(view.served().publishedRevision());
    }

    // region fixtures
    private FindPublishedContent useCase(DocumentAccessPolicy policy) {
        return new FindPublishedContent(repository, publishedRepository, TestGuards.with(policy));
    }

    private void stub(Document document, List<PublishedDocument> snapshots) {
        when(repository.findByOrgKeyAndSlug(ORG, SLUG)).thenReturn(Optional.of(document));
        when(publishedRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(snapshots);
    }

    private static PublishedDocument snapshot(String locale, String blockId) {
        DocumentBlock block = new DocumentBlock(blockId, BlockKind.TEXT, true, null, null, null, null, null, null);
        return new PublishedDocument(ORG, ID, locale, DocumentContent.of(List.of(block)), 1L, Instant.now(), "ada");
    }

    private static Document publicDocument() {
        Document document = new Document(ORG, ID, SLUG, "Getting started", "en", "ada");
        document.replaceProperties(SLUG, "Getting started", null, null, "Ada", "en", true,
                DocumentRoles.unrestricted(), DocumentPorts.empty());
        return document;
    }

    private static Document restrictedDocument() {
        Document document = new Document(ORG, ID, SLUG, "Getting started", "en", "ada");
        document.replaceProperties(SLUG, "Getting started", null, null, "Ada", "en", false,
                new DocumentRoles(List.of("secret-reader"), List.of(), List.of()), DocumentPorts.empty());
        return document;
    }

    private static DocumentAccessPolicy anonymous() {
        return new DocumentAccessPolicy() {
            @Override
            public boolean isAuthenticated() {
                return false;
            }
        };
    }

    private static DocumentAccessPolicy holding(String... roles) {
        List<String> held = List.of(roles);
        return new DocumentAccessPolicy() {
            @Override
            public boolean hasAnyRole(Collection<String> requiredRoles) {
                return requiredRoles.stream().anyMatch(held::contains);
            }
        };
    }
    // endregion
}
