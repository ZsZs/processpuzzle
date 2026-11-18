package com.processpuzzle.ai.assistant;

import com.processpuzzle.ai.domain.AiProvider;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * An organization's AI subscription, for its administrators only. The API key is accepted but never returned: a read
 * says only that a subscription exists.
 */
@RestController
@ConditionalOnProperty(prefix = "processpuzzle.ai.assistant", name = "enabled", havingValue = "true")
public class OrganizationAiSubscriptionEndpoint {

    private static final String PATH = "/organizations/{orgKey}/ai-subscription";

    private final OrganizationAiSubscriptions subscriptions;
    private final OrganizationGuard guard;
    private final AssistantAccess access;

    public OrganizationAiSubscriptionEndpoint(OrganizationAiSubscriptions subscriptions, OrganizationGuard guard,
                                              AssistantAccess access) {
        this.subscriptions = subscriptions;
        this.guard = guard;
        this.access = access;
    }

    @GetMapping(PATH)
    public ResponseEntity<AiSubscriptionResponse> get(@PathVariable String orgKey) {
        guard.requireAdministration(orgKey);
        return subscriptions.find(orgKey)
            .map(view -> ResponseEntity.ok(AiSubscriptionResponse.of(view)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(PATH)
    public AiSubscriptionResponse put(@PathVariable String orgKey, @RequestBody AiSubscriptionRequest request) {
        guard.requireAdministration(orgKey);
        try {
            return AiSubscriptionResponse.of(subscriptions.save(orgKey,
                new OrganizationAiSubscriptions.Change(request.provider(), request.model(), request.baseUrl(),
                    request.apiKey()), access.principal()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping(PATH)
    public ResponseEntity<Void> delete(@PathVariable String orgKey) {
        guard.requireAdministration(orgKey);
        subscriptions.delete(orgKey);
        return ResponseEntity.noContent().build();
    }

    /** {@code apiKey} may be blank when only the other fields change. */
    public record AiSubscriptionRequest(AiProvider provider, String model, String baseUrl, String apiKey) {
    }

    public record AiSubscriptionResponse(AiProvider provider, String model, String baseUrl, boolean keySet,
                                         Instant updatedAt, String updatedBy) {

        static AiSubscriptionResponse of(OrganizationAiSubscriptions.View view) {
            return new AiSubscriptionResponse(view.provider(), view.model(), view.baseUrl(), true, view.updatedAt(),
                view.updatedBy());
        }
    }
}
