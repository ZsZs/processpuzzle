package com.processpuzzle.document.adapter.inbound;

import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentDraft;
import com.processpuzzle.document.model.DocumentBlockInput;
import com.processpuzzle.document.model.DocumentInput;
import com.processpuzzle.document.model.DocumentPropertiesInput;
import com.processpuzzle.document.model.DocumentTranslationInput;
import com.processpuzzle.document.model.ReorderBlocksRequest;
import com.processpuzzle.document.usecase.AddDocumentTranslation;
import com.processpuzzle.document.usecase.AppendDocumentBlock;
import com.processpuzzle.document.usecase.CreateDocument;
import com.processpuzzle.document.usecase.DeleteDocument;
import com.processpuzzle.document.usecase.DeleteDocumentBlock;
import com.processpuzzle.document.usecase.DiscardDocumentDraft;
import com.processpuzzle.document.usecase.DocumentDetails;
import com.processpuzzle.document.usecase.DocumentTranslationView;
import com.processpuzzle.document.usecase.DocumentValidationProblem;
import com.processpuzzle.document.usecase.ExportDocument;
import com.processpuzzle.document.usecase.FindAllDocuments;
import com.processpuzzle.document.usecase.FindDocument;
import com.processpuzzle.document.usecase.FindDocumentTranslations;
import com.processpuzzle.document.usecase.FindPublishedContent;
import com.processpuzzle.document.usecase.ImportDocuments;
import com.processpuzzle.document.usecase.ImportOutcome;
import com.processpuzzle.document.usecase.PublishDocumentTranslation;
import com.processpuzzle.document.usecase.RemoveDocumentTranslation;
import com.processpuzzle.document.usecase.ReorderDocumentBlocks;
import com.processpuzzle.document.usecase.ReplaceDocumentBlock;
import com.processpuzzle.document.usecase.UnpublishDocumentTranslation;
import com.processpuzzle.document.usecase.UpdateDocument;
import com.processpuzzle.document.usecase.UpdateDocumentProperties;
import com.processpuzzle.document.usecase.ValidateDocument;
import com.processpuzzle.rule.domain.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The endpoint is a delegator, so what these assert is the delegation itself: that the path's
 * {@code orgKey}/{@code documentId} reach the right use case, that the UUID-to-string bridge happens
 * on the way in, and that each verb answers with the status the contract promises.
 */
class DocumentEndpointTest {

    private static final String ORG = "demo";
    private static final UUID DOCUMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ID = DOCUMENT_ID.toString();

    private CreateDocument createDocument;
    private UpdateDocument updateDocument;
    private UpdateDocumentProperties updateDocumentProperties;
    private DeleteDocument deleteDocument;
    private FindDocument findDocument;
    private FindAllDocuments findAllDocuments;
    private FindDocumentTranslations findDocumentTranslations;
    private AddDocumentTranslation addDocumentTranslation;
    private RemoveDocumentTranslation removeDocumentTranslation;
    private PublishDocumentTranslation publishDocumentTranslation;
    private UnpublishDocumentTranslation unpublishDocumentTranslation;
    private DiscardDocumentDraft discardDocumentDraft;
    private FindPublishedContent findPublishedContent;
    private AppendDocumentBlock appendDocumentBlock;
    private ReplaceDocumentBlock replaceDocumentBlock;
    private DeleteDocumentBlock deleteDocumentBlock;
    private ReorderDocumentBlocks reorderDocumentBlocks;
    private ValidateDocument validateDocument;
    private ImportDocuments importDocuments;
    private ExportDocument exportDocument;
    private DocumentEndpoint endpoint;

