package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.DefinitionAuthoringParticipant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefinitionDraftsTest {

    private static final String ORG = "acme";

    @Test
    void kindsAreTheParticipantKindsInNameOrder() {
        DefinitionDrafts drafts = new DefinitionDrafts(List.of(
            new InMemoryAuthoringParticipant("state"), new InMemoryAuthoringParticipant("entity")));

        assertThat(drafts.kinds()).containsExactly("entity", "state");
    }

    @Test
    void aDraftIsSavedAndReadBackThroughTheKindsParticipant() {
        DefinitionDrafts drafts = new DefinitionDrafts(List.of(
            new InMemoryAuthoringParticipant("entity"), new InMemoryAuthoringParticipant("state")));

        assertThat(drafts.save(ORG, "entity", "invoice", "{\"code\":\"invoice\"}")).isEmpty();

        assertThat(drafts.list(ORG, "entity")).containsExactly("invoice");
        assertThat(drafts.get(ORG, "entity", "invoice")).contains("{\"code\":\"invoice\"}");
        assertThat(drafts.list(ORG, "state")).isEmpty();
    }

    @Test
    void issuesComeBackWhenTheParticipantRefusesTheDraft() {
        DefinitionDrafts drafts = new DefinitionDrafts(List.of(new InMemoryAuthoringParticipant("entity")));

        List<AuthoringIssue> issues = drafts.save(ORG, "entity", "invoice", "refuse");

        assertThat(issues).containsExactly(AuthoringIssue.error("", "refused"));
        assertThat(drafts.get(ORG, "entity", "invoice")).isEmpty();
    }

    @Test
    void validateAndSchemaAreDelegated() {
        DefinitionDrafts drafts = new DefinitionDrafts(List.of(new InMemoryAuthoringParticipant("entity")));

        assertThat(drafts.schema("entity")).isEqualTo("schema:entity");
        assertThat(drafts.validate(ORG, "entity", "invoice")).isEmpty();
    }

    @Test
    void anUnknownKindIsRefusedAndTheMessageNamesTheKindsThatExist() {
        DefinitionDrafts drafts = new DefinitionDrafts(List.of(new InMemoryAuthoringParticipant("entity")));

        assertThatThrownBy(() -> drafts.list(ORG, "rule"))
            .isInstanceOf(UnknownDefinitionKindException.class)
            .hasMessageContaining("'rule'")
            .hasMessageContaining("[entity]");
    }

    @Test
    void twoParticipantsForOneKindAreAConfigurationError() {
        List<DefinitionAuthoringParticipant> twins = List.of(
            new InMemoryAuthoringParticipant("entity"), new InMemoryAuthoringParticipant("entity"));

        assertThatThrownBy(() -> new DefinitionDrafts(twins))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("'entity'");
    }
}
