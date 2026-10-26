package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DraftToolsTest {

    private static final String ORG = "acme";

    @Test
    void aCallerWithoutTheDesignerRoleChangesNothing() {
        InMemoryAuthoringParticipant entities = new InMemoryAuthoringParticipant("entity");
        DraftTools tools = tools(entities, new FixedAccess(ORG, false), 10);

        assertThat(tools.saveDraft("entity", "invoice", "{}")).startsWith("Refused");
        assertThat(entities.list(ORG)).isEmpty();
    }

    @Test
    void aSavedDraftIsReportedAndAnRefusedOneReturnsItsIssues() {
        DraftTools tools = tools(new InMemoryAuthoringParticipant("entity"), new FixedAccess(ORG, true), 10);

        assertThat(tools.saveDraft("entity", "invoice", "{}")).isEqualTo("Saved. No issues.");
        assertThat(tools.saveDraft("entity", "invoice", "refuse")).isEqualTo("ERROR: refused");
    }

    @Test
    void theTurnStopsAtItsCapAndSaysSo() {
        DraftTools tools = tools(new InMemoryAuthoringParticipant("entity"), new FixedAccess(ORG, true), 2);

        tools.listDefinitions("entity");
        tools.listDefinitions("entity");

        assertThat(tools.listDefinitions("entity")).startsWith("Stop: this turn reached its limit of 2 tool calls");
    }

    @Test
    void anUnknownKindComesBackAsARefusalTheModelCanRepair() {
        DraftTools tools = tools(new InMemoryAuthoringParticipant("entity"), new FixedAccess(ORG, true), 10);

        assertThat(tools.listDefinitions("rule")).startsWith("Refused: Unknown definition kind 'rule'");
    }

    @Test
    void readsReturnTheStoredDraftOrSayWhyThereIsNone() {
        InMemoryAuthoringParticipant entities = new InMemoryAuthoringParticipant("entity");
        DraftTools tools = tools(entities, new FixedAccess(ORG, true), 10);
        tools.saveDraft("entity", "invoice", "{\"code\":\"invoice\"}");

        assertThat(tools.getDefinition("entity", "invoice")).isEqualTo("{\"code\":\"invoice\"}");
        assertThat(tools.getDefinition("entity", "absent")).isEqualTo("No draft 'absent' of kind entity");
        assertThat(tools.getSchema("entity")).isEqualTo("schema:entity");
    }

    private static DraftTools tools(InMemoryAuthoringParticipant participant, AssistantAccess access, int maxCalls) {
        return new DraftTools(new DefinitionDrafts(List.of(participant)), access, maxCalls);
    }

    private record FixedAccess(String organization, boolean designer) implements AssistantAccess {

        @Override
        public String currentOrganization() {
            return organization;
        }

        @Override
        public boolean mayDesign() {
            return designer;
        }
    }
}
