package com.processpuzzle.chat.definition.adapters.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.domain.ChatDefinitionTest;
import com.processpuzzle.chat.definition.usecases.inbound.ImportChatDefinitions;
import com.processpuzzle.chat.definition.usecases.inbound.ImportOutcome;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** How a Business Starter's chat file is imported, replaced and compared. */
class ChatDefinitionImportParticipantTest {

    private static final String ORG = "org-1";
    private static final byte[] FILE = "chat-definitions: []".getBytes(StandardCharsets.UTF_8);

    private ImportChatDefinitions importer;
    private ChatDefinitionRepository repository;
    private ChatDefinitionImportParticipant participant;

    @BeforeEach
    void setUp() {
        importer = mock(ImportChatDefinitions.class);
        repository = mock(ChatDefinitionRepository.class);
        participant = new ChatDefinitionImportParticipant(importer, repository);
    }

    @Test
    void isTheChatKindAndImportsAfterTheEntitiesAndTheStates() {
        assertThat(participant.kind()).isEqualTo(DefinitionKinds.CHAT);
        assertThat(participant.order()).isEqualTo(DefinitionKinds.CHAT_ORDER).isGreaterThan(DefinitionKinds.STATE_ORDER);
    }

    @Test
    void importsDraftsOnlyBecauseTheStarterIsReleasedAsAWhole() throws IOException {
        when(importer.execute(eq(ORG), any(), eq(false))).thenReturn(new ImportOutcome(1, 0, List.of(), List.of("order-discussion"), List.of()));

        ParticipantResult result = participant.apply(ORG, "chats.yaml", FILE);

        assertThat(result.isRejected()).isFalse();
        assertThat(result.items()).containsExactly(new ImportedItem("order-discussion", ImportedItem.Action.CREATE));
    }

    @Test
    void tellsANewDefinitionFromOneThatReplacedADraft() throws IOException {
        when(importer.execute(eq(ORG), any(), eq(false)))
                .thenReturn(new ImportOutcome(1, 1, List.of(), List.of("new-chat", "old-chat"), List.of("old-chat")));

        assertThat(participant.apply(ORG, "chats.yaml", FILE).items()).containsExactly(
                new ImportedItem("new-chat", ImportedItem.Action.CREATE), new ImportedItem("old-chat", ImportedItem.Action.UPDATE));
    }

    @Test
    void refusesTheFileWithEveryProblemNamingTheFile() throws IOException {
        when(importer.execute(eq(ORG), any(), eq(false))).thenReturn(ImportOutcome.rejected(List.of("'Bad Key': 'key' must be lower-case", "Duplicate key")));

        ParticipantResult result = participant.apply(ORG, "chats.yaml", FILE);

        assertThat(result.isRejected()).isTrue();
        assertThat(result.errors()).containsExactly("chats.yaml: 'Bad Key': 'key' must be lower-case", "chats.yaml: Duplicate key");
        assertThat(result.items()).isEmpty();
    }

    @Test
    void refusesAFileThatCannotBeReadInsteadOfFailingTheWholeImport() throws IOException {
        when(importer.execute(eq(ORG), any(), eq(false))).thenThrow(new IOException("bad yaml"));

        ParticipantResult result = participant.apply(ORG, "chats.yaml", FILE);

        assertThat(result.errors()).singleElement().asString().startsWith("chats.yaml: not a readable chat definition file").contains("bad yaml");
    }

    @Test
    void removesAllByDeletingTheDrafts() {
        when(importer.removeAll(ORG)).thenReturn(List.of("a-chat", "b-chat"));

        assertThat(participant.removeAll(ORG)).containsExactly("a-chat", "b-chat");
    }

    @Test
    void fingerprintsTheRequestedDraftsAndOnlyThose() {
        ChatDefinition wanted = ChatDefinitionTest.definition("wanted");
        ChatDefinition other = ChatDefinitionTest.definition("other");
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(ORG, Channel.DRAFT)).thenReturn(List.of(other, wanted));

        assertThat(participant.fingerprints(ORG, Set.of("wanted"))).containsOnlyKeys("wanted").containsEntry("wanted", wanted.fingerprint());
    }

    @Test
    void aFingerprintChangesWithWhatAnAuthorEditsAndNotWithWhatTheSystemMoves() {
        ChatDefinition definition = ChatDefinitionTest.definition("order-discussion");
        String before = definition.fingerprint();

        definition.setVersion(9L);
        definition.setCreatedAt(java.time.Instant.now());
        assertThat(definition.fingerprint()).isEqualTo(before);

        definition.setReadRoles(new ArrayList<>(List.of("sailor")));
        assertThat(definition.fingerprint()).isNotEqualTo(before);
    }

    @Test
    void upgradesChatByChat_keepingTheRestOfTheFileOut() throws IOException {
        byte[] file = "chat-definitions:\n  - key: order-discussion\n  - key: other\n".getBytes(StandardCharsets.UTF_8);
        when(importer.execute(eq(ORG), any(), eq(false))).thenReturn(new ImportOutcome(1, 0, List.of(), List.of("other"), List.of()));

        assertThat(participant.supportsUpgrade()).isTrue();
        assertThat(participant.keysIn("chats.yaml", file)).containsExactly("order-discussion", "other");
        assertThat(participant.applyOnly(ORG, "chats.yaml", file, java.util.Set.of("other")).items())
                .containsExactly(new ImportedItem("other", ImportedItem.Action.CREATE));
        assertThat(participant.applyOnly(ORG, "chats.yaml", file, java.util.Set.of("absent")).items()).isEmpty();
    }

    @Test
    void removingDrafts_deletesTheDraftRows_andKeepsWhatHasNone() {
        ChatDefinition draft = mock(ChatDefinition.class);
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(ORG, "old", com.processpuzzle.core.definition.Channel.DRAFT))
                .thenReturn(java.util.Optional.of(draft));
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(ORG, "ghost", com.processpuzzle.core.definition.Channel.DRAFT))
                .thenReturn(java.util.Optional.empty());

        var removal = participant.removeDrafts(ORG, new java.util.LinkedHashSet<>(List.of("old", "ghost")));

        assertThat(removal.removed()).containsExactly("old");
        assertThat(removal.refused()).containsKey("ghost");
        verify(repository).delete(draft);
    }
}
