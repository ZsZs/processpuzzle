package com.processpuzzle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.assist.AssistResult;
import com.processpuzzle.ai.assist.AuthoringAssist;
import com.processpuzzle.ai.assistant.AssistantAccess;
import com.processpuzzle.ai.assistant.AssistantModels;
import com.processpuzzle.baseentity.adapter.inbound.EntityDefinitionReleaseParticipant;
import com.processpuzzle.baseentity.definition.usecases.inbound.ImportEntityDefinitions;
import com.processpuzzle.core.assist.AssistRequest;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The rule expression assist through the real platform, with only the model scripted: the entity the rule is for is found
 * by its name among the organization's released definitions, its attributes (including an embedded component and a quantity
 * with its unit) reach the model and the candidate is checked against them. A candidate that misspells an attribute is sent
 * back with the attributes it could have meant, and the repaired one is returned.
 */
@SpringBootTest(properties = {
    "processpuzzle.ai.assistant.enabled=true",
    // 32 bytes, Base64 encoded: seals the organizations' API keys, which this test never stores.
    "processpuzzle.ai.assistant.secret-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
class RuleExpressionAssistIntegrationTest {

    private static final String ORG = "rule-assist";

    private static final String ENTITIES = """
            entityDefinitions:
              - code: assist-line
                name: Assist Line
                status: ACTIVE
                isEmbedded: true
                componentParents: [ assist-order ]
                attributes:
                  - { code: quantity, name: Quantity, displayOrder: 1, valueKind: REFERENCE, formControlType: QUANTITY, quantityKind: MASS, allowedUnits: [ kg, g ], required: true }
                  - { code: amount, name: Amount, displayOrder: 2, valueKind: NUMBER, formControlType: NUMBER }
              - code: assist-order
                name: Assist Order
                status: ACTIVE
                isEmbedded: false
                componentParents: [ ]
                attributes:
                  - { code: reference, name: Reference, displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
                  - { code: total, name: Total, displayOrder: 2, valueKind: NUMBER, formControlType: NUMBER, required: true }
                  - code: lines
                    name: Lines
                    displayOrder: 3
                    valueKind: REFERENCE
                    formControlType: EMBEDDED_COMPONENTS
                    linkedEntityType: assist-line
                    multiplicity: '0..n'
            """;

    private static final String BAD = "{\"candidate\":\"entity.totl > 0\",\"explanation\":\"x\",\"assumptions\":[]}";
    private static final String GOOD = """
        {"candidate":"entity.total > 0 && (!entity.lines || entity.lines.every(l => qty(l.quantity).gt(qty('0 kg'))))",
         "explanation":"The total is positive and every line weighs something.","assumptions":["Lines are optional."]}""";

    private static final ChatModel MODEL = mock(ChatModel.class);

    @TestConfiguration
    static class ScriptedModel {
        @Bean
        @Primary
        AssistantModels scriptedModels() {
            return organization -> ChatClient.create(MODEL);
        }

        @Bean
        @Primary
        AssistantAccess fixedAccess() {
            AssistantAccess access = mock(AssistantAccess.class);
            when(access.currentOrganization()).thenReturn(ORG);
            when(access.principal()).thenReturn("Ada");
            when(access.subject()).thenReturn("ada-subject");
            when(access.mayDesign()).thenReturn(true);
            return access;
        }
    }

    @MockitoBean private OrganizationGuard guard;
    @Autowired private AuthoringAssist assist;
    @Autowired private ImportEntityDefinitions importEntities;
    @Autowired private EntityDefinitionReleaseParticipant entityRelease;

    @BeforeEach
    void seed() throws IOException {
        assertThat(importEntities.execute(ORG, new ByteArrayInputStream(ENTITIES.getBytes(StandardCharsets.UTF_8))).errors()).isEmpty();
        entityRelease.release(ORG);
        reset(MODEL);
        when(MODEL.getOptions()).thenReturn(OpenAiChatOptions.builder().model("pp-designer").build());
        when(MODEL.call(any(Prompt.class))).thenReturn(reply(BAD), reply(GOOD));
    }

    private static ChatResponse reply(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void aRuleIsWrittenForARealEntityAndAMisspeltAttributeIsRepaired() {
        AssistResult result = assist.assist(ORG, "rule.expression", new AssistRequest(
            "The total must be positive and every line must weigh something", Map.of("context", "Assist Order"), ""));

        assertThat(result.candidate()).startsWith("entity.total > 0");
        assertThat(result.issues()).isEmpty();
        assertThat(result.rounds()).isEqualTo(2);
        assertThat(result.assumptions()).containsExactly("Lines are optional.");

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(MODEL, times(2)).call(prompts.capture());
        String first = prompts.getAllValues().get(0).getContents();
        assertThat(first).contains("Entity: Assist Order", "- reference: TEXT, required", "- total: NUMBER, required",
            "- lines: list of embedded object", "  - quantity: QUANTITY (kg, g), required", "  - amount: NUMBER",
            "The total must be positive and every line must weigh something");
        String second = prompts.getAllValues().get(1).getContents();
        assertThat(second).contains("entity.totl", "is not an attribute of Assist Order", "total");
    }

    @Test
    void anEntityTheOrganizationDoesNotHaveIsAWarningAndTheSyntaxIsStillChecked() {
        when(MODEL.call(any(Prompt.class))).thenReturn(reply(GOOD));

        AssistResult result = assist.assist(ORG, "rule.expression", new AssistRequest(
            "The total must be positive", Map.of("context", "No Such Entity"), ""));

        assertThat(result.rounds()).isEqualTo(1);
        assertThat(result.issues()).extracting(AuthoringIssue::severity).containsOnly(AuthoringIssue.Severity.WARNING);
        assertThat(new ArrayList<>(result.issues()).get(0).text()).contains("No Such Entity");
    }
}
