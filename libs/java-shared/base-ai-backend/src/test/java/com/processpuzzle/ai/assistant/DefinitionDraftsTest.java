package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.DefinitionAuthoringParticipant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class DefinitionDraftsTest {

    private static final String ORG = "acme";

    @Test
    void kindsAreTheParticipantKindsInNameOrder() {
        DefinitionDrafts drafts = new DefinitionDrafts(List.of(new InMemory("state"), new InMemory("entity")));

        assertThat(drafts.kinds()).containsExactly("entity", "state");
    }

    @Test
    void aDraftIsSavedAndReadBackThroughTheKindsParticipant() {
        InMemory entities = new InMemory("entity");
        DefinitionDrafts drafts = new DefinitionDrafts(List.of(entities, new InMemory("state")));

        assertThat(drafts.save(ORG, "entity", "invoice", "{\"code\":\"invoice\"}")).isEmpty();

        assertThat(drafts.list(ORG, "entity")).containsExactly("invoice");
        assertThat(drafts.get(ORG, "entity", "invoice")).contains("{\"code\":\"invoice\"}");
        assertThat(drafts.list(ORG, "state")).isEmpty();
    }

    @Test
    void issuesComeBackWhenTheParticipantRefusesTheDraft() {
        DefinitionDrafts drafts = new DefinitionDrafts(List.of(new InMemory("entity")));

        List<AuthoringIssue> issues = drafts.save(ORG, "entity", "invoice", "refuse");

        assertThat(issues).containsExactly(AuthoringIssue.error("", "refused"));
        assertThat(drafts.get(ORG, "entity", "invoice")).isEmpty();
    }

    @Test
    void validateAndSchemaAreDelegated() {
        DefinitionDrafts drafts = new DefinitionDrafts(List.of(new InMemory("entity")));

        assertThat(drafts.schema("entity")).isEqualTo("schema:entity");
        assertThat(drafts.validate(ORG, "entity", "invoice")).isEmpty();
    }

    @Test
    void anUnknownKindIsRefusedAndTheMessageNamesTheKindsThatExist() {
        DefinitionDrafts drafts = new DefinitionDrafts(List.of(new InMemory("entity")));

        assertThatThrownBy(() -> drafts.list(ORG, "rule"))
            .isInstanceOf(UnknownDefinitionKindException.class)
            .hasMessageContaining("'rule'")
            .hasMessageContaining("[entity]");
    }

    @Test
    void twoParticipantsForOneKindAreAConfigurationError() {
        List<DefinitionAuthoringParticipant> twins = List.of(new InMemory("entity"), new InMemory("entity"));

        assertThatThrownBy(() -> new DefinitionDrafts(twins))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("'entity'");
    }

    /** Keeps drafts in memory, keyed by the definition key; refuses any draft whose JSON is {@code refuse}. */
    private static final class InMemory implements DefinitionAuthoringParticipant {

        private final String kind;
        private final Map<String, String> drafts = new TreeMap<>();

        InMemory(String kind) {
            this.kind = kind;
        }

        @Override
        public String kind() {
            return kind;
        }

        @Override
        public String schema() {
            return "schema:" + kind;
        }

        @Override
        public List<String> list(String orgKey) {
            return new ArrayList<>(drafts.keySet());
        }

        @Override
        public Optional<String> get(String orgKey, String key) {
            return Optional.ofNullable(drafts.get(key));
        }

        @Override
        public List<AuthoringIssue> saveDraft(String orgKey, String key, String json) {
            if ("refuse".equals(json)) {
                return List.of(AuthoringIssue.error("", "refused"));
            }
            drafts.put(key, json);
            return List.of();
        }

        @Override
        public List<AuthoringIssue> validate(String orgKey, String key) {
            return List.of();
        }
    }
}
