package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.model.DocumentBlockInput;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.model.DocumentTranslationInput;
import com.processpuzzle.document.usecase.exception.DocumentSlugAlreadyExistsException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import com.processpuzzle.document.usecase.service.DocumentTranslationAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CreateDocumentTest {

    private static final String ORG = "demo";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;
    private PublishedDocumentRepository publishedRepository;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void mintsTheIdItselfAndIgnoresAnyTheClientEchoedBack() {
        UUID clientSupplied = UUID.fromString("22222222-2222-2222-2222-222222222222");

        DocumentDetails details = create(TestPolicies.permitAll()).execute(ORG, input().id(clientSupplied));

        assertThat(details.document().getId()).isNotEqualTo(clientSupplied.toString());
        assertThat(UUID.fromString(details.document().getId())).isNotNull();
    }

    @Test
    void createsTheSourceLocalesDraftInTheSameCallAndPublishesNothing() {
        create(TestPolicies.permitAll()).execute(ORG, input().translations(List.of(
                new DocumentTranslationInput().locale("en").blocks(List.of(textBlock("intro"))))));

        ArgumentCaptor<DocumentDraft> saved = ArgumentCaptor.forClass(DocumentDraft.class);
        verify(draftRepository).save(saved.capture());
        assertThat(saved.getValue().getLocale()).isEqualTo("en");
        assertThat(saved.getValue().getBlocks()).extracting(DocumentBlock::id).containsExactly("intro");
        // The source locale is based on nothing, which is what keeps it from ever reporting itself stale.
        assertThat(saved.getValue().getBasedOnRevision()).isNull();
        verify(publishedRepository, never()).save(any());
    }

    @Test
    void aCreatePayloadWithNoTranslationsStartsWithABlankSourceDraft() {
        create(TestPolicies.permitAll()).execute(ORG, input());

        ArgumentCaptor<DocumentDraft> saved = ArgumentCaptor.forClass(DocumentDraft.class);
        verify(draftRepository).save(saved.capture());
        assertThat(saved.getValue().getBlocks()).isEmpty();
    }

    @Test
    void anOmittedTranslationsArrayIsTheSameAsAnEmptyOne() {
        create(TestPolicies.permitAll()).execute(ORG, input().translations(null));

        ArgumentCaptor<DocumentDraft> saved = ArgumentCaptor.forClass(DocumentDraft.class);
        verify(draftRepository).save(saved.capture());
        assertThat(saved.getValue().getLocale()).isEqualTo("en");
        assertThat(saved.getValue().getBlocks()).isEmpty();
    }

    @Test
    void translationsForOtherLocalesAreIgnoredBecauseThereIsNoSourceRevisionToBaseThemOn() {
        create(TestPolicies.permitAll()).execute(ORG, input().translations(List.of(
                new DocumentTranslationInput().locale("de").blocks(List.of(textBlock("einleitung"))))));

        ArgumentCaptor<DocumentDraft> saved = ArgumentCaptor.forClass(DocumentDraft.class);
        verify(draftRepository).save(saved.capture());
        assertThat(saved.getAllValues()).hasSize(1);
        assertThat(saved.getValue().getLocale()).isEqualTo("en");
        assertThat(saved.getValue().getBlocks()).isEmpty();
    }

    @Test
    void aSourceTranslationThatOmitsBlocksEntirelyIsTreatedAsBlankRatherThanNull() {
        create(TestPolicies.permitAll()).execute(ORG, input().translations(List.of(
                new DocumentTranslationInput().locale("en"))));

        ArgumentCaptor<DocumentDraft> saved = ArgumentCaptor.forClass(DocumentDraft.class);
        verify(draftRepository).save(saved.capture());
        assertThat(saved.getValue().getBlocks()).isEmpty();
    }

    @Test
    void aTakenSlugIsAConflict() {
        when(repository.existsByOrgKeyAndSlug(ORG, "getting-started")).thenReturn(true);

        assertThatThrownBy(() -> create(TestPolicies.permitAll()).execute(ORG, input()))
                .isInstanceOf(DocumentSlugAlreadyExistsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void contentThatDoesNotValidateIsRejectedBeforeAnythingIsWritten() {
        DocumentBlockInput widget = new DocumentBlockInput()
                .kind(com.processpuzzle.document.model.BlockKind.WIDGET)
                .type("entity-grid")
                .inputBindings(Map.of("rows", "gone"));

        assertThatThrownBy(() -> create(TestPolicies.permitAll()).execute(ORG, input().translations(List.of(
                new DocumentTranslationInput().locale("en").blocks(List.of(widget))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid document");
        verify(repository, never()).save(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void aWarningOnItsOwnDoesNotBlockTheCreate() {
        // An orphaned REFERENCED widget is legitimate — it may be placed later.
        DocumentBlockInput orphan = new DocumentBlockInput()
                .kind(com.processpuzzle.document.model.BlockKind.WIDGET)
                .placement(com.processpuzzle.document.model.WidgetPlacement.REFERENCED)
                .id("grid-1")
                .type("entity-grid");

        DocumentDetails details = create(TestPolicies.permitAll()).execute(ORG, input().translations(List.of(
                new DocumentTranslationInput().locale("en").blocks(List.of(orphan)))));

        assertThat(details.document()).isNotNull();
        verify(draftRepository).save(any());
    }

    @Test
    void creatingRequiresMembershipOfTheOrganization() {
        assertThatThrownBy(() -> create(TestPolicies.outsider()).execute(ORG, input()))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void theCreatedByFieldComesFromThePrincipalRatherThanThePayload() {
        Document document = create(TestPolicies.principal("ada")).execute(ORG, input().author(null)).document();

        assertThat(document.getCreatedBy()).isEqualTo("ada");
        assertThat(document.getAuthor()).isEqualTo("ada");
    }

    // region fixtures
    private CreateDocument create(DocumentAccessPolicy policy) {
        return new CreateDocument(
                repository,
                draftRepository,
                new DocumentReferentialIntegrityChecker(),
                new DocumentTranslationAssembler(draftRepository, publishedRepository),
                TestGuards.with(policy),
                new DocumentMapper());
    }


    private static DocumentInput input() {
        return new DocumentInput().slug("getting-started").title("Getting started").sourceLocale("en");
    }

    private static DocumentBlockInput textBlock(String id) {
        return new DocumentBlockInput().id(id).kind(com.processpuzzle.document.model.BlockKind.TEXT);
    }
    // endregion
}
