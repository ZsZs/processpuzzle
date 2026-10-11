package com.processpuzzle.document.usecase;

import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.WidgetPlacement;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
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
    void mintsTheIdItself() {
        DocumentDetails details = create(TestPolicies.permitAll()).execute(ORG, input());
        assertThat(UUID.fromString(details.document().getId())).isNotNull();
    }

    @Test
    void createsTheSourceLocalesDraftInTheSameCallAndPublishesNothing() {
        create(TestPolicies.permitAll()).execute(ORG, input(List.of(
                new DocumentTranslationCommand("en", DocumentContent.of(List.of(textBlock("intro")))))));

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
        create(TestPolicies.permitAll()).execute(ORG, input(null));

        ArgumentCaptor<DocumentDraft> saved = ArgumentCaptor.forClass(DocumentDraft.class);
        verify(draftRepository).save(saved.capture());
        assertThat(saved.getValue().getLocale()).isEqualTo("en");
        assertThat(saved.getValue().getBlocks()).isEmpty();
    }

    @Test
    void translationsForOtherLocalesAreIgnoredBecauseThereIsNoSourceRevisionToBaseThemOn() {
        create(TestPolicies.permitAll()).execute(ORG, input(List.of(
                new DocumentTranslationCommand("de", DocumentContent.of(List.of(textBlock("einleitung")))))));

        ArgumentCaptor<DocumentDraft> saved = ArgumentCaptor.forClass(DocumentDraft.class);
        verify(draftRepository).save(saved.capture());
        assertThat(saved.getAllValues()).hasSize(1);
        assertThat(saved.getValue().getLocale()).isEqualTo("en");
        assertThat(saved.getValue().getBlocks()).isEmpty();
    }

    @Test
    void aSourceTranslationThatOmitsBlocksEntirelyIsTreatedAsBlankRatherThanNull() {
        create(TestPolicies.permitAll()).execute(ORG, input(List.of(
                new DocumentTranslationCommand("en", null))));

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
        DocumentBlock widget = new DocumentBlock(null, BlockKind.WIDGET, null, null, null,
                "entity-grid", null, Map.of("rows", "gone"), null);

        assertThatThrownBy(() -> create(TestPolicies.permitAll()).execute(ORG, input(List.of(
                new DocumentTranslationCommand("en", DocumentContent.of(List.of(widget)))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid document");
        verify(repository, never()).save(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void aWarningOnItsOwnDoesNotBlockTheCreate() {
        // An orphaned REFERENCED widget is legitimate — it may be placed later.
        DocumentBlock orphan = new DocumentBlock("grid-1", BlockKind.WIDGET, null, null,
                WidgetPlacement.REFERENCED, "entity-grid", null, null, null);

        DocumentDetails details = create(TestPolicies.permitAll()).execute(ORG, input(List.of(
                new DocumentTranslationCommand("en", DocumentContent.of(List.of(orphan))))));

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
        Document document = create(TestPolicies.principal("ada")).execute(ORG, input()).document();

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
                TestGuards.with(policy));
    }


    private static DocumentCommand input() {
        return input(List.of());
    }

    private static DocumentCommand input(List<DocumentTranslationCommand> translations) {
        return new DocumentCommand(new DocumentProperties("getting-started", "Getting started", null, null,
                null, "en", false, null, null), translations);
    }

    private static DocumentBlock textBlock(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }
    // endregion
}
