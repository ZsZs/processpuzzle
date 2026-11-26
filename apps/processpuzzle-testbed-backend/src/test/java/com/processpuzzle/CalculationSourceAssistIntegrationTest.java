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
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * The calculation source assist through the real platform, with only the model scripted: the organization's real entity
 * types reach the model, the real validator and FEEL engine check the candidate, and an output that writes an attribute the
 * entity does not have is sent back with the attributes it could have meant. The user's id and description are kept.
 */
@SpringBootTest(properties = {
    "processpuzzle.ai.assistant.enabled=true",
    // 32 bytes, Base64 encoded: seals the organizations' API keys, which this test never stores.
    "processpuzzle.ai.assistant.secret-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
class CalculationSourceAssistIntegrationTest {

    private static final String ORG = "calculation-assist";

    private static final String ENTITIES = """
            entityDefinitions:
              - code: parcel-item
                name: Parcel Item
                status: ACTIVE
                isEmbedded: true
                componentParents: [ parcel ]
                attributes:
                  - { code: label, name: Label, displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
                  - { code: weight, name: Weight, displayOrder: 2, valueKind: REFERENCE, formControlType: QUANTITY, quantityKind: MASS }
              - code: parcel
                name: Parcel
                status: ACTIVE
                isEmbedded: false
                componentParents: [ ]
                attributes:
                  - { code: reference, name: Reference, displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
                  - { code: totalWeight, name: Total Weight, displayOrder: 2, valueKind: REFERENCE, formControlType: QUANTITY, quantityKind: MASS, allowedUnits: [ kg, g ], defaultUnit: kg }
                  - code: items
                    name: Items
                    displayOrder: 3
                    valueKind: REFERENCE
                    formControlType: EMBEDDED_COMPONENTS
                    linkedEntityType: parcel-item
                    multiplicity: '0..n'
            """;

    private static final String HEADER = "calculation: parcel-weight\nname: Parcel weight\ndescription: Add up the weight of the parcel items.\n";

    private static final String GOOD_SOURCE = HEADER + """
        parameters:
          parcel: { entity: parcel }
        inputs:
          items: { from: parcel, path: items, entity: parcel-item }
        decisions:
          weights: { for: items, expr: "weight.baseAmount" }
          weightTotal: { requires: [weights], aggregate: { sum: weights } }
        output:
          entity: parcel
          mapping: { totalWeight: weightTotal }
        trigger:
          onChange: { entity: parcel }
        """;

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
    }

    private static ChatResponse reply(String source) {
        String escaped = source.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return new ChatResponse(List.of(new Generation(new AssistantMessage(
            "{\"candidate\":\"" + escaped + "\",\"explanation\":\"Sums the item weights.\",\"assumptions\":[\"Weights are added in kilograms.\"]}"))));
    }

    @Test
    void aCalculationIsWrittenForTheOrganizationsRealEntitiesAndAMisspeltOutputAttributeIsRepaired() {
        when(MODEL.call(any(Prompt.class))).thenReturn(reply(GOOD_SOURCE.replace("totalWeight: weightTotal", "totalWeigth: weightTotal")), reply(GOOD_SOURCE));

        AssistResult result = assist.assist(ORG, "calculation.source", new AssistRequest("Add up the weight of the parcel items.", Map.of(), HEADER));

        assertThat(result.candidate()).isEqualTo(GOOD_SOURCE);
        assertThat(result.issues()).isEmpty();
        assertThat(result.rounds()).isEqualTo(2);

        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(MODEL, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(0).getContents())
            .contains("- parcel (Parcel): reference: TEXT (required)", "items: list of embedded parcel-item",
                "- parcel-item (Parcel Item) [embedded component", "calculation: parcel-weight", "Add up the weight of the parcel items.");
        assertThat(prompts.getAllValues().get(1).getContents()).contains("'totalWeigth' is not an attribute of parcel", "totalWeight");
    }

    @Test
    void theUsersIdAndDescriptionAreKept() {
        String rewritten = GOOD_SOURCE.replace("calculation: parcel-weight", "calculation: something-else");
        when(MODEL.call(any(Prompt.class))).thenReturn(reply(rewritten), reply(GOOD_SOURCE));

        AssistResult result = assist.assist(ORG, "calculation.source", new AssistRequest("Add up the weight of the parcel items.", Map.of(), HEADER));

        assertThat(result.candidate()).isEqualTo(GOOD_SOURCE);
        assertThat(result.rounds()).isEqualTo(2);
    }
}
