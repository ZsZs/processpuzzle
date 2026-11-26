package com.processpuzzle.ai.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.ai.assistant.AssistantAccess;
import com.processpuzzle.ai.assistant.AssistantAuditLog;
import com.processpuzzle.ai.assistant.AssistantLimits;
import com.processpuzzle.ai.assistant.AssistantModels;
import com.processpuzzle.ai.assistant.AssistantNotConfiguredException;
import com.processpuzzle.ai.assistant.AssistantQuota;
import com.processpuzzle.ai.assistant.AssistantQuotaExceededException;
import com.processpuzzle.core.assist.AssistRequest;
import com.processpuzzle.core.assist.AuthoringAssistParticipant;
import com.processpuzzle.core.definition.AuthoringIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

class AuthoringAssistTest {

    private static final String GOOD = """
        {"candidate": "entity.total > 0", "explanation": "The total is positive.", "assumptions": ["Zero is not positive."]}""";
    private static final String BAD = """
        {"candidate": "entity.totl > 0", "explanation": "x", "assumptions": []}""";
    private static final AssistRequest REQUEST =
        new AssistRequest("The total must be positive", Map.of("context", "Order"), "");

    private final RecordingAudit audit = new RecordingAudit();
    private final List<String> quotaRecords = new ArrayList<>();

    @Test
    void aCleanCandidateIsReturnedAfterOneModelCall() {
        ChatModel model = modelReplying(GOOD);

        AssistResult result = assist(model, new FakeParticipant()).assist("acme", "rule.expression", REQUEST);

        assertThat(result.candidate()).isEqualTo("entity.total > 0");
        assertThat(result.explanation()).isEqualTo("The total is positive.");
        assertThat(result.assumptions()).containsExactly("Zero is not positive.");
        assertThat(result.issues()).isEmpty();
        assertThat(result.rounds()).isEqualTo(1);
        assertThat(result.model()).isEqualTo("pp-designer");
        verify(model, times(1)).call(any(Prompt.class));
    }

