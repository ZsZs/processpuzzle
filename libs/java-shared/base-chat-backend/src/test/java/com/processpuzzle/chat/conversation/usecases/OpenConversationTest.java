package com.processpuzzle.chat.conversation.usecases;

import static com.processpuzzle.chat.conversation.ConversationFixtures.DEFINITION_KEY;
import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.SUBJECT_ID;
import static com.processpuzzle.chat.conversation.ConversationFixtures.provider;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.ConversationStatus;
import com.processpuzzle.chat.conversation.domain.ConversationView;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationConflictException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.chat.conversation.usecases.inbound.ConversationPlatformFacts;
import com.processpuzzle.chat.conversation.usecases.inbound.OpenConversation;
import com.processpuzzle.chat.conversation.usecases.inbound.ReadMarkers;
import com.processpuzzle.chat.conversation.usecases.inbound.ResolveAccess;
import com.processpuzzle.chat.conversation.usecases.outbound.SubjectInfo;
import com.processpuzzle.chat.conversation.usecases.outbound.SubjectResolverPort;
import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionTest;
import com.processpuzzle.chat.definition.domain.ChatLifecycle;
import com.processpuzzle.chat.definition.domain.Multiplicity;
import com.processpuzzle.chat.definition.usecases.exception.ChatDefinitionNotFoundException;
import com.processpuzzle.chat.definition.usecases.inbound.FindChatDefinition;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class OpenConversationTest {

    private ConversationRepository conversations;
    private FindChatDefinition definitions;
    private SubjectResolverPort subjects;
    private ResolveAccess access;
    private OrganizationGuard guard;
    private CurrentPrincipal principal;
    private ConversationPlatformFacts facts;
    private ReadMarkers markers;
    private OpenConversation open;
    private ChatDefinition definition;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        definitions = mock(FindChatDefinition.class);
        subjects = mock(SubjectResolverPort.class);
        access = mock(ResolveAccess.class);
        guard = mock(OrganizationGuard.class);
        principal = mock(CurrentPrincipal.class);
        facts = mock(ConversationPlatformFacts.class);
        markers = mock(ReadMarkers.class);
        open = new OpenConversation(conversations, definitions, provider(subjects), access, guard, principal, facts, markers);

        definition = ChatDefinitionTest.definition(DEFINITION_KEY);
        when(definitions.released(ORG, DEFINITION_KEY)).thenReturn(Optional.of(definition));
        when(subjects.resolve(ORG, definition.getSubjectType(), "order", SUBJECT_ID)).thenReturn(Optional.of(new SubjectInfo(null, "Running")));
        when(principal.subject()).thenReturn(Optional.of("sub-caller"));
        when(principal.displayName()).thenReturn(Optional.of("Cora Caller"));
        when(access.resolve(any(), any(), any())).thenReturn(new ConversationAccess(true, true, true, true));
        when(conversations.saveAndFlush(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void theOpenerFollowsTheConversationFromTheStartWithNothingRead() {
        ConversationView view = open.open(ORG, DEFINITION_KEY, SUBJECT_ID, null);

        verify(markers).advanceTo(ORG, view.conversation().getId(), "sub-caller", 0);
        assertThat(view.lastReadSeq()).isZero();
        assertThat(view.following()).isTrue();
        assertThat(view.unread()).isZero();
    }

    @Test
    void aConversationThatWasNotOpenedIsNotFollowed() {
        when(conversations.existsByOrgKeyAndBindingKey(ORG, DEFINITION_KEY + "|" + SUBJECT_ID)).thenReturn(true);

        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, null)).isInstanceOf(ConversationConflictException.class);
        verify(markers, never()).advanceTo(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void opensTheOneConversationOfASingleDefinitionBoundToItsSubject() {
        ConversationView view = open.open(ORG, DEFINITION_KEY, SUBJECT_ID, "ignored for a single chat");

        Conversation conversation = view.conversation();
        assertThat(conversation.getOrgKey()).isEqualTo(ORG);
        assertThat(conversation.getDefinitionKey()).isEqualTo(DEFINITION_KEY);
        assertThat(conversation.getSubjectDefinition()).isEqualTo("order");
        assertThat(conversation.getSubjectId()).isEqualTo(SUBJECT_ID);
        assertThat(conversation.getTitle()).isNull();
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.OPEN);
        assertThat(conversation.getBindingKey()).isEqualTo(DEFINITION_KEY + "|" + SUBJECT_ID);
        assertThat(conversation.getCreatedBy()).isEqualTo("sub-caller");
        assertThat(conversation.getCreatedByName()).isEqualTo("Cora Caller");
        assertThat(conversation.getCreatedAt()).isNotNull();
        assertThat(view.access().canWrite()).isTrue();
        verify(guard).requireAccess(ORG);
        verify(facts).opened(conversation);
    }

    @Test
    void aManyDefinitionOpensTitledTopicsBoundToTheirNormalizedTitle() {
        definition.setMultiplicity(Multiplicity.MANY);

        Conversation conversation = open.open(ORG, DEFINITION_KEY, SUBJECT_ID, "  Protest   of race 3  ").conversation();

        assertThat(conversation.getTitle()).isEqualTo("Protest   of race 3");
        assertThat(conversation.getBindingKey()).isEqualTo(DEFINITION_KEY + "|" + SUBJECT_ID + "|protest of race 3");
    }

    @Test
    void aTopicWithATitleTheSubjectAlreadyHasIsAConflictWhateverItsCase() {
        definition.setMultiplicity(Multiplicity.MANY);
        when(conversations.existsByOrgKeyAndBindingKey(ORG, DEFINITION_KEY + "|" + SUBJECT_ID + "|delivery")).thenReturn(true);

        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, " DELIVERY "))
                .isInstanceOf(ConversationConflictException.class).hasMessageContaining("topic titled");
        verify(conversations, never()).saveAndFlush(any());
        verify(facts, never()).opened(any());
    }

    @Test
    void aTopicNeedsATitleOfReasonableLength() {
        definition.setMultiplicity(Multiplicity.MANY);

        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, "  "))
                .isInstanceOf(InvalidChatRequestException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, null))
                .isInstanceOf(InvalidChatRequestException.class);
        String tooLong = "x".repeat(201);
        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, tooLong))
                .isInstanceOf(InvalidChatRequestException.class).hasMessageContaining("200");
        verify(conversations, never()).saveAndFlush(any());
    }

    @Test
    void aSecondConversationOfASingleDefinitionIsAConflict() {
        when(conversations.existsByOrgKeyAndBindingKey(ORG, DEFINITION_KEY + "|" + SUBJECT_ID)).thenReturn(true);

        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, null))
                .isInstanceOf(ConversationConflictException.class).hasMessageContaining("already has");
        verify(conversations, never()).saveAndFlush(any());
        verify(facts, never()).opened(any());
    }

    @Test
    void aConcurrentOpenThatLostTheRaceIsAlsoAConflict() {
        when(conversations.saveAndFlush(any(Conversation.class))).thenThrow(new DataIntegrityViolationException("uq_chat_conversation_binding"));

        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, null)).isInstanceOf(ConversationConflictException.class);
        verify(facts, never()).opened(any());
    }

    @Test
    void anAutoCreatedChatIsNotOpenedByHand() {
        definition.setLifecycle(ChatLifecycle.AUTO_CREATE);

        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, null))
                .isInstanceOf(ConversationConflictException.class).hasMessageContaining("created together with their subject");
    }

    @Test
    void aCallerWhoMayNotReadTheChatIsToldItDoesNotExist() {
        when(access.resolve(any(), any(), any())).thenReturn(new ConversationAccess(false, false, false, true));

        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, null)).isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void aReaderWithoutACreateRoleIsRefused() {
        when(access.resolve(any(), any(), any())).thenReturn(new ConversationAccess(true, true, false, true));

        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, null))
                .isInstanceOf(ChatAccessDeniedException.class).hasMessageContaining(DEFINITION_KEY);
    }

    @Test
    void anUnknownDefinitionOrSubjectIsNotFound() {
        assertThatThrownBy(() -> open.open(ORG, "nope", SUBJECT_ID, null)).isInstanceOf(ChatDefinitionNotFoundException.class);
        assertThatThrownBy(() -> open.open(ORG, null, SUBJECT_ID, null)).isInstanceOf(ChatDefinitionNotFoundException.class);

        when(subjects.resolve(ORG, definition.getSubjectType(), "order", "gone")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, "gone", null)).isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void needsASubjectIdAndASignedInUser() {
        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, " ", null)).isInstanceOf(InvalidChatRequestException.class);

        when(principal.subject()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, null)).isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void aNonMemberOfTheOrganizationIsRefusedBeforeAnythingIsLookedUp() {
        doThrow(new OrganizationAccessDeniedException(ORG)).when(guard).requireAccess(ORG);

        assertThatThrownBy(() -> open.open(ORG, DEFINITION_KEY, SUBJECT_ID, null)).isInstanceOf(OrganizationAccessDeniedException.class);
        verify(definitions, never()).released(any(), any());
    }

    @Test
    void passesTheOpenedConversationToTheFacts() {
        open.open(ORG, DEFINITION_KEY, SUBJECT_ID, null);

        ArgumentCaptor<Conversation> captured = ArgumentCaptor.forClass(Conversation.class);
        verify(facts).opened(captured.capture());
        assertThat(captured.getValue().getSubjectId()).isEqualTo(SUBJECT_ID);
    }
}
