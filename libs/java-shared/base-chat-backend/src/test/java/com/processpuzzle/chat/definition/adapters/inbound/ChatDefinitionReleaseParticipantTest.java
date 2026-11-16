package com.processpuzzle.chat.definition.adapters.inbound;

import static com.processpuzzle.chat.conversation.ConversationFixtures.provider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.domain.ChatDefinitionTest;
import com.processpuzzle.chat.definition.domain.ChatLifecycle;
import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.chat.definition.usecases.outbound.SubjectStates;
import com.processpuzzle.chat.definition.usecases.outbound.SubjectStatesPort;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** What a release does with chat definitions, and what it refuses. */
class ChatDefinitionReleaseParticipantTest {

    private static final String ORG = "org-1";

    private ChatDefinitionRepository repository;
    private SubjectStatesPort states;
    private ReleaseCandidate candidate;
    private ChatDefinitionReleaseParticipant participant;

    @BeforeEach
    void setUp() {
        repository = mock(ChatDefinitionRepository.class);
        states = mock(SubjectStatesPort.class);
        candidate = mock(ReleaseCandidate.class);
        when(candidate.manages(DefinitionKinds.ENTITY)).thenReturn(true);
        when(candidate.exists(DefinitionKinds.ENTITY, "order")).thenReturn(true);
        participant = new ChatDefinitionReleaseParticipant(repository, provider(states));
    }

    private static ChatDefinition draft(String key) {
        return ChatDefinitionTest.definition(key);
    }

    private static ChatDefinition stateBound(String key, String... writable) {
        ChatDefinition definition = draft(key);
        definition.setLifecycle(ChatLifecycle.STATE_BOUND);
        definition.setWritableStates(new ArrayList<>(List.of(writable)));
        return definition;
    }