    @BeforeEach
    void setUp() {
        createDocument = mock(CreateDocument.class);
        updateDocument = mock(UpdateDocument.class);
        updateDocumentProperties = mock(UpdateDocumentProperties.class);
        deleteDocument = mock(DeleteDocument.class);
        findDocument = mock(FindDocument.class);
        findAllDocuments = mock(FindAllDocuments.class);
        findDocumentTranslations = mock(FindDocumentTranslations.class);
        addDocumentTranslation = mock(AddDocumentTranslation.class);
        removeDocumentTranslation = mock(RemoveDocumentTranslation.class);
        publishDocumentTranslation = mock(PublishDocumentTranslation.class);
        unpublishDocumentTranslation = mock(UnpublishDocumentTranslation.class);
        discardDocumentDraft = mock(DiscardDocumentDraft.class);
        findPublishedContent = mock(FindPublishedContent.class);
        appendDocumentBlock = mock(AppendDocumentBlock.class);
        replaceDocumentBlock = mock(ReplaceDocumentBlock.class);
        deleteDocumentBlock = mock(DeleteDocumentBlock.class);
        reorderDocumentBlocks = mock(ReorderDocumentBlocks.class);
        validateDocument = mock(ValidateDocument.class);
        importDocuments = mock(ImportDocuments.class);
        exportDocument = mock(ExportDocument.class);
        endpoint = new DocumentEndpoint(createDocument, updateDocument, updateDocumentProperties, deleteDocument,
                findDocument, findAllDocuments, findDocumentTranslations, addDocumentTranslation,
                removeDocumentTranslation, publishDocumentTranslation, unpublishDocumentTranslation,
                discardDocumentDraft, findPublishedContent, appendDocumentBlock, replaceDocumentBlock,
                deleteDocumentBlock, reorderDocumentBlocks, validateDocument, importDocuments, exportDocument,
                new DocumentMapper());
    }

    // ── Documents ───────────────────────────────────────────────

