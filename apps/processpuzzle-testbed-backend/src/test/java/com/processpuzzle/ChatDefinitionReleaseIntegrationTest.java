package com.processpuzzle;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.baseentity.adapter.inbound.EntityDefinitionReleaseParticipant;
import com.processpuzzle.baseentity.definition.usecases.inbound.ImportEntityDefinitions;
import com.processpuzzle.chat.definition.adapters.inbound.ChatDefinitionReleaseParticipant;
import com.processpuzzle.chat.definition.usecases.inbound.ChatRoleReferences;
import com.processpuzzle.chat.definition.usecases.inbound.ImportChatDefinitions;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.state.adapter.inbound.StateMachineReleaseParticipant;
import com.processpuzzle.state.usecase.ImportStateMachineDefinitions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The release check of chat definitions against the real state machines of the host. */
@SpringBootTest
class ChatDefinitionReleaseIntegrationTest {

    private static final String ORG = "chat-release";

    private static final String ENTITIES = """
            entityDefinitions:
              - code: ticket
                name: Ticket
                status: ACTIVE
                isEmbedded: false
                componentParents: [ ]
                attributes:
                  - { code: title, name: Title, displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
                  - code: status
                    name: Status
                    displayOrder: 2
                    valueKind: ENUM
                    formControlType: DROPDOWN
                    enumValues: [ OPEN, CLOSED ]
              - code: note
                name: Note
                status: ACTIVE
                isEmbedded: false
                componentParents: [ ]
                attributes:
                  - { code: text, name: Text, displayOrder: 1, valueKind: TEXT, formControlType: TEXT_BOX, required: true }
            """;

    private static final String STATE_MACHINES = """
            stateMachines:
              - entityName: ticket
                name: Ticket State Machine
                stateAttributeKey: status
                initialStateKey: OPEN
                states:
                  - { key: OPEN, name: Open, isFinal: false, isLocked: false }
                  - { key: CLOSED, name: Closed, isFinal: true, isLocked: false }
                transitions:
                  - { key: close, name: Close, sourceStateKey: OPEN, targetStateKey: CLOSED, triggerKey: close }
            """;

    @Autowired private ImportEntityDefinitions importEntities;
    @Autowired private EntityDefinitionReleaseParticipant entityRelease;
    @Autowired private ImportStateMachineDefinitions importStateMachines;
    @Autowired private StateMachineReleaseParticipant stateRelease;
    @Autowired private ImportChatDefinitions importChats;
    @Autowired private ChatDefinitionReleaseParticipant chatRelease;
    @Autowired private ChatRoleReferences roleReferences;

    private final ReleaseCandidate candidate = new ReleaseCandidate() {
        @Override
        public boolean manages(String kind) {
            return true;
        }

        @Override
        public boolean exists(String kind, String key) {
            return true;
        }
    };

    @BeforeEach
    void seed() throws IOException {
        assertThat(importEntities.execute(ORG, stream(ENTITIES)).errors()).isEmpty();
        entityRelease.release(ORG);
        assertThat(importStateMachines.execute(ORG, stream(STATE_MACHINES)).errors()).isEmpty();
        stateRelease.release(ORG);
    }

    private List<ReleaseProblem> checkChats(String yaml) throws IOException {
        importChats.removeAll(ORG);
        assertThat(importChats.execute(ORG, stream(yaml), false).errors()).isEmpty();
        return chatRelease.check(ORG, candidate);
    }

    @Test
    void aStateBoundChatOnAStateOfTheMachineMayBeReleased() throws IOException {
        assertThat(checkChats("""
                chat-definitions:
                  - { key: ticket-chat, name: Ticket chat, subjectType: ENTITY, subjectDefinition: ticket, lifecycle: STATE_BOUND, writableStates: [ OPEN ] }
                """)).isEmpty();
    }

    @Test
    void aStateBoundChatOnAStateTheMachineLacksIsRefused() throws IOException {
        assertThat(checkChats("""
                chat-definitions:
                  - { key: ticket-chat, name: Ticket chat, subjectType: ENTITY, subjectDefinition: ticket, lifecycle: STATE_BOUND, writableStates: [ REOPENED ] }
                """)).singleElement().satisfies(problem -> {
                    assertThat(problem.errorId()).isEqualTo("chat.release.unknown-state");
                    assertThat(problem.key()).isEqualTo("ticket-chat");
                });
    }

    @Test
    void aStateBoundChatOnAnEntityWithoutAMachineIsRefused() throws IOException {
        assertThat(checkChats("""
                chat-definitions:
                  - { key: note-chat, name: Note chat, subjectType: ENTITY, subjectDefinition: note, lifecycle: STATE_BOUND, writableStates: [ OPEN ] }
                """)).singleElement().extracting(ReleaseProblem::errorId).isEqualTo("chat.release.no-state-machine");
    }

    @Test
    void theRolesAChatNamesReachTheRoleCatalog() throws IOException {
        importChats.removeAll(ORG);
        importChats.execute(ORG, stream("""
                chat-definitions:
                  - { key: ticket-chat, name: Ticket chat, subjectType: ENTITY, subjectDefinition: ticket, readRoles: [ agent ] }
                """), false);

        assertThat(roleReferences.referencedRoles(ORG)).containsKey("agent");
        assertThat(chatRelease.kind()).isEqualTo(DefinitionKinds.CHAT);
    }

    private static ByteArrayInputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }
}
