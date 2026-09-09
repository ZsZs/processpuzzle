package com.processpuzzle.document.usecase;

import com.processpuzzle.document.adapter.inbound.DocumentMapper;
import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.domain.DocumentDraftRepository;
import com.processpuzzle.document.domain.DocumentInputPort;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.document.usecase.service.DocumentReferentialIntegrityChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportDocumentsTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private DocumentDraftRepository draftRepository;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        draftRepository = mock(DocumentDraftRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(draftRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsADocumentPerEntryWithTheDraftsItCarries() throws IOException {
        ImportOutcome outcome = importDocuments(TestPolicies.permitAll()).execute(ORG, yaml("""
                documents:
                  - slug: getting-started
                    title: Getting started
                    subject: Onboarding
                    sourceLocale: en
                    translations:
                      - locale: en
                        blocks:
                          - id: intro
                            kind: TEXT
                      - locale: de
                        blocks:
                          - id: einleitung
                            kind: TEXT
                """));

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.errors()).isEmpty();

        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSlug()).isEqualTo("getting-started");
        assertThat(saved.getValue().getSubject()).isEqualTo("Onboarding");

        assertThat(savedDrafts()).extracting(DocumentDraft::getLocale).containsExactlyInAnyOrder("en", "de");
    }

    @Test
    void aTranslationOtherThanTheSourceRecordsWhichSourceRevisionItCameIn() {
        // Null for the source locale — it is based on nothing — and the source's revision otherwise.
        importSilently("""
                documents:
                  - slug: getting-started
                    title: Getting started
                    sourceLocale: en
                    translations:
                      - locale: en
                      - locale: de
                """);

        assertThat(savedDrafts()).filteredOn(draft -> draft.getLocale().equals("en"))
                .singleElement().satisfies(draft -> assertThat(draft.getBasedOnRevision()).isNull());
        assertThat(savedDrafts()).filteredOn(draft -> draft.getLocale().equals("de"))
                .singleElement().satisfies(draft -> assertThat(draft.getBasedOnRevision()).isEqualTo(1L));
    }

    @Test
    void aFileWithoutTheSourceLocaleStillGetsASourceDraft() {
        importSilently("""
                documents:
                  - slug: getting-started
                    title: Getting started
                    sourceLocale: en
                    translations:
                      - locale: de
                """);

        assertThat(savedDrafts()).extracting(DocumentDraft::getLocale).containsExactlyInAnyOrder("en", "de");
    }

    @Test
    void anEntryWithNoTranslationsAtAllGetsAnEmptySourceDraft() {
        importSilently("""
                documents:
                  - slug: getting-started
                    title: Getting started
                    sourceLocale: en
                """);

        assertThat(savedDrafts()).singleElement().satisfies(draft -> {
            assertThat(draft.getLocale()).isEqualTo("en");
            assertThat(draft.getBlocks()).isEmpty();
        });
    }

    @Test
    void anEmptyFileImportsNothingRatherThanFailing() throws IOException {
        ImportOutcome outcome = importDocuments(TestPolicies.permitAll()).execute(ORG, yaml("documents:\n"));

        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.errors()).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void anExistingSlugIsUpdatedRatherThanDuplicated() throws IOException {
        // Entries match on slug and any id in the file is ignored: ids are organization-local, the
        // slug is what "the same document" means across organizations.
        Document existing = new Document(ORG, ID, "getting-started", "Original", "en", "ada");
        DocumentDraft english = new DocumentDraft(ORG, ID, "en",
                DocumentContent.of(List.of(text("old"))), null);
        when(repository.findByOrgKeyAndSlug(ORG, "getting-started")).thenReturn(Optional.of(existing));
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(english));

        ImportOutcome outcome = importDocuments(TestPolicies.permitAll()).execute(ORG, yaml("""
                documents:
                  - id: 99999999-9999-9999-9999-999999999999
                    slug: getting-started
                    title: Renamed
                    sourceLocale: en
                    translations:
                      - locale: en
                        blocks:
                          - id: fresh
                            kind: TEXT
                """));

        assertThat(outcome.updated()).isEqualTo(1);
        assertThat(outcome.created()).isZero();
        assertThat(existing.getId()).isEqualTo(ID);
        assertThat(existing.getTitle()).isEqualTo("Renamed");
        assertThat(english.getBlocks()).extracting(DocumentBlock::id).containsExactly("fresh");
        assertThat(english.getRevision()).isEqualTo(2L);
    }

    @Test
    void aLocaleTheExistingDocumentDoesNotHaveYetIsCreatedAgainstTheSourcesCurrentRevision() {
        Document existing = new Document(ORG, ID, "getting-started", "Original", "en", "ada");
        DocumentDraft english = new DocumentDraft(ORG, ID, "en", DocumentContent.empty(), null);
        english.replaceBlocks(List.of(text("intro")));
        when(repository.findByOrgKeyAndSlug(ORG, "getting-started")).thenReturn(Optional.of(existing));
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of(english));

        importSilently("""
                documents:
                  - slug: getting-started
                    title: Getting started
                    sourceLocale: en
                    translations:
                      - locale: de
                """);

        assertThat(savedDrafts()).filteredOn(draft -> draft.getLocale().equals("de"))
                .singleElement().satisfies(draft -> assertThat(draft.getBasedOnRevision()).isEqualTo(2L));
    }

    @Test
    void anExistingDocumentWhoseSourceLocaleHasNoDraftLeavesTheNewTranslationUnbased() {
        Document existing = new Document(ORG, ID, "getting-started", "Original", "en", "ada");
        when(repository.findByOrgKeyAndSlug(ORG, "getting-started")).thenReturn(Optional.of(existing));
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of());

        importSilently("""
                documents:
                  - slug: getting-started
                    title: Getting started
                    sourceLocale: en
                    translations:
                      - locale: de
                """);

        assertThat(savedDrafts()).singleElement()
                .satisfies(draft -> assertThat(draft.getBasedOnRevision()).isNull());
    }

    @Test
    void anEntryWithoutASlugOrASourceLocaleIsReportedAndTheWholeFileIsRejected() throws IOException {
        // All-or-nothing: the whole file is validated before anything is persisted.
        ImportOutcome outcome = importDocuments(TestPolicies.permitAll()).execute(ORG, yaml("""
                documents:
                  - title: No slug
                    sourceLocale: en
                  - slug: no-source-locale
                    title: No source locale
                  - slug: fine
                    title: Fine
                    sourceLocale: en
                """));

        assertThat(outcome.created()).isZero();
        assertThat(outcome.updated()).isZero();
        assertThat(outcome.errors()).containsExactly(
                "Entry 0 is missing 'slug' and was skipped.",
                "Entry 'no-source-locale' is missing 'sourceLocale'.");
        verify(repository, never()).save(any());
        verify(draftRepository, never()).save(any());
    }

    @Test
    void aBlankSourceLocaleCountsAsMissingToo() throws IOException {
        ImportOutcome outcome = importDocuments(TestPolicies.permitAll()).execute(ORG, yaml("""
                documents:
                  - slug: blank-source-locale
                    title: Blank source locale
                    sourceLocale: '  '
                """));

        assertThat(outcome.errors())
                .containsExactly("Entry 'blank-source-locale' is missing 'sourceLocale'.");
    }

    @Test
    void anEntryWhoseTranslationsKeyCarriesNothingIsTreatedAsHavingNone() {
        importSilently("""
                documents:
                  - slug: getting-started
                    title: Getting started
                    sourceLocale: en
                    translations:
                """);

        assertThat(savedDrafts()).singleElement().satisfies(draft -> {
            assertThat(draft.getLocale()).isEqualTo("en");
            assertThat(draft.getBlocks()).isEmpty();
        });
    }

    @Test
    void reimportingTheSourceLocaleOfADocumentThatHasNoSourceDraftYetCreatesOneUnbased() {
        Document existing = new Document(ORG, ID, "getting-started", "Original", "en", "ada");
        when(repository.findByOrgKeyAndSlug(ORG, "getting-started")).thenReturn(Optional.of(existing));
        when(draftRepository.findByOrgKeyAndDocumentId(ORG, ID)).thenReturn(List.of());

        importSilently("""
                documents:
                  - slug: getting-started
                    title: Getting started
                    sourceLocale: en
                    translations:
                      - locale: en
                        blocks:
                          - id: intro
                            kind: TEXT
                """);

        assertThat(savedDrafts()).singleElement().satisfies(draft -> {
            assertThat(draft.getLocale()).isEqualTo("en");
            assertThat(draft.getBasedOnRevision()).isNull();
            assertThat(draft.getBlocks()).extracting(DocumentBlock::id).containsExactly("intro");
        });
    }

    @Test
    void aBlankSlugCountsAsMissing() throws IOException {
        ImportOutcome outcome = importDocuments(TestPolicies.permitAll()).execute(ORG, yaml("""
                documents:
                  - slug: '  '
                    title: Blank slug
                    sourceLocale: en
                """));

        assertThat(outcome.errors()).containsExactly("Entry 0 is missing 'slug' and was skipped.");
    }

    @Test
    void contentThatDoesNotValidateIsReportedPerLocaleAndPersistsNothing() throws IOException {
        ImportOutcome outcome = importDocuments(TestPolicies.permitAll()).execute(ORG, yaml("""
                documents:
                  - slug: getting-started
                    title: Getting started
                    sourceLocale: en
                    translations:
                      - locale: en
                        blocks:
                          - id: grid-1
                            kind: WIDGET
                            type: entity-grid
                            inputBindings:
                              rows: gone
                """));

        assertThat(outcome.errors()).singleElement().satisfies(error -> {
            assertThat(error).contains("Entry 'getting-started' locale 'en'");
            assertThat(error).contains("unknown-port");
        });
        verify(repository, never()).save(any());
    }

    @Test
    void aWidgetBoundToADeclaredPortImportsCleanly() throws IOException {
        ImportOutcome outcome = importDocuments(TestPolicies.permitAll()).execute(ORG, yaml("""
                documents:
                  - slug: getting-started
                    title: Getting started
                    sourceLocale: en
                    inputPorts:
                      - name: customer
                        type: ENTITY_REF
                        required: true
                        entityType: Customer
                    translations:
                      - locale: en
                        blocks:
                          - id: grid-1
                            kind: WIDGET
                            type: entity-grid
                            inputBindings:
                              rows: customer
                """));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isEqualTo(1);
        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPorts().inputPorts())
                .extracting(DocumentInputPort::name).containsExactly("customer");
    }

    @Test
    void importingRequiresMembershipOfTheOrganization() {
        assertThatThrownBy(() -> importDocuments(TestPolicies.outsider()).execute(ORG, yaml("documents:\n")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void malformedYamlFailsRatherThanImportingHalfAFile() {
        assertThatThrownBy(() -> importDocuments(TestPolicies.permitAll()).execute(ORG, yaml("documents: [ {")))
                .isInstanceOf(IOException.class);
    }

    // region fixtures
    private ImportDocuments importDocuments(DocumentAccessPolicy policy) {
        return new ImportDocuments(repository, draftRepository, new DocumentReferentialIntegrityChecker(),
                TestGuards.with(policy), new DocumentMapper());
    }

    private void importSilently(String yaml) {
        try {
            assertThat(importDocuments(TestPolicies.permitAll()).execute(ORG, yaml(yaml)).errors()).isEmpty();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<DocumentDraft> savedDrafts() {
        ArgumentCaptor<DocumentDraft> captor = ArgumentCaptor.forClass(DocumentDraft.class);
        verify(draftRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private static InputStream yaml(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }
    // endregion
}