    @Test
    void listPassesTheQueryParametersThroughAndPagesTheSummaries() {
        when(findAllDocuments.execute(ORG, "title==Getting*", "title,asc", 1, 5)).thenReturn(
                new FindAllDocuments.Result(new PageImpl<>(List.of(document()), PageRequest.of(1, 5), 11),
                        Map.of(ID, List.of(view()))));

        var response = endpoint.listDocuments(ORG, "title==Getting*", "title,asc", 1, 5);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getTotalElements()).isEqualTo(11);
        assertThat(response.getBody().getContent()).singleElement()
                .satisfies(summary -> assertThat(summary.getTranslations()).hasSize(1));
    }

    @Test
    void createAnswers201() {
        DocumentInput input = new DocumentInput().slug("getting-started").title("Getting started").sourceLocale("en");
        when(createDocument.execute(ORG, input)).thenReturn(details());

        ResponseEntity<com.processpuzzle.document.model.Document> response = endpoint.createDocument(ORG, input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isEqualTo(DOCUMENT_ID);
    }

    @Test
    void getResolvesTheDraftFlagAndPassesTheIdAsAString() {
        when(findDocument.execute(ORG, ID, "de", true)).thenReturn(details());

        assertThat(endpoint.getDocument(ORG, DOCUMENT_ID, "de", true).getBody().getId()).isEqualTo(DOCUMENT_ID);
    }

    @Test
    void anAbsentDraftFlagMeansPublishedContent() {
        when(findDocument.execute(ORG, ID, null, false)).thenReturn(details());

        assertThat(endpoint.getDocument(ORG, DOCUMENT_ID, null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(findDocument).execute(ORG, ID, null, false);
    }

    @Test
    void updateAndUpdatePropertiesEachReachTheirOwnUseCase() {
        DocumentInput input = new DocumentInput().slug("getting-started").title("Renamed").sourceLocale("en");
        DocumentPropertiesInput properties = new DocumentPropertiesInput().slug("getting-started").title("Renamed")
                .sourceLocale("en");
        when(updateDocument.execute(ORG, ID, input)).thenReturn(details());
        when(updateDocumentProperties.execute(ORG, ID, properties)).thenReturn(details());

        assertThat(endpoint.updateDocument(ORG, DOCUMENT_ID, input).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(endpoint.updateDocumentProperties(ORG, DOCUMENT_ID, properties).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void deleteAnswers204() {
        assertThat(endpoint.deleteDocument(ORG, DOCUMENT_ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteDocument).execute(ORG, ID);
    }

    // ── Translations ────────────────────────────────────────────

    @Test
    void theLocaleSelectorReturnsStateSummaries() {
        when(findDocumentTranslations.executeAll(ORG, ID)).thenReturn(List.of(view()));

        assertThat(endpoint.listDocumentTranslations(ORG, DOCUMENT_ID).getBody())
                .singleElement().satisfies(summary -> assertThat(summary.getLocale()).isEqualTo("en"));
    }

    @Test
    void addingATranslationAnswers201() {
        DocumentTranslationInput input = new DocumentTranslationInput().locale("de");
        when(addDocumentTranslation.execute(ORG, ID, input)).thenReturn(view());

        assertThat(endpoint.addDocumentTranslation(ORG, DOCUMENT_ID, input).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void oneTranslationCanBeReadAsADraftOrAsPublished() {
        when(findDocumentTranslations.executeOne(ORG, ID, "en", true)).thenReturn(view());
        when(findDocumentTranslations.executeOne(ORG, ID, "en", false)).thenReturn(view());

        assertThat(endpoint.getDocumentTranslation(ORG, DOCUMENT_ID, "en", true).getBody().getLocale())
                .isEqualTo("en");
        assertThat(endpoint.getDocumentTranslation(ORG, DOCUMENT_ID, "en", null).getBody().getLocale())
                .isEqualTo("en");
    }

    @Test
    void removingATranslationAnswers204() {
        assertThat(endpoint.removeDocumentTranslation(ORG, DOCUMENT_ID, "de").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        verify(removeDocumentTranslation).execute(ORG, ID, "de");
    }

    // ── Publishing ──────────────────────────────────────────────

    @Test
    void publishUnpublishAndDiscardAllAnswerWithTheResultingTranslation() {
        when(publishDocumentTranslation.execute(ORG, ID, "en")).thenReturn(view());
        when(unpublishDocumentTranslation.execute(ORG, ID, "en")).thenReturn(view());
        when(discardDocumentDraft.execute(ORG, ID, "en")).thenReturn(view());

        assertThat(endpoint.publishDocumentTranslation(ORG, DOCUMENT_ID, "en").getBody().getLocale()).isEqualTo("en");
        assertThat(endpoint.unpublishDocumentTranslation(ORG, DOCUMENT_ID, "en").getBody().getLocale()).isEqualTo("en");
        assertThat(endpoint.discardDocumentDraft(ORG, DOCUMENT_ID, "en").getBody().getLocale()).isEqualTo("en");
    }

    // ── Blocks ──────────────────────────────────────────────────

    @Test
    void appendingABlockAnswers201WithTheBlockTheUseCaseMinted() {
        DocumentBlockInput input = new DocumentBlockInput().kind(com.processpuzzle.document.model.BlockKind.TEXT);
        when(appendDocumentBlock.execute(ORG, ID, "en", input)).thenReturn(text("intro"));

        var response = endpoint.appendDocumentBlock(ORG, DOCUMENT_ID, "en", input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isEqualTo("intro");
    }

    @Test
    void reorderingUnwrapsTheRequestBodyAndAnswersWithTheNewOrder() {
        when(reorderDocumentBlocks.execute(ORG, ID, "en", List.of("outro", "intro")))
                .thenReturn(List.of(text("outro"), text("intro")));

        var response = endpoint.reorderDocumentBlocks(ORG, DOCUMENT_ID, "en",
                new ReorderBlocksRequest().blockIds(List.of("outro", "intro")));

        assertThat(response.getBody()).extracting(com.processpuzzle.document.model.DocumentBlock::getId)
                .containsExactly("outro", "intro");
    }

    @Test
    void replacingABlockAnswers200AndDeletingItAnswers204() {
        DocumentBlockInput input = new DocumentBlockInput().kind(com.processpuzzle.document.model.BlockKind.TEXT);
        when(replaceDocumentBlock.execute(ORG, ID, "en", "intro", input)).thenReturn(text("intro"));

        assertThat(endpoint.replaceDocumentBlock(ORG, DOCUMENT_ID, "en", "intro", input).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(endpoint.deleteDocumentBlock(ORG, DOCUMENT_ID, "en", "intro").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        verify(deleteDocumentBlock).execute(ORG, ID, "en", "intro");
    }

    // ── Public read ─────────────────────────────────────────────

    @Test
    void thePublicReadIsAddressedBySlugAndReportsTheFallback() {
        when(findPublishedContent.execute(ORG, "getting-started", "fr")).thenReturn(
                new FindPublishedContent.PublishedContentView(document(), view(), true, List.of("en")));

        var response = endpoint.getPublishedContent(ORG, "getting-started", "fr");

        assertThat(response.getBody().getSlug()).isEqualTo("getting-started");
        assertThat(response.getBody().getIsFallback()).isTrue();
        assertThat(response.getBody().getAvailableLocales()).containsExactly("en");
    }

    // ── Validation, import, export ──────────────────────────────

    @Test
    void validationAnswers200EvenWhenTheDocumentIsInvalid() {
        DocumentInput input = new DocumentInput().slug("s").title("t").sourceLocale("en");
        when(validateDocument.execute(input)).thenReturn(new ValidateDocument.ValidationOutcome(false,
                List.of(new DocumentValidationProblem("/blocks/0", "document.validation.unknown-port",
                        "'gone' is not a declared input port.", Severity.ERROR, "en"))));

        var response = endpoint.validateDocument(ORG, input);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getValid()).isFalse();
        assertThat(response.getBody().getProblems()).hasSize(1);
    }

    @Test
    void importReadsTheUploadedFileAndReportsWhatItDid() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "documents.yaml", "application/x-yaml",
                "documents: []".getBytes(StandardCharsets.UTF_8));
        when(importDocuments.execute(any(), any())).thenReturn(new ImportOutcome(1, 2, List.of()));

        var response = endpoint.importDocuments(ORG, file);

        assertThat(response.getBody().getCreated()).isEqualTo(1);
        assertThat(response.getBody().getUpdated()).isEqualTo(2);
    }

    @Test
    void anUnreadableUploadBecomesAnUncheckedIoException() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getInputStream()).thenThrow(new IOException("stream closed"));

        assertThatThrownBy(() -> endpoint.importDocuments(ORG, broken))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Cannot read the uploaded document file");
    }

    @Test
    void exportIsServedAsADownloadableYamlNamedAfterTheDocument() {
        when(exportDocument.execute(ORG, ID)).thenReturn("documents:\n  - slug: getting-started\n");

        ResponseEntity<Resource> response = endpoint.exportDocument(ORG, DOCUMENT_ID);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"" + ID + ".yaml\"");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/x-yaml");
        assertThat(response.getBody().getDescription()).isNotNull();
    }

    // region fixtures
    private static Document document() {
        return new Document(ORG, ID, "getting-started", "Getting started", "en", "ada");
    }

    private static DocumentDetails details() {
        return new DocumentDetails(document(), view(), List.of(view()));
    }

    private static DocumentTranslationView view() {
        return DocumentTranslationView.ofDraft(
                new DocumentDraft(ORG, ID, "en", DocumentContent.of(List.of(text("intro"))), null), null, 1L);
    }

    private static DocumentBlock text(String id) {
        return new DocumentBlock(id, BlockKind.TEXT, true, null, null, null, null, null, null);
    }
    // endregion
}
