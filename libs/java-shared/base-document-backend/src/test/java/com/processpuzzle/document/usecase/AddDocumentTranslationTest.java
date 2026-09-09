package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentPorts;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentRoles;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.model.DocumentBlockInput;
import com.processpuzzle.document.model.DocumentTranslationInput;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentTranslationAlreadyExistsException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddDocumentTranslationTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;
    private PublishedDocumentRepository publishedRepository;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void omittingBlocksCopiesTheSourceLocalesCurrentDraft() {
        // What a translator wants far more often than a blank page — and it makes the copied block ids
        // line up with the source's, which is what the widget-coverage check compares later.
        stubFound(DocumentRoles.unrestricted(), sourceDraft());

        DocumentTranslationView view = add(TestPolicies.permitAll()).execute(ORG, ID, new DocumentTranslationInput().locale("de"));

        assertThat(view.locale()).isEqualTo("de");
        assertThat(view.content().blocks()).extracting(DocumentBlock::id).containsExactly("intro");
    }

    @Test
    void anExplicitEmptyArrayStillMeansStartBlank() {
        stubFound(DocumentRoles.unrestricted(), sourceDraft());

        DocumentTranslationView view = add(TestPolicies.permitAll()).execute(ORG, ID,
                new DocumentTranslationInput().locale("de").blocks(List.of()));

        assertThat(view.content().blocks()).isEmpty();
    }

    @Test
    void blocksTheRequestCarriesAreUsedAsGiven() {
        stubFound(DocumentRoles.unrestricted(), sourceDraft());

        DocumentTranslationView view = add(TestPolicies.permitAll()).execute(ORG, ID,
                new DocumentTranslationInput().locale("de").blocks(List.of(
                        new DocumentBlockInput().id("einleitung")
                                .kind(com.processpuzzle.document.model.BlockKind.TEXT))));

        assertThat(view.content().blocks()).extracting(DocumentBlock::id).containsExactly("einleitung");
    }

    @Test
    void theSourceRevisionItWasMadeFromIsRecordedEitherWay() {
        // Even a from-scratch translation was written against some state of the original; recording
        // which one is what lets it be reported stale later.
        DocumentDraft source = sourceDraft();
        source.replaceBlocks(List.of(text("intro"), text("outro")));
        stubFound(DocumentRoles.unrestricted(), source);

        DocumentTranslationView view = add(TestPolicies.permitAll()).execute(ORG, ID,
                new DocumentTranslationInput().locale("de").blocks(List.of()));

        assertThat(source.getRevision()).isEqualTo(2L);
        assertThat(view.basedOnRevision()).isEqualTo(2L);
        assertThat(view.outOfDate()).isFalse();
    }

    @Test
    void aDocumentWhoseSourceLocaleHasNoDraftYetGetsABlankTranslationBasedOnNothing() {
        stubFound(DocumentRoles.unrestricted(), null);

        DocumentTranslationView view = add(TestPolicies.permitAll()).execute(ORG, ID, new DocumentTranslationInput().locale("de"));

        assertThat(view.content().blocks()).isEmpty();
        assertThat(view.basedOnRevision()).isNull();
    }

    @Test
    void aLocaleThatAlreadyExistsIsAConflict() {
        stubFound(DocumentRoles.unrestricted(), sourceDraft());
        when(draftRepository.existsByOrgKeyAndDocumentIdAndLocale(ORG, ID, "de")).thenReturn(true);

        assertThatThrownBy(() -> add(TestPolicies.permitAll()).execute(ORG, ID, new DocumentTranslationInput().locale("de")))
                .isInstanceOf(DocumentTranslationAlreadyExistsException.class);
        verify(draftRepository, never()).save(any());
    }

    @Test
    void addingATranslationRequiresAnEditorRole() {
        stubFound(new DocumentRoles(List.of(), List.of("editor"), List.of()), sourceDraft());

        assertThatThrownBy(() -> add(TestPolicies.holding("reader")).execute(ORG, ID, new DocumentTranslationInput().locale("de")))
                .isInstanceOf(DocumentAccessDeniedException.class);
    }

    @Test
    void unknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> add(TestPolicies.permitAll()).execute(ORG, "missing", new DocumentTranslationInput().locale("de")))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    // region fixtures
    private AddDocumentTranslation add(DocumentAccessPolicy policy) {
        return new AddDocumentTranslation(repository, draftRepository,
                new DocumentTranslationAssembler(draftRepository, publishedRepository),
                TestGuards.with(policy), new DocumentMapper());
    }

    private void stubFound(DocumentRoles roles, DocumentDraft source) {
        Document document = new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
        document.replaceProperties("getting-started", "Getting started", null, null, "ada", "en", false, roles,
                DocumentPorts.empty());
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        if (source != null) {
            when(draftRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en")).thenReturn(Optional.of(source));
        }
    }

    private static DocumentDraft sourceDraft() {
        return new DocumentDraft(ORG, ID, "en", DocumentContent.of(List.of(text("intro"))), null);
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }
    // endregion
}
