package com.processpuzzle.document.usecase;

import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.document.domain.BlockKind;
import com.processpuzzle.document.domain.Document;
import com.processpuzzle.document.domain.DocumentBlock;
import com.processpuzzle.document.domain.DocumentContent;
import com.processpuzzle.document.domain.DocumentRepository;
import com.processpuzzle.document.domain.DocumentSignature;
import com.processpuzzle.document.domain.DocumentSignatureRepository;
import com.processpuzzle.document.domain.PublishedDocument;
import com.processpuzzle.document.domain.PublishedDocumentRepository;
import com.processpuzzle.document.usecase.exception.DocumentAccessDeniedException;
import com.processpuzzle.document.usecase.exception.DocumentNotFoundException;
import com.processpuzzle.document.usecase.exception.DocumentPublishingConflictException;
import com.processpuzzle.document.usecase.port.DocumentAccessPolicy;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignDocumentTest {

    private static final String ORG = "demo";
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    private DocumentRepository repository;
    private PublishedDocumentRepository publishedRepository;
    private DocumentSignatureRepository signatureRepository;
    private CurrentPrincipal principal;
    private List<PlatformEvent> facts;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        publishedRepository = mock(PublishedDocumentRepository.class);
        signatureRepository = mock(DocumentSignatureRepository.class);
        principal = mock(CurrentPrincipal.class);
        facts = new ArrayList<>();
        when(signatureRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(principal.isMemberOf(ORG)).thenReturn(true);
        when(principal.displayName()).thenReturn(Optional.of("Ada Lovelace"));
    }

    @Test
    void recordsTheSignerRevisionAndHashOfThePublishedContent() {
        stub(document(), snapshot(4L, "intro"));

        SignDocument.Outcome outcome = useCase().execute(ORG, ID, "employee", "e-1", null);

        DocumentSignature signature = outcome.signature();
        assertThat(outcome.created()).isTrue();
        assertThat(signature.getSignedBy()).isEqualTo("Ada Lovelace");
        assertThat(signature.getLocale()).isEqualTo("en");
        assertThat(signature.getRevision()).isEqualTo(4L);
        assertThat(signature.getContentHash()).hasSize(64).isEqualTo(SignDocument.hashOf(snapshot(4L, "intro")));
        assertThat(signature.getTitle()).isEqualTo("Employment contract");
        assertThat(signature.getSubjectType()).isEqualTo("employee");
        assertThat(signature.getSubjectId()).isEqualTo("e-1");
    }

    @Test
    void theHashFollowsTheContent() {
        assertThat(SignDocument.hashOf(snapshot(1L, "intro"))).isNotEqualTo(SignDocument.hashOf(snapshot(1L, "other")));
    }

    @Test
    void publishesSignedAboutTheObjectSignedFor() {
        stub(document(), snapshot(4L, "intro"));

        useCase().execute(ORG, ID, "employee", "e-1", "en");

        assertThat(facts).singleElement().satisfies(fact -> {
            assertThat(fact.action()).isEqualTo(PlatformEventAction.SIGNED);
            assertThat(fact.subjectType()).isEqualTo("employee");
            assertThat(fact.subjectId()).isEqualTo("e-1");
            assertThat(fact.payload()).containsEntry("documentId", ID)
                    .containsEntry("slug", "employment-contract")
                    .containsEntry("revision", 4L)
                    .containsEntry("signedBy", "Ada Lovelace");
        });
    }

    @Test
    void signingTheSameRevisionAgainReturnsTheExistingSignatureAndPublishesNothing() {
        stub(document(), snapshot(4L, "intro"));
        DocumentSignature existing = new DocumentSignature("s-1", ORG, ID, "Employment contract", "en", 4L, "hash",
                "employee", "e-1", "Ada Lovelace", Instant.now());
        when(signatureRepository.findFirstByOrgKeyAndDocumentIdAndLocaleAndRevisionAndSubjectTypeAndSubjectIdAndSignedBy(
                ORG, ID, "en", 4L, "employee", "e-1", "Ada Lovelace")).thenReturn(Optional.of(existing));

        SignDocument.Outcome outcome = useCase().execute(ORG, ID, "employee", "e-1", null);

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.signature()).isSameAs(existing);
        assertThat(facts).isEmpty();
        verify(signatureRepository, never()).save(any());
    }

    @Test
    void recordsTheSignersIdentityWhereTheTokenHasOne() {
        stub(document(), snapshot(4L, "intro"));
        when(principal.subject()).thenReturn(Optional.of("kc-ada"));

        DocumentSignature signature = useCase().execute(ORG, ID, "employee", "e-1", null).signature();

        assertThat(signature.getSignedBy()).isEqualTo("Ada Lovelace");
        assertThat(signature.getSignedById()).isEqualTo("kc-ada");
    }

    @Test
    void aSignatureHasNoIdentityWhenTheTokenCarriesNone() {
        stub(document(), snapshot(4L, "intro"));

        assertThat(useCase().execute(ORG, ID, "employee", "e-1", null).signature().getSignedById()).isNull();
    }

    @Test
    void signingAgainAsTheSameIdentityReturnsTheExistingSignature() {
        stub(document(), snapshot(4L, "intro"));
        when(principal.subject()).thenReturn(Optional.of("kc-ada"));
        DocumentSignature existing = new DocumentSignature("s-1", ORG, ID, "Employment contract", "en", 4L, "hash",
                "employee", "e-1", "Ada Lovelace", "kc-ada", Instant.now());
        when(signatureRepository.findFirstByOrgKeyAndDocumentIdAndLocaleAndRevisionAndSubjectTypeAndSubjectIdAndSignedById(
                ORG, ID, "en", 4L, "employee", "e-1", "kc-ada")).thenReturn(Optional.of(existing));

        SignDocument.Outcome outcome = useCase().execute(ORG, ID, "employee", "e-1", null);

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.signature()).isSameAs(existing);
        verify(signatureRepository, never()).save(any());
    }

    // The name is what a person reads; two of them can share one, and are two signers.
    @Test
    void twoPeopleOfOneNameAreTwoSigners() {
        stub(document(), snapshot(4L, "intro"));
        when(principal.subject()).thenReturn(Optional.of("kc-other-ada"));
        DocumentSignature firstAda = new DocumentSignature("s-1", ORG, ID, "Employment contract", "en", 4L, "hash",
                "employee", "e-1", "Ada Lovelace", "kc-ada", Instant.now());
        when(signatureRepository.findFirstByOrgKeyAndDocumentIdAndLocaleAndRevisionAndSubjectTypeAndSubjectIdAndSignedBy(
                ORG, ID, "en", 4L, "employee", "e-1", "Ada Lovelace")).thenReturn(Optional.of(firstAda));

        SignDocument.Outcome outcome = useCase().execute(ORG, ID, "employee", "e-1", null);

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.signature().getSignedById()).isEqualTo("kc-other-ada");
    }

    @Test
    void theAccessPolicysPrincipalIsPreferredOverTheToken() {
        stub(document(), snapshot(1L, "intro"));
        DocumentAccessPolicy policy = new DocumentAccessPolicy() {
            @Override
            public String currentPrincipal() {
                return "policy-user";
            }
        };

        assertThat(useCase(policy, provider(principal)).execute(ORG, ID, "employee", "e-1", null).signature()
                .getSignedBy()).isEqualTo("policy-user");
    }

    @Test
    void aDocumentWithNothingPublishedIsNotSignable() {
        stub(document(), null);

        SignDocument useCase = useCase();

        assertThatThrownBy(() -> useCase.execute(ORG, ID, "employee", "e-1", "en"))
                .isInstanceOf(DocumentPublishingConflictException.class)
                .satisfies(error -> assertThat(((DocumentPublishingConflictException) error).getErrorId())
                        .isEqualTo("document.sign.not-published"));
        assertThat(facts).isEmpty();
    }

    @Test
    void withoutAnAuthenticatedPrincipalNobodySigns() {
        stub(document(), snapshot(1L, "intro"));
        when(principal.displayName()).thenReturn(Optional.empty());
        SignDocument withoutName = useCase();
        SignDocument withoutPrincipal = useCase(new DocumentAccessPolicy() { }, provider(null));

        assertThatThrownBy(() -> withoutName.execute(ORG, ID, "employee", "e-1", null))
                .isInstanceOf(DocumentAccessDeniedException.class);
        assertThatThrownBy(() -> withoutPrincipal.execute(ORG, ID, "employee", "e-1", null))
                .isInstanceOf(DocumentAccessDeniedException.class);
    }

    /** A public document is readable by anyone, but its signatures would be facts in this tenant's stream. */
    @Test
    void aTokenOfAnotherRealmCannotSignEvenAPublicDocument() {
        Document document = document();
        document.replaceProperties("employment-contract", "Employment contract", null, null, "ada", "en", true,
                document.getRoles(), document.getPorts());
        stub(document, snapshot(1L, "intro"));
        when(principal.isMemberOf(ORG)).thenReturn(false);
        SignDocument useCase = useCase();

        assertThatThrownBy(() -> useCase.execute(ORG, ID, "employee", "e-1", null))
                .isInstanceOf(DocumentAccessDeniedException.class);
        verify(signatureRepository, never()).save(any());
    }

    @Test
    void aPolicyRefusingMembershipRefusesTheSignature() {
        stub(document(), snapshot(1L, "intro"));
        DocumentAccessPolicy policy = mock(DocumentAccessPolicy.class);
        when(policy.isAuthenticated()).thenReturn(true);
        when(policy.hasAnyRole(any())).thenReturn(true);
        doThrow(new DocumentAccessDeniedException("Not a member")).when(policy).requireAccess(ORG);

        SignDocument useCase = useCase(policy, provider(principal));

        assertThatThrownBy(() -> useCase.execute(ORG, ID, "employee", "e-1", null))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void anUnknownDocumentIsNotFound() {
        when(repository.findByOrgKeyAndId(ORG, "missing")).thenReturn(Optional.empty());

        SignDocument useCase = useCase();

        assertThatThrownBy(() -> useCase.execute(ORG, "missing", "employee", "e-1", null))
                .isInstanceOf(DocumentNotFoundException.class);
        verify(signatureRepository, never())
                .findFirstByOrgKeyAndDocumentIdAndLocaleAndRevisionAndSubjectTypeAndSubjectIdAndSignedBy(
                        anyString(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());
    }

    // region fixtures
    private SignDocument useCase() {
        return useCase(null, provider(principal));
    }

    private SignDocument useCase(DocumentAccessPolicy policy, ObjectProvider<CurrentPrincipal> principals) {
        return new SignDocument(repository, publishedRepository, signatureRepository,
                policy == null ? TestGuards.permitAll() : TestGuards.with(policy), TestFacts.recordingInto(facts),
                principals);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<CurrentPrincipal> provider(CurrentPrincipal principal) {
        ObjectProvider<CurrentPrincipal> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(principal);
        return provider;
    }

    private void stub(Document document, PublishedDocument snapshot) {
        when(repository.findByOrgKeyAndId(ORG, ID)).thenReturn(Optional.of(document));
        when(publishedRepository.findByOrgKeyAndDocumentIdAndLocale(ORG, ID, "en"))
                .thenReturn(Optional.ofNullable(snapshot));
    }

    private static Document document() {
        return new Document(ORG, ID, "employment-contract", "Employment contract", "en", "ada");
    }

    private static PublishedDocument snapshot(long revision, String blockId) {
        return new PublishedDocument(ORG, ID, "en", DocumentContent.of(List.of(
                new DocumentBlock(blockId, BlockKind.TEXT, false, null, null, null, null, null, null))),
                revision, Instant.parse("2026-10-17T09:00:00Z"), "ada");
    }
    // endregion
}
