package com.processpuzzle.ai.assistant;

import com.processpuzzle.ai.domain.AiProvider;
import com.processpuzzle.ai.domain.OrganizationAiSubscription;
import com.processpuzzle.ai.domain.OrganizationAiSubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The organizations' AI subscriptions: which model service each has chosen and its own key for it. The platform has no
 * key of its own, so an organization without a subscription has no assistant. The model is called directly with the
 * organization's key; nothing is shared between organizations.
 */
public class OrganizationAiSubscriptions implements AssistantModels {

    static final String MISTRAL_BASE_URL = "https://api.mistral.ai/v1";
    private static final int MAX_MODEL_LENGTH = 200;
    private static final int MAX_KEY_LENGTH = 1000;

    private final OrganizationAiSubscriptionRepository repository;
    private final AiSecretCipher cipher;
    private final Clock clock;
    private final ModelLister modelLister;

    public OrganizationAiSubscriptions(OrganizationAiSubscriptionRepository repository, AiSecretCipher cipher,
                                       Clock clock, ModelLister modelLister) {
        this.repository = repository;
        this.cipher = cipher;
        this.clock = clock;
        this.modelLister = modelLister;
    }

    /**
     * The models the key may use, asked of the provider. A blank {@code apiKey} means the stored one, so an
     * administrator can list the models again without typing the key; it is then the stored provider's key, never
     * another's, and the address is checked like a saved one.
     *
     * @throws IllegalArgumentException    when the provider, address or key is missing or refused
     * @throws ModelLister.ModelListException when the provider cannot be asked
     */
    @Transactional(readOnly = true)
    public List<String> listModels(String organization, AiProvider provider, String baseUrl, String apiKey) {
        if (provider == null) {
            throw new IllegalArgumentException("The provider is required");
        }
        String url = MISTRAL_BASE_URL;
        if (provider == AiProvider.OPENAI_COMPATIBLE) {
            url = baseUrl == null ? "" : baseUrl.trim();
            AiEndpointPolicy.require(url);
        }
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            OrganizationAiSubscription stored = repository.findById(organization)
                .filter(s -> s.getProvider() == provider)
                .orElseThrow(() -> new IllegalArgumentException("Enter the API key to list the models"));
            key = cipher.open(organization, stored.getSealedKey());
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("The API key is too long");
        }
        return modelLister.list(url, key);
    }

    /** What an administrator may read back: everything but the key. */
    public record View(AiProvider provider, String model, String baseUrl, Instant updatedAt, String updatedBy) {
    }

    /** What an administrator submits; a blank {@code apiKey} keeps the stored one. */
    public record Change(AiProvider provider, String model, String baseUrl, String apiKey) {
    }

    @Transactional(readOnly = true)
    public Optional<View> find(String organization) {
        return repository.findById(organization).map(OrganizationAiSubscriptions::view);
    }

    /** @throws IllegalArgumentException with a reason fit to show the administrator */
    @Transactional
    public View save(String organization, Change change, String principal) {
        if (change == null || change.provider() == null) {
            throw new IllegalArgumentException("The provider is required");
        }
        String model = change.model() == null ? "" : change.model().trim();
        if (model.isEmpty() || model.length() > MAX_MODEL_LENGTH) {
            throw new IllegalArgumentException("The model name is required and at most %d characters"
                .formatted(MAX_MODEL_LENGTH));
        }
        String baseUrl = null;
        if (change.provider() == AiProvider.OPENAI_COMPATIBLE) {
            baseUrl = change.baseUrl() == null ? "" : change.baseUrl().trim();
            AiEndpointPolicy.require(baseUrl);
        }
        OrganizationAiSubscription subscription =
            repository.findById(organization).orElseGet(() -> new OrganizationAiSubscription(organization));
        String sealedKey = sealedKeyFor(organization, subscription, change.apiKey());
        subscription.update(change.provider(), model, baseUrl, sealedKey,
            principal == null || principal.isBlank() ? "unknown" : principal, clock.instant());
        return view(repository.save(subscription));
    }

    @Transactional
    public void delete(String organization) {
        repository.deleteByOrganization(organization);
    }

    @Override
    public ChatClient clientFor(String organization) {
        OrganizationAiSubscription subscription = repository.findById(organization)
            .orElseThrow(() -> new AssistantNotConfiguredException(organization));
        String baseUrl = subscription.getProvider() == AiProvider.MISTRAL ? MISTRAL_BASE_URL : subscription.getBaseUrl();
        if (subscription.getProvider() != AiProvider.MISTRAL) {
            AiEndpointPolicy.require(baseUrl);
        }
        return chatClient(baseUrl, cipher.open(organization, subscription.getSealedKey()), subscription.getModel());
    }

    /**
     * The chat client for one provider. {@code streamUsage} is deliberately left unset: Spring AI then streams with
     * {@code stream_options.include_usage} alone, which reports the tokens. Setting it, either way, also sends
     * {@code include_obfuscation}, which Mistral rejects with a 422 (the shared gateway used to strip it).
     */
    static ChatClient chatClient(String baseUrl, String apiKey, String model) {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .options(OpenAiChatOptions.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .model(model)
                .build())
            .build();
        return ChatClient.create(chatModel);
    }

    private String sealedKeyFor(String organization, OrganizationAiSubscription existing, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            if (existing.getSealedKey() == null) {
                throw new IllegalArgumentException("The API key is required");
            }
            return existing.getSealedKey();
        }
        if (apiKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("The API key is too long");
        }
        return cipher.seal(organization, apiKey.trim());
    }

    private static View view(OrganizationAiSubscription s) {
        return new View(s.getProvider(), s.getModel(), s.getBaseUrl(), s.getUpdatedAt(), s.getUpdatedBy());
    }
}
