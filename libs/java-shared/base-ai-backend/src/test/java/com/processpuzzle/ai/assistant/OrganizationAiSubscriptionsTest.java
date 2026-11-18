package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.domain.AiProvider;
import com.processpuzzle.ai.domain.OrganizationAiSubscription;
import com.processpuzzle.ai.domain.OrganizationAiSubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrganizationAiSubscriptionsTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private OrganizationAiSubscriptionRepository repository;
    private OrganizationAiSubscription stored;
    private AiSecretCipher cipher;
    private OrganizationAiSubscriptions subscriptions;

    @BeforeEach
    void setUp() {
        repository = mock(OrganizationAiSubscriptionRepository.class);
        cipher = new AiSecretCipher(KEY);
        when(repository.findById(any())).thenAnswer(i -> Optional.ofNullable(stored));
        when(repository.save(any())).thenAnswer(i -> stored = i.getArgument(0));
        subscriptions = new OrganizationAiSubscriptions(repository, cipher,
            Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));
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
        assertThatThrownBy(() -> subscriptions.save("acme", change(""), "Ada"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("API key");
    }

    @Test
    void aCompatibleServiceNeedsAPublicHttpsAddress() {
        for (String url : new String[] {null, "http://example.com/v1", "https://localhost/v1", "https://127.0.0.1/v1",
            "https://169.254.169.254/latest", "https://10.0.0.5/v1", "https://u:p@example.com/v1"}) {
            assertThatThrownBy(() -> subscriptions.save("acme",
                new OrganizationAiSubscriptions.Change(AiProvider.OPENAI_COMPATIBLE, "m", url, "k"), "Ada"))
                .as(String.valueOf(url)).isInstanceOf(IllegalArgumentException.class);
        }
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

    private static OrganizationAiSubscriptions.Change change(String apiKey) {
        return new OrganizationAiSubscriptions.Change(AiProvider.MISTRAL, "mistral-large-latest", null, apiKey);
    }
}
