package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.TimeUnit;

import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.security.SecurityProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Phase 2 step 1 of docs/ai/implementation-plan.md: the real model, through the gateway, calls the draft tools. A
 * throwaway that spends real tokens, so it only runs when {@code PP_LLM_GATEWAY_URL} is set. Run it against stage
 * through the SSH tunnel described in {@link com.processpuzzle.ai.LlmGatewaySmokeTest}.
 */
@EnabledIfEnvironmentVariable(named = "PP_LLM_GATEWAY_URL", matches = ".+")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class DesignAssistantGatewaySmokeTest {

    @Test
    void theModelSavesADraftThroughTheToolAndTheTurnReportsIt() {
        InMemoryAuthoringParticipant entities = new InMemoryAuthoringParticipant("entity");
        OpenAiChatModel model = OpenAiChatModel.builder()
            .options(OpenAiChatOptions.builder()
                .baseUrl(System.getenv("PP_LLM_GATEWAY_URL"))
                .apiKey(System.getenv().getOrDefault("PP_LLM_GATEWAY_KEY", "unset"))
                .model(System.getenv().getOrDefault("PP_LLM_GATEWAY_MODEL", "pp-designer"))
                .build())
            .build();
        DesignAssistant assistant = new DesignAssistant(ChatClient.create(model),
            new DefinitionDrafts(List.of(entities)), new SecurityContextAssistantAccess(new CurrentPrincipal(new SecurityProperties())), 5);
        signInAsAcmeDesigner();

        String reply = assistant.respond("Define the entity with key invoice and name Invoice, with no attributes. "
            + "Follow the workflow: get_schema for kind entity, then save_draft with kind entity and key invoice.");

        System.out.println("model reply: " + reply);
        assertThat(entities.list("acme")).as("the model called save_draft").containsExactly("invoice");
    }

    /** A designer token for realm acme in the security context, as the endpoint sees it for a real request. */
    private static void signInAsAcmeDesigner() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
            .issuer("https://auth.example.com/realms/acme").subject("designer")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            List.of(new SimpleGrantedAuthority("definition-designer"))));
    }
}