    @Test
    void theModelReadsTheGuideTheContextAndTheDescription() {
        ChatModel model = modelReplying(GOOD);
        FakeParticipant participant = new FakeParticipant();
        participant.context = "attributes: total (number)";

        assist(model, participant).assist("acme", "rule.expression",
            new AssistRequest("The total must be positive", Map.of(), "entity.total >= 0"));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getContents())
            .contains(AuthoringAssist.SYSTEM_PROMPT, "GUIDE-TEXT", "attributes: total (number)",
                "The total must be positive", "entity.total >= 0");
    }

    @Test
    void aCandidateWithErrorsIsSentBackWithItsIssuesAndTheRepairIsReturned() {
        ChatModel model = modelReplying(BAD, GOOD);

        AssistResult result = assist(model, new FakeParticipant()).assist("acme", "rule.expression", REQUEST);

        assertThat(result.candidate()).isEqualTo("entity.total > 0");
        assertThat(result.issues()).isEmpty();
        assertThat(result.rounds()).isEqualTo(2);
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(prompt.capture());
        assertThat(prompt.getAllValues().get(1).getContents())
            .contains("entity.totl > 0", "No attribute totl", "candidate.expression");
    }

    @Test
    void whenTheRepairRoundsRunOutTheLastCandidateIsReturnedWithItsIssues() {
        ChatModel model = modelReplying(BAD, BAD, BAD, BAD, BAD);

        AssistResult result = assist(model, new FakeParticipant()).assist("acme", "rule.expression", REQUEST);

        assertThat(result.rounds()).isEqualTo(4);
        assertThat(result.candidate()).isEqualTo("entity.totl > 0");
        assertThat(result.issues()).extracting(AuthoringIssue::severity).containsExactly(AuthoringIssue.Severity.ERROR);
        assertThat(audit.assists).singleElement().extracting(AssistantAuditLog.Assist::outcome).isEqualTo("issues");
    }

    @Test
    void aWarningIsReturnedWithoutAnotherRound() {
        ChatModel model = modelReplying(GOOD);
        FakeParticipant participant = new FakeParticipant();
        participant.warn = true;

        AssistResult result = assist(model, participant).assist("acme", "rule.expression", REQUEST);

        assertThat(result.rounds()).isEqualTo(1);
        assertThat(result.issues()).extracting(AuthoringIssue::severity).containsExactly(AuthoringIssue.Severity.WARNING);
    }

    @Test
    void aReplyThatIsNotTheRequestedJsonIsRepairedLikeAnyOtherProblem() {
        ChatModel model = modelReplying("Sure! Here you go: entity.total > 0", GOOD);

        AssistResult result = assist(model, new FakeParticipant()).assist("acme", "rule.expression", REQUEST);

        assertThat(result.candidate()).isEqualTo("entity.total > 0");
        assertThat(result.rounds()).isEqualTo(2);
    }

    @Test
    void whenNoReplyIsReadableTheResultHasNoCandidateAndSaysWhy() {
        ChatModel model = modelReplying("nope", "nope", "nope", "nope");

        AssistResult result = assist(model, new FakeParticipant()).assist("acme", "rule.expression", REQUEST);

        assertThat(result.candidate()).isEmpty();
        assertThat(result.issues()).hasSize(1);
    }

    @Test
    void anUnknownTaskIsRefusedBeforeAnyModelCall() {
        ChatModel model = modelReplying(GOOD);

        assertThatThrownBy(() -> assist(model, new FakeParticipant()).assist("acme", "nope", REQUEST))
            .isInstanceOf(UnknownAssistTaskException.class);
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void aCallerWhoMayNotEditTheTargetIsRefusedBeforeAnyModelCall() {
        ChatModel model = modelReplying(GOOD);
        FakeParticipant participant = new FakeParticipant();
        participant.denied = true;

        assertThatThrownBy(() -> assist(model, participant).assist("acme", "rule.expression", REQUEST))
            .isInstanceOf(SecurityException.class);
        verify(model, never()).call(any(Prompt.class));
        assertThat(audit.assists).isEmpty();
    }

    @Test
    void anOrganizationWithoutASubscriptionIsRefusedBeforeAnyModelCall() {
        AuthoringAssist assist = new AuthoringAssist(organization -> {
            throw new AssistantNotConfiguredException(organization);
        }, new FixedAccess(), new AssistantLimits(5, 3), audit, new CountingQuota(false), List.of(new FakeParticipant()));

        assertThatThrownBy(() -> assist.assist("acme", "rule.expression", REQUEST))
            .isInstanceOf(AssistantNotConfiguredException.class);
        assertThat(audit.assists).isEmpty();
    }

    @Test
    void anOrganizationOutOfQuotaIsRefusedBeforeAnyModelCall() {
        ChatModel model = modelReplying(GOOD);
        AuthoringAssist assist = new AuthoringAssist(organization -> ChatClient.create(model), new FixedAccess(),
            new AssistantLimits(5, 3), audit, new CountingQuota(true), List.of(new FakeParticipant()));

        assertThatThrownBy(() -> assist.assist("acme", "rule.expression", REQUEST))
            .isInstanceOf(AssistantQuotaExceededException.class);
        verify(model, never()).call(any(Prompt.class));
    }

    @Test
    void aFailingModelServiceBecomesUnavailableAndIsAudited() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("pp-designer").build());
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("401 secret-detail"));

        assertThatThrownBy(() -> assist(model, new FakeParticipant()).assist("acme", "rule.expression", REQUEST))
            .isInstanceOf(AssistUnavailableException.class)
            .hasMessageNotContaining("secret-detail");
        assertThat(audit.assists).singleElement().extracting(AssistantAuditLog.Assist::outcome).isEqualTo("failed");
    }

    @Test
    void theAuditTrailHoldsTheTokensAndAHashOfTheInputNeverTheText() {
        ChatModel model = modelReplying(BAD, GOOD);

        assist(model, new FakeParticipant()).assist("acme", "rule.expression", REQUEST);

        assertThat(audit.assists).singleElement().satisfies(entry -> {
            assertThat(entry.task()).isEqualTo("rule.expression");
            assertThat(entry.organization()).isEqualTo("acme");
            assertThat(entry.principal()).isEqualTo("Ada");
            assertThat(entry.rounds()).isEqualTo(2);
            assertThat(entry.promptTokens()).isEqualTo(20);
            assertThat(entry.completionTokens()).isEqualTo(10);
            assertThat(entry.outcome()).isEqualTo("valid");
            assertThat(entry.inputHash()).hasSize(64).doesNotContain("positive");
        });
        assertThat(quotaRecords).containsExactly("acme:30");
    }

    @Test
    void theTasksOfAnOrganizationWithoutASubscriptionAreNone() {
        AssistantModels unconfigured = organization -> {
            throw new AssistantNotConfiguredException(organization);
        };
        AuthoringAssist assist = new AuthoringAssist(unconfigured, new FixedAccess(), new AssistantLimits(5, 3), audit,
            new CountingQuota(false), List.of(new FakeParticipant()));

        assertThat(assist.availableTasks("acme")).isEmpty();
    }

    @Test
    void onlyTheTasksTheCallerMayEditAreAvailable() {
        AuthoringAssist assist = new AuthoringAssist(organization -> ChatClient.create(modelReplying(GOOD)),
            new FixedAccess(), new AssistantLimits(5, 3), audit, new CountingQuota(false),
            List.of(new FakeParticipant(), new FakeParticipant("calculation.source", true)));

        assertThat(assist.availableTasks("acme")).containsExactly("rule.expression");
    }

    @Test
    void twoParticipantsForOneTaskAreRefusedAtStartup() {
        assertThatThrownBy(() -> new AuthoringAssist(organization -> ChatClient.create(modelReplying(GOOD)),
            new FixedAccess(), new AssistantLimits(5, 3), audit, new CountingQuota(false),
            List.of(new FakeParticipant(), new FakeParticipant())))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("rule.expression");
    }

    private AuthoringAssist assist(ChatModel model, AuthoringAssistParticipant participant) {
        return new AuthoringAssist(organization -> ChatClient.create(model), new FixedAccess(),
            new AssistantLimits(5, 3), audit, new CountingQuota(false) {
                @Override
                public void recordTurn(String organization, long tokens) {
                    quotaRecords.add(organization + ":" + tokens);
                }
            }, List.of(participant));
    }

    /** A model that gives each of the replies in turn, with 10 prompt and 5 completion tokens for each. */
    private static ChatModel modelReplying(String... replies) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("pp-designer").build());
        List<ChatResponse> responses = new ArrayList<>();
        for (String reply : replies) {
            responses.add(ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(reply))))
                .metadata(ChatResponseMetadata.builder().model("pp-designer").usage(new DefaultUsage(10, 5)).build())
                .build());
        }
        var stubbing = when(model.call(any(Prompt.class)));
        stubbing.thenReturn(responses.get(0), responses.subList(1, responses.size()).toArray(new ChatResponse[0]));
        return model;
    }

    /** A participant that finds {@code entity.totl} wrong and everything else right. */
    private static final class FakeParticipant implements AuthoringAssistParticipant {
        private final String task;
        private boolean denied;
        private boolean warn;
        private String context = "context";

        FakeParticipant() {
            this("rule.expression", false);
        }

        FakeParticipant(String task, boolean denied) {
            this.task = task;
            this.denied = denied;
        }

        @Override
        public String task() {
            return task;
        }

        @Override
        public String guide() {
            return "GUIDE-TEXT";
        }

        @Override
        public void checkAccess(String orgKey) {
            if (denied) {
                throw new SecurityException("may not edit");
            }
        }

        @Override
        public String context(String orgKey, AssistRequest request) {
            return context;
        }

        @Override
        public List<AuthoringIssue> validate(String orgKey, AssistRequest request, String candidate) {
            if (candidate.contains("totl")) {
                return List.of(AuthoringIssue.error("candidate.expression", "No attribute totl"));
            }
            return warn ? List.of(AuthoringIssue.warning("", "Always true for empty orders")) : List.of();
        }
    }

    private static class CountingQuota implements AssistantQuota {
        private final boolean exhausted;

        CountingQuota(boolean exhausted) {
            this.exhausted = exhausted;
        }

        @Override
        public void checkTurnAllowed(String organization) {
            if (exhausted) {
                throw new AssistantQuotaExceededException(organization);
            }
        }

        @Override
        public void recordTurn(String organization, long tokens) {
            // Counted only where a test asks for it.
        }
    }

    private static final class RecordingAudit implements AssistantAuditLog {
        private final List<Assist> assists = new ArrayList<>();

        @Override
        public void assist(Assist assist) {
            assists.add(assist);
        }

        @Override
        public void toolCall(ToolCall call) {
            // Tool calls are not what this test checks.
        }

        @Override
        public void turn(Turn turn) {
            // Turns are not what this test checks.
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
