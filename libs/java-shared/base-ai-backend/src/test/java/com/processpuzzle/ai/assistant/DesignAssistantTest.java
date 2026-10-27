package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

class DesignAssistantTest {

    private static final UUID SESSION = UUID.randomUUID();

    @Test
    void aMessageGoesToTheModelWithTheSystemPromptAndItsReplyIsReturned() {
        ChatModel model = modelReplying("Saved the invoice draft.");
        DesignAssistant assistant = assistant(model, sessionsWithHistory(List.of()), new RecordingAudit(),
            new InMemoryAssistantQuota(Clock.systemUTC(), 100, 1_000_000));

        String reply = assistant.respond(SESSION, "Define an invoice entity.");

        assertThat(reply).isEqualTo("Saved the invoice draft.");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(prompt.capture());
        assertThat(prompt.getValue().getContents())
            .contains(DesignAssistant.SYSTEM_PROMPT)
            .contains("Define an invoice entity.");
    }

    @Test
    void theReplyIsHandedOnPieceByPieceAsTheModelProducesIt() {
        ChatModel model = modelReplying("Saved ", "the invoice ", "draft.");
        DesignAssistant assistant = assistant(model, sessionsWithHistory(List.of()), new RecordingAudit(),
            new InMemoryAssistantQuota(Clock.systemUTC(), 100, 1_000_000));
        List<String> deltas = new ArrayList<>();

        String reply = assistant.respond(SESSION, "Define an invoice entity.", deltas::add);

        assertThat(deltas).containsExactly("Saved ", "the invoice ", "draft.");
        assertThat(reply).isEqualTo("Saved the invoice draft.");
    }

    @Test
    void theEarlierMessagesOfTheSessionReachTheModelBeforeTheNewOne() {
        ChatModel model = modelReplying("Renamed it.");
        DesignAssistant assistant = assistant(model,
            sessionsWithHistory(List.of(new UserMessage("Define an invoice entity."),
                new AssistantMessage("Saved the invoice draft."))),
            new RecordingAudit(), new InMemoryAssistantQuota(Clock.systemUTC(), 100, 1_000_000));

        assistant.respond(SESSION, "Rename it to bill.");

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).stream(prompt.capture());
        assertThat(prompt.getValue().getContents())
            .contains("Define an invoice entity.", "Saved the invoice draft.", "Rename it to bill.");
    }

    @Test
    void theWholeReplyIsKeptInTheSession() {
        AssistantSessions sessions = sessionsWithHistory(List.of());
        DesignAssistant assistant = assistant(modelReplying("Saved", "."), sessions, new RecordingAudit(),
            new InMemoryAssistantQuota(Clock.systemUTC(), 100, 1_000_000));

        assistant.respond(SESSION, "Define an invoice entity.");

        verify(sessions).append(eq(SESSION), eq("acme"), eq("ada-subject"), eq("Define an invoice entity."), eq("Saved."));
    }

    @Test
    void aFinishedTurnIsAuditedWithItsCallerAndWhatTheLastChunkSaysAboutTheModel() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("pp-designer").build());
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
            chunk("Done."),
            ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(""))))
                .metadata(ChatResponseMetadata.builder().model("mistral-large-latest").usage(new DefaultUsage(73, 12)).build())
                .build()));
        RecordingAudit audit = new RecordingAudit();
        DesignAssistant assistant = assistant(model, sessionsWithHistory(List.of()), audit,
            new InMemoryAssistantQuota(Clock.systemUTC(), 100, 1_000_000));

        assistant.respond(SESSION, "Define an invoice entity.");

        assertThat(audit.turns).singleElement().satisfies(turn -> {
            assertThat(turn.principal()).isEqualTo("Ada");
            assertThat(turn.organization()).isEqualTo("acme");
            assertThat(turn.model()).isEqualTo("mistral-large-latest");
            assertThat(turn.promptTokens()).isEqualTo(73);
            assertThat(turn.completionTokens()).isEqualTo(12);
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
        DesignAssistant assistant = assistant(model, sessionsWithHistory(List.of()), new RecordingAudit(), exhausted);

        assertThatThrownBy(() -> assistant.respond(SESSION, "Define an invoice entity."))
            .isInstanceOf(AssistantQuotaExceededException.class);
        verify(model, never()).stream(any(Prompt.class));
    }

    /** A model that answers in the given pieces, one chunk each, the way a streamed reply arrives. */
    private static ChatModel modelReplying(String... pieces) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("pp-designer").build());
        when(model.stream(any(Prompt.class))).thenReturn(Flux.fromIterable(Arrays.stream(pieces).map(DesignAssistantTest::chunk).toList()));
        return model;
    }

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** Sessions whose history is the given messages; the exchanges are recorded on the mock for the tests to check. */
    private static AssistantSessions sessionsWithHistory(List<Message> history) {
        AssistantSessions sessions = mock(AssistantSessions.class);
        when(sessions.history(any(UUID.class), anyString(), anyString())).thenReturn(history);
        return sessions;
    }

    private static DesignAssistant assistant(ChatModel model, AssistantSessions sessions, AssistantAuditLog audit,
                                             AssistantQuota quota) {
        return new DesignAssistant(ChatClient.create(model),
            new DefinitionDrafts(List.of(new InMemoryAuthoringParticipant("entity"))), new FixedAccess(),
            new AssistantLimits(5, 3), audit, quota, sessions);
    }

    private static final class RecordingAudit implements AssistantAuditLog {

        private final List<Turn> turns = new ArrayList<>();

        @Override
        public void toolCall(ToolCall call) {
            // Tool calls are not what this test checks.
        }

        @Override
        public void turn(Turn turn) {
            turns.add(turn);
        }
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

        @Override
        public String subject() {
            return "ada-subject";
        }
    }
}
