package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

class DesignAssistantTest {

    @Test
    void aMessageGoesToTheModelWithTheSystemPromptAndItsReplyIsReturned() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("pp-designer").build());
        when(model.call(any(Prompt.class))).thenReturn(
            new ChatResponse(List.of(new Generation(new AssistantMessage("Saved the invoice draft.")))));
        DesignAssistant assistant = new DesignAssistant(ChatClient.create(model),
            new DefinitionDrafts(List.of(new InMemoryAuthoringParticipant("entity"))),
            new FixedAccess(), 5, 3, new AssistantAuditLog() {
                @Override
                public void toolCall(ToolCall call) {
                    // Tool calls are not what this test checks.
                }

                @Override
                public void turn(Turn turn) {
                    // Turns are not what this test checks.
                }
            }, new InMemoryAssistantQuota(Clock.systemUTC(), 100, 1_000_000));

        String reply = assistant.respond("Define an invoice entity.");

        assertThat(reply).isEqualTo("Saved the invoice draft.");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getContents())
            .contains(DesignAssistant.SYSTEM_PROMPT)
            .contains("Define an invoice entity.");
    }

    @Test
    void aFinishedTurnIsAuditedWithItsCallerAndTheToolCallsItMade() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("pp-designer").build());
        when(model.call(any(Prompt.class))).thenReturn(
            new ChatResponse(List.of(new Generation(new AssistantMessage("Done.")))));
        List<AssistantAuditLog.Turn> turns = new java.util.ArrayList<>();
        AssistantAuditLog audit = new AssistantAuditLog() {
            @Override
            public void toolCall(ToolCall call) {
                // Tool calls are not what this test checks.
            }

            @Override
            public void turn(Turn turn) {
                turns.add(turn);
            }
        };
        DesignAssistant assistant = new DesignAssistant(ChatClient.create(model),
            new DefinitionDrafts(List.of(new InMemoryAuthoringParticipant("entity"))), new FixedAccess(), 5, 3, audit, new InMemoryAssistantQuota(Clock.systemUTC(), 100, 1_000_000));

        assistant.respond("Define an invoice entity.");

        assertThat(turns).singleElement().satisfies(turn -> {
            assertThat(turn.principal()).isEqualTo("Ada");
            assertThat(turn.organization()).isEqualTo("acme");
            assertThat(turn.toolCalls()).isZero();
        });
    }

    @Test
    void aTurnOverTheDailyLimitNeverReachesTheModel() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("pp-designer").build());
        AssistantQuota exhausted = new AssistantQuota() {
            @Override
            public void checkTurnAllowed(String organization) {
                throw new AssistantQuotaExceededException(organization);
            }

            @Override
            public void recordTurn(String organization, long tokens) {
                // Nothing is recorded for a refused turn.
            }
        };
        DesignAssistant assistant = new DesignAssistant(ChatClient.create(model),
            new DefinitionDrafts(List.of(new InMemoryAuthoringParticipant("entity"))), new FixedAccess(), 5, 3,
            new LoggingAssistantAuditLog(), exhausted);

        assertThatThrownBy(() -> assistant.respond("Define an invoice entity."))
            .isInstanceOf(AssistantQuotaExceededException.class);
        verify(model, never()).call(any(Prompt.class));
    }

    private record FixedAccess() implements AssistantAccess {

        @Override
        public String currentOrganization() {
            return "acme";
        }

        @Override
        public boolean mayDesign() {
            return true;
        }

        @Override
        public String principal() {
            return "Ada";
        }
    }
}
