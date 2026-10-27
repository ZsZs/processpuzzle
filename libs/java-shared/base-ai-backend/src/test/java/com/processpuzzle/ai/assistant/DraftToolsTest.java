package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DraftToolsTest {

    private static final String ORG = "acme";

    @Test
    void aCallerWithoutTheDesignerRoleChangesNothing() {
        InMemoryAuthoringParticipant entities = new InMemoryAuthoringParticipant("entity");
        DraftTools tools = tools(entities, new Caller(ORG, "ada-subject", "Ada", false), 10, new RecordingAudit());

        assertThat(tools.saveDraft("entity", "invoice", "{}")).startsWith("Refused");
        assertThat(entities.list(ORG)).isEmpty();
    }

    @Test
    void aSavedDraftIsReportedAndAnRefusedOneReturnsItsIssues() {
        DraftTools tools = tools(new InMemoryAuthoringParticipant("entity"), new Caller(ORG, "ada-subject", "Ada", true), 10, new RecordingAudit());

        assertThat(tools.saveDraft("entity", "invoice", "{}")).isEqualTo("Saved. No issues.");
        assertThat(tools.saveDraft("entity", "invoice", "refuse")).isEqualTo("ERROR: refused");
    }

    @Test
    void validatingAStoredDraftReportsNoIssues() {
        DraftTools tools = tools(new InMemoryAuthoringParticipant("entity"), new Caller(ORG, "ada-subject", "Ada", true), 10, new RecordingAudit());
        tools.saveDraft("entity", "invoice", "{}");

        assertThat(tools.validateDraft("entity", "invoice")).isEqualTo("No issues.");
    }

    @Test
    void theTurnStopsAtItsCapAndSaysSo() {
        DraftTools tools = tools(new InMemoryAuthoringParticipant("entity"), new Caller(ORG, "ada-subject", "Ada", true), 2, new RecordingAudit());

        tools.listDefinitions("entity");
        tools.listDefinitions("entity");

        assertThat(tools.listDefinitions("entity")).startsWith("Stop: this turn reached its limit of 2 tool calls");
    }

    @Test
    void anUnknownKindComesBackAsARefusalTheModelCanRepair() {
        DraftTools tools = tools(new InMemoryAuthoringParticipant("entity"), new Caller(ORG, "ada-subject", "Ada", true), 10, new RecordingAudit());

        assertThat(tools.listDefinitions("rule")).startsWith("Refused: Unknown definition kind 'rule'");
    }

    @Test
    void readsReturnTheStoredDraftOrSayWhyThereIsNone() {
        InMemoryAuthoringParticipant entities = new InMemoryAuthoringParticipant("entity");
        DraftTools tools = tools(entities, new Caller(ORG, "ada-subject", "Ada", true), 10, new RecordingAudit());
        tools.saveDraft("entity", "invoice", "{\"code\":\"invoice\"}");

        assertThat(tools.getDefinition("entity", "invoice")).isEqualTo("{\"code\":\"invoice\"}");
        assertThat(tools.getDefinition("entity", "absent")).isEqualTo("No draft 'absent' of kind entity");
        assertThat(tools.getSchema("entity")).isEqualTo("schema:entity");
    }

    @Test
    void everyCallIsAuditedWithItsOutcomeAndNoRawArguments() {
        RecordingAudit audit = new RecordingAudit();
        DraftTools tools = tools(new InMemoryAuthoringParticipant("entity"), new Caller(ORG, "ada-subject", "Ada", true), 10, audit);

        tools.saveDraft("entity", "invoice", "{\"secret\":\"do not log me\"}");
        tools.saveDraft("entity", "invoice", "refuse");
        tools.listDefinitions("rule");

        assertThat(audit.toolCalls).extracting(AssistantAuditLog.ToolCall::outcome)
            .containsExactly("ok", "issues", "refused");
        assertThat(audit.toolCalls).allSatisfy(call -> {
            assertThat(call.principal()).isEqualTo("Ada");
            assertThat(call.organization()).isEqualTo(ORG);
            assertThat(call.argumentsHash()).hasSize(64).doesNotContain("secret", "invoice");
        });
        assertThat(audit.toolCalls.get(0).argumentsHash()).isNotEqualTo(audit.toolCalls.get(1).argumentsHash());
    }

    @Test
    void aRefusedRoleAndAStoppedTurnAreAuditedToo() {
        RecordingAudit audit = new RecordingAudit();
        tools(new InMemoryAuthoringParticipant("entity"), new Caller(ORG, "ada-subject", "Ada", false), 10, audit)
            .listDefinitions("entity");
        DraftTools capped = tools(new InMemoryAuthoringParticipant("entity"), new Caller(ORG, "ada-subject", "Ada", true), 1, audit);
        capped.listDefinitions("entity");
        capped.listDefinitions("entity");

        assertThat(audit.toolCalls).extracting(AssistantAuditLog.ToolCall::outcome)
            .containsExactly("refused-role", "ok", "stopped-limit");
        assertThat(capped.toolCalls()).isEqualTo(2);
    }

    @Test
    void aDraftReturnedWithIssuesTooOftenIsLeftForAPersonInThatTurn() {
        RecordingAudit audit = new RecordingAudit();
        DraftTools tools = tools(new InMemoryAuthoringParticipant("entity"), new Caller(ORG, "ada-subject", "Ada", true), 10, audit);

        for (int round = 0; round < 3; round++) {
            assertThat(tools.saveDraft("entity", "invoice", "refuse")).isEqualTo("ERROR: refused");
        }

        assertThat(tools.saveDraft("entity", "invoice", "refuse"))
            .startsWith("Stop: this draft was returned with issues 3 times in this turn");
        assertThat(audit.toolCalls.get(3).outcome()).isEqualTo("stopped-repairs");
    }

    private static DraftTools tools(InMemoryAuthoringParticipant participant, Caller caller, int maxCalls,
                                    AssistantAuditLog audit) {
        return new DraftTools(new DefinitionDrafts(List.of(participant)), caller, maxCalls, 3, audit);
    }

    private static final class RecordingAudit implements AssistantAuditLog {

        private final List<ToolCall> toolCalls = new ArrayList<>();

        @Override
        public void toolCall(ToolCall call) {
            toolCalls.add(call);
        }

        @Override
        public void turn(Turn turn) {
            // Turns are audited by DesignAssistant, not by its tools.
        }
    }
}
