package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.domain.AiProvider;
import com.processpuzzle.ai.domain.OrganizationAiSubscription;
import com.processpuzzle.ai.domain.OrganizationAiSubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrganizationAiSubscriptionsTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private OrganizationAiSubscriptionRepository repository;
    private OrganizationAiSubscription stored;
    private AiSecretCipher cipher;
    private OrganizationAiSubscriptions subscriptions;
    private final ModelLister lister = mock(ModelLister.class);

    @BeforeEach
    void setUp() {
        repository = mock(OrganizationAiSubscriptionRepository.class);
        cipher = new AiSecretCipher(KEY);
        when(repository.findById(any())).thenAnswer(i -> Optional.ofNullable(stored));
        when(repository.save(any())).thenAnswer(i -> stored = i.getArgument(0));
        subscriptions = new OrganizationAiSubscriptions(repository, cipher,
            Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC), lister);
    }

    @Test
    void theKeyIsStoredSealedAndNeverReadBack() {
        var view = subscriptions.save("acme", change("sk-secret"), "Ada");

        assertThat(stored.getSealedKey()).doesNotContain("sk-secret");
        assertThat(cipher.open("acme", stored.getSealedKey())).isEqualTo("sk-secret");
        assertThat(view.toString()).doesNotContain("sk-secret");
        assertThat(view.updatedBy()).isEqualTo("Ada");
    }

    @Test
    void aBlankKeyOnUpdateKeepsTheStoredOne() {
        subscriptions.save("acme", change("sk-secret"), "Ada");
        String sealed = stored.getSealedKey();

        subscriptions.save("acme", new OrganizationAiSubscriptions.Change(AiProvider.MISTRAL, "mistral-small-latest", null, " "),
            "Ada");

        assertThat(stored.getSealedKey()).isEqualTo(sealed);
        assertThat(stored.getModel()).isEqualTo("mistral-small-latest");
    }

    @Test
    void theFirstSaveNeedsAKey() {
        OrganizationAiSubscriptions.Change withoutKey = change("");

        assertThatThrownBy(() -> subscriptions.save("acme", withoutKey, "Ada"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("API key");
    }

    @Test
    void aCompatibleServiceNeedsAPublicHttpsAddress() {
        for (String url : new String[] {null, "http://example.com/v1", "https://localhost/v1", "https://127.0.0.1/v1",
            "https://169.254.169.254/latest", "https://10.0.0.5/v1", "https://u:p@example.com/v1"}) {
            OrganizationAiSubscriptions.Change change =
                new OrganizationAiSubscriptions.Change(AiProvider.OPENAI_COMPATIBLE, "m", url, "k");
            assertThatThrownBy(() -> subscriptions.save("acme", change, "Ada"))
                .as(String.valueOf(url)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void aCompatibleServiceAtAPublicAddressIsAcceptedAndGetsAClient() {
        var view = subscriptions.save("acme",
            new OrganizationAiSubscriptions.Change(AiProvider.OPENAI_COMPATIBLE, "gpt", "https://8.8.8.8/v1", "k"), "Ada");

        assertThat(view.baseUrl()).isEqualTo("https://8.8.8.8/v1");
        assertThat(subscriptions.clientFor("acme")).isNotNull();
        assertThat(subscriptions.find("acme")).isPresent();
    }

    @Test
    void aMistralSubscriptionHasNoBaseUrlOfItsOwn() {
        var view = subscriptions.save("acme", new OrganizationAiSubscriptions.Change(
            AiProvider.MISTRAL, "mistral-large-latest", "https://8.8.8.8/v1", "k"), null);

        assertThat(view.baseUrl()).isNull();
        assertThat(view.updatedBy()).isEqualTo("unknown");
    }

    @Test
    void theProviderAndAModelAreRequired() {
        OrganizationAiSubscriptions.Change noProvider = new OrganizationAiSubscriptions.Change(null, "m", null, "k");
        OrganizationAiSubscriptions.Change noModel = new OrganizationAiSubscriptions.Change(AiProvider.MISTRAL, " ", null, "k");
        OrganizationAiSubscriptions.Change longModel =
            new OrganizationAiSubscriptions.Change(AiProvider.MISTRAL, "m".repeat(201), null, "k");
        OrganizationAiSubscriptions.Change longKey =
            new OrganizationAiSubscriptions.Change(AiProvider.MISTRAL, "m", null, "k".repeat(1001));

        assertThatThrownBy(() -> subscriptions.save("acme", null, "Ada")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subscriptions.save("acme", noProvider, "Ada")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subscriptions.save("acme", noModel, "Ada")).hasMessageContaining("model");
        assertThatThrownBy(() -> subscriptions.save("acme", longModel, "Ada")).hasMessageContaining("model");
        assertThatThrownBy(() -> subscriptions.save("acme", longKey, "Ada")).hasMessageContaining("too long");
    }

    @Test
    void theModelsAreListedWithTheTypedKeyAtMistralsAddress() {
        when(lister.list("https://api.mistral.ai/v1", "sk-typed")).thenReturn(List.of("a", "b"));

        assertThat(subscriptions.listModels("acme", AiProvider.MISTRAL, "https://ignored", " sk-typed ")).containsExactly("a", "b");
    }

    @Test
    void aBlankKeyListsWithTheStoredKeyOfTheSameProvider() {
        subscriptions.save("acme", change("sk-secret"), "Ada");
        when(lister.list("https://api.mistral.ai/v1", "sk-secret")).thenReturn(List.of("a"));

        assertThat(subscriptions.listModels("acme", AiProvider.MISTRAL, null, "")).containsExactly("a");
    }

    @Test
    void aBlankKeyDoesNotBorrowTheStoredKeyOfAnotherProvider() {
        subscriptions.save("acme", change("sk-secret"), "Ada");

        assertThatThrownBy(() -> subscriptions.listModels("acme", AiProvider.OPENAI_COMPATIBLE, "https://8.8.8.8/v1", null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("API key");
        verifyNoInteractions(lister);
    }

    @Test
    void listingNeedsAProviderAKeyAndAPublicAddress() {
        assertThatThrownBy(() -> subscriptions.listModels("acme", null, null, "k")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subscriptions.listModels("acme", AiProvider.MISTRAL, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subscriptions.listModels("acme", AiProvider.OPENAI_COMPATIBLE, "https://10.0.0.1/v1", "k"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subscriptions.listModels("acme", AiProvider.MISTRAL, null, "k".repeat(1001)))
            .hasMessageContaining("too long");
        verifyNoInteractions(lister);
    }

    @Test
    void deletingRemovesTheOrganizationsRow() {
        subscriptions.delete("acme");

        verify(repository).deleteByOrganization("acme");
    }

    @Test
    void anOrganizationWithoutASubscriptionHasNoModel() {
        assertThatThrownBy(() -> subscriptions.clientFor("acme")).isInstanceOf(AssistantNotConfiguredException.class);
    }

    @Test
    void aSubscribedOrganizationGetsAClient() {
        subscriptions.save("acme", change("sk-secret"), "Ada");

        assertThat(subscriptions.clientFor("acme")).isNotNull();
    }

    @Test
    void aSealedKeyDoesNotOpenForAnotherOrganization() {
        String sealed = cipher.seal("acme", "sk-secret");

        assertThatThrownBy(() -> cipher.open("other", sealed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theMasterKeyMustBe32Bytes() {
        assertThatThrownBy(() -> new AiSecretCipher("c2hvcnQ=")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AiSecretCipher(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aCorruptSealedKeyDoesNotOpen() {
        assertThatThrownBy(() -> cipher.open("acme", "not base64 !")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.open("acme", Base64.getEncoder().encodeToString(new byte[40])))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theMasterKeyMustBeBase64() {
        assertThatThrownBy(() -> new AiSecretCipher("%%%")).isInstanceOf(IllegalStateException.class);
    }

    private static OrganizationAiSubscriptions.Change change(String apiKey) {
        return new OrganizationAiSubscriptions.Change(AiProvider.MISTRAL, "mistral-large-latest", null, apiKey);
    }

    @Test
    void anOrganizationIsConfiguredExactlyWhenItHasASubscription() {
        when(repository.existsById("acme")).thenReturn(true);
        when(repository.existsById("other")).thenReturn(false);

        assertThat(subscriptions.isConfigured("acme")).isTrue();
        assertThat(subscriptions.isConfigured("other")).isFalse();
    }

    @Test
    void theDefaultIsConfiguredAsksForTheClient() {
        AssistantModels unconfigured = organization -> {
            throw new AssistantNotConfiguredException(organization);
        };
        AssistantModels configured = organization -> null;

        assertThat(unconfigured.isConfigured("acme")).isFalse();
        assertThat(configured.isConfigured("acme")).isTrue();
    }
}