    private void drafts(ChatDefinition... drafts) {
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(ORG, Channel.DRAFT)).thenReturn(List.of(drafts));
    }

    private void released(ChatDefinition... released) {
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(ORG, Channel.RELEASED)).thenReturn(List.of(released));
    }

    @Test
    void isTheChatKindAndComesAfterTheEntitiesAndTheStates() {
        assertThat(participant.kind()).isEqualTo("chat");
        assertThat(participant.order()).isGreaterThan(DefinitionKinds.STATE_ORDER).isLessThan(DefinitionKinds.WORKFLOW_ORDER);
    }

    @Test
    void namesEveryDraftWhetherOrNotItChanged() {
        drafts(draft("b-chat"), draft("a-chat"));

        assertThat(participant.drafts(ORG)).containsExactlyInAnyOrder("a-chat", "b-chat");
    }

    @Test
    void reportsWhatAReleaseWouldAddChangeAndRemove() {
        ChatDefinition unchanged = draft("same");
        ChatDefinition edited = draft("edited");
        edited.setName("Renamed since");
        drafts(unchanged, edited, draft("new-one"));
        released(ChatDefinition.releasedCopyOf(unchanged), ChatDefinition.releasedCopyOf(draft("edited")), ChatDefinition.releasedCopyOf(draft("gone")));

        List<PendingChange> pending = participant.pending(ORG);

        assertThat(pending).extracting(PendingChange::key, PendingChange::change).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("new-one", PendingChange.Change.ADDED),
                org.assertj.core.groups.Tuple.tuple("edited", PendingChange.Change.CHANGED),
                org.assertj.core.groups.Tuple.tuple("gone", PendingChange.Change.REMOVED));
    }

    @Test
    void aVersionBumpOrANewTimestampIsNotAChange() {
        ChatDefinition edited = draft("same");
        ChatDefinition released = ChatDefinition.releasedCopyOf(edited);
        edited.setVersion(5L);
        edited.setCreatedAt(java.time.Instant.now());
        drafts(edited);
        released(released);

        assertThat(participant.pending(ORG)).isEmpty();
    }

    @Test
    void everyChatRequiresTheEntityItIsAbout() {
        drafts(draft("order-discussion"));

        assertThat(participant.references(ORG)).containsExactly(
                DefinitionReference.required("chat", "order-discussion", DefinitionKinds.ENTITY, "order"));
    }

    // --- the states of a STATE_BOUND chat

    @Test
    void aStateBoundChatWhoseWritableStatesAllExistIsFine() {
        drafts(stateBound("order-chat", "DRAFT", "CONFIRMED"));
        when(states.statesOf(ORG, SubjectType.ENTITY, "order", Channel.DRAFT)).thenReturn(Optional.of(new SubjectStates(List.of("DRAFT", "CONFIRMED", "SHIPPED"))));

        assertThat(participant.check(ORG, candidate)).isEmpty();
    }

    @Test
    void aStateBoundChatNamingAStateTheEntityDoesNotHaveIsRefused() {
        drafts(stateBound("order-chat", "DRAFT", "SHPPED"));
        when(states.statesOf(ORG, SubjectType.ENTITY, "order", Channel.DRAFT)).thenReturn(Optional.of(new SubjectStates(List.of("DRAFT", "SHIPPED"))));

        List<ReleaseProblem> problems = participant.check(ORG, candidate);

        assertThat(problems).singleElement().satisfies(problem -> {
            assertThat(problem.isError()).isTrue();
            assertThat(problem.kind()).isEqualTo("chat");
            assertThat(problem.key()).isEqualTo("order-chat");
            assertThat(problem.errorId()).isEqualTo("chat.release.unknown-state");
            assertThat(problem.message()).contains("SHPPED");
        });
    }

    @Test
    void everyUnknownStateIsReported() {
        drafts(stateBound("order-chat", "A", "B", "DRAFT"));
        when(states.statesOf(ORG, SubjectType.ENTITY, "order", Channel.DRAFT)).thenReturn(Optional.of(new SubjectStates(List.of("DRAFT"))));

        assertThat(participant.check(ORG, candidate)).extracting(ReleaseProblem::errorId).containsExactly("chat.release.unknown-state", "chat.release.unknown-state");
    }

    @Test
    void aStateBoundChatOnAnEntityNoStateMachineGovernsIsRefused() {
        drafts(stateBound("order-chat", "DRAFT"));
        when(states.statesOf(ORG, SubjectType.ENTITY, "order", Channel.DRAFT)).thenReturn(Optional.of(SubjectStates.NONE));

        assertThat(participant.check(ORG, candidate)).singleElement().satisfies(problem -> {
            assertThat(problem.isError()).isTrue();
            assertThat(problem.errorId()).isEqualTo("chat.release.no-state-machine");
        });
    }

    @Test
    void aHostThatCannotSayWhichStatesThereAreRefusesNothing() {
        drafts(stateBound("order-chat", "ANYTHING"));
        when(states.statesOf(ORG, SubjectType.ENTITY, "order", Channel.DRAFT)).thenReturn(Optional.empty());

        assertThat(participant.check(ORG, candidate)).isEmpty();
    }

    @Test
    void aChatThatIsNotStateBoundNeverAsksAboutStates() {
        drafts(draft("order-discussion"));

        assertThat(participant.check(ORG, candidate)).isEmpty();
        verify(states, never()).statesOf(any(), any(), any(), any());
    }

    @Test
    void doesNotRepeatAMissingEntityAsAStatesProblem() {
        ChatDefinition lost = stateBound("order-chat", "DRAFT");
        lost.setSubjectDefinition("vanished");
        drafts(lost);
        when(candidate.exists(DefinitionKinds.ENTITY, "vanished")).thenReturn(false);
        when(states.statesOf(ORG, SubjectType.ENTITY, "vanished", Channel.DRAFT)).thenReturn(Optional.of(SubjectStates.NONE));

        assertThat(participant.check(ORG, candidate)).as("the reference already refuses it").isEmpty();
    }

    @Test
    void withoutAnAdapterNothingIsRefused() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SubjectStatesPort> none = mock(ObjectProvider.class);
        when(none.getIfUnique(any())).thenAnswer(invocation -> ((java.util.function.Supplier<SubjectStatesPort>) invocation.getArgument(0)).get());
        ChatDefinitionReleaseParticipant unaware = new ChatDefinitionReleaseParticipant(repository, none);
        drafts(stateBound("order-chat", "ANYTHING"));

        assertThat(unaware.check(ORG, candidate)).isEmpty();
    }

    // --- releasing

    @Test
    void releasingCopiesANewDraftOntoAReleasedRow() {
        ChatDefinition fresh = draft("order-discussion");
        drafts(fresh);
        released();

        participant.release(ORG);

        ArgumentCaptor<ChatDefinition> saved = ArgumentCaptor.forClass(ChatDefinition.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getChannel()).isEqualTo(Channel.RELEASED);
        assertThat(saved.getValue().getDefinitionKey()).isEqualTo("order-discussion");
        assertThat(saved.getValue().getVersion()).isNull();
    }

    @Test
    void releasingReplacesTheContentOfAnExistingReleasedRow() {
        ChatDefinition edited = draft("order-discussion");
        ChatDefinition current = ChatDefinition.releasedCopyOf(draft("order-discussion"));
        edited.setName("Renamed");
        edited.setReadRoles(new ArrayList<>(List.of("sailor")));
        drafts(edited);
        released(current);

        participant.release(ORG);

        verify(repository).save(current);
        assertThat(current.getName()).isEqualTo("Renamed");
        assertThat(current.getReadRoles()).containsExactly("sailor");
        assertThat(current.getChannel()).isEqualTo(Channel.RELEASED);
    }

    @Test
    void releasingRemovesTheReleasedRowsWhoseDraftWasDeleted() {
        ChatDefinition kept = draft("kept");
        ChatDefinition withdrawn = ChatDefinition.releasedCopyOf(draft("withdrawn"));
        drafts(kept);
        released(ChatDefinition.releasedCopyOf(kept), withdrawn);

        participant.release(ORG);

        verify(repository).deleteAll(List.of(withdrawn));
    }
}
