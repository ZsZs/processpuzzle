package com.processpuzzle.chat.conversation.usecases;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.SUBJECT_ID;
import static com.processpuzzle.chat.conversation.ConversationFixtures.conversation;
import static com.processpuzzle.chat.conversation.ConversationFixtures.message;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.ConversationView;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.inbound.FindConversations;
import com.processpuzzle.chat.conversation.usecases.inbound.GetConversation;
import com.processpuzzle.chat.conversation.usecases.inbound.ListMessages;
import com.processpuzzle.chat.conversation.usecases.inbound.ReadMarkers;
import com.processpuzzle.chat.conversation.usecases.inbound.ResolveAccess;
import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

/** The three read use cases: finding the conversations of a subject, getting one, and paging its history. */
class ReadConversationsTest {

    private static final ConversationAccess ALLOWED = new ConversationAccess(true, true, true, true);
    private static final ConversationAccess HIDDEN = new ConversationAccess(false, false, false, true);

    private ConversationRepository conversations;
    private MessageRepository messages;
    private ResolveAccess access;
    private OrganizationGuard guard;
    private ReadMarkers markers;
    private CurrentPrincipal principal;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        messages = mock(MessageRepository.class);
        access = mock(ResolveAccess.class);
        guard = mock(OrganizationGuard.class);
        markers = mock(ReadMarkers.class);
        principal = mock(CurrentPrincipal.class);
        when(principal.subject()).thenReturn(Optional.of("sub-caller"));
        conversation = conversation();
        when(conversations.findByIdAndOrgKey(conversation.getId(), ORG)).thenReturn(Optional.of(conversation));
        when(access.of(conversation)).thenReturn(ALLOWED);
    }

    // --- GetConversation

    @Test
    void getsAConversationWithTheCallersAccess() {
        ConversationView view = new GetConversation(conversations, access, guard, markers, principal).get(ORG, conversation.getId());

        assertThat(view.conversation()).isSameAs(conversation);
        assertThat(view.access()).isEqualTo(ALLOWED);
        assertThat(view.following()).isFalse();
        assertThat(view.unread()).isZero();
        verify(guard).requireAccess(ORG);
    }

    @Test
    void aFollowedConversationCarriesTheCallersMarkerAndUnreadCount() {
        conversation.setLastSeq(9);
        when(markers.lastRead("sub-caller", List.of(conversation.getId()))).thenReturn(Map.of(conversation.getId(), 6L));

        ConversationView view = new GetConversation(conversations, access, guard, markers, principal).get(ORG, conversation.getId());

        assertThat(view.following()).isTrue();
        assertThat(view.lastReadSeq()).isEqualTo(6);
        assertThat(view.unread()).isEqualTo(3);
    }

    @Test
    void aConversationTheCallerMayNotReadIsAsGoodAsAbsent() {
        when(access.of(conversation)).thenReturn(HIDDEN);

        GetConversation get = new GetConversation(conversations, access, guard, markers, principal);
        UUID id = conversation.getId();
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> get.get(ORG, id)).isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> get.get(ORG, unknown)).isInstanceOf(ConversationNotFoundException.class);
    }

    // --- FindConversations

    @Test
    void findsOnlyTheConversationsOfTheSubjectThatTheCallerMayRead() {
        Conversation hidden = conversation();
        hidden.setDefinitionKey("race-protests");
        Conversation gone = conversation();
        gone.setDefinitionKey("retired");
        when(conversations.findByOrgKeyAndSubjectTypeAndSubjectIdOrderByCreatedAtAsc(ORG, SubjectType.ENTITY, SUBJECT_ID))
                .thenReturn(List.of(conversation, hidden, gone));
        when(access.of(hidden)).thenReturn(HIDDEN);
        when(access.of(gone)).thenThrow(ConversationNotFoundException.conversation());

        List<ConversationView> found = new FindConversations(conversations, access, guard, markers, principal)
                .find(ORG, SubjectType.ENTITY, SUBJECT_ID, null, null);

        assertThat(found).extracting(ConversationView::conversation).containsExactly(conversation);
    }

    @Test
    void foundConversationsCarryTheCallersMarkers() {
        Conversation second = conversation();
        when(conversations.findByOrgKeyAndSubjectTypeAndSubjectIdOrderByCreatedAtAsc(ORG, SubjectType.ENTITY, SUBJECT_ID))
                .thenReturn(List.of(conversation, second));
        when(access.of(second)).thenReturn(ALLOWED);
        when(markers.lastRead(org.mockito.ArgumentMatchers.eq("sub-caller"), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of(second.getId(), 2L));

        List<ConversationView> found = new FindConversations(conversations, access, guard, markers, principal)
                .find(ORG, SubjectType.ENTITY, SUBJECT_ID, null, null);

        assertThat(found).extracting(ConversationView::following).containsExactly(false, true);
        assertThat(found.get(1).lastReadSeq()).isEqualTo(2);
    }

    @Test
    void narrowsBySubjectDefinitionAndChatDefinition() {
        Conversation other = conversation();
        other.setDefinitionKey("race-protests");
        other.setSubjectDefinition("race");
        when(conversations.findByOrgKeyAndSubjectTypeAndSubjectIdOrderByCreatedAtAsc(ORG, SubjectType.ENTITY, SUBJECT_ID))
                .thenReturn(List.of(conversation, other));
        when(access.of(other)).thenReturn(ALLOWED);
        FindConversations find = new FindConversations(conversations, access, guard, markers, principal);

        assertThat(find.find(ORG, SubjectType.ENTITY, SUBJECT_ID, "race", null)).extracting(ConversationView::conversation).containsExactly(other);
        assertThat(find.find(ORG, SubjectType.ENTITY, SUBJECT_ID, null, "order-discussion")).extracting(ConversationView::conversation).containsExactly(conversation);
        assertThat(find.find(ORG, SubjectType.ENTITY, SUBJECT_ID, "race", "order-discussion")).isEmpty();
    }

    @Test
    void aNonMemberFindsNothingButAnError() {
        doThrow(new OrganizationAccessDeniedException(ORG)).when(guard).requireAccess(ORG);

        assertThatThrownBy(() -> new FindConversations(conversations, access, guard, markers, principal).find(ORG, SubjectType.ENTITY, SUBJECT_ID, null, null))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    // --- ListMessages

    @Test
    void listsTheNewestPageByDefault() {
        List<Message> page = List.of(message(conversation, 3), message(conversation, 2));
        when(messages.findByConversationIdAndSeqLessThanOrderBySeqDesc(any(), anyLong(), any())).thenReturn(page);

        List<Message> listed = new ListMessages(conversations, messages, access, guard).list(ORG, conversation.getId(), null, null);

        assertThat(listed).isSameAs(page);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(messages).findByConversationIdAndSeqLessThanOrderBySeqDesc(
                org.mockito.ArgumentMatchers.eq(conversation.getId()), org.mockito.ArgumentMatchers.eq(Long.MAX_VALUE), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getOffset()).isZero();
    }

    @Test
    void pagesByCursorAndClampsTheLimit() {
        ListMessages list = new ListMessages(conversations, messages, access, guard);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        list.list(ORG, conversation.getId(), 40L, 1000);
        list.list(ORG, conversation.getId(), 40L, 0);
        verify(messages, times(2)).findByConversationIdAndSeqLessThanOrderBySeqDesc(
                org.mockito.ArgumentMatchers.eq(conversation.getId()), org.mockito.ArgumentMatchers.eq(40L), pageable.capture());

        assertThat(pageable.getAllValues()).extracting(Pageable::getPageSize).containsExactly(200, 1);
    }

    @Test
    void historyOfAConversationTheCallerMayNotReadIsNotFound() {
        when(access.of(conversation)).thenReturn(HIDDEN);
        ListMessages list = new ListMessages(conversations, messages, access, guard);

        UUID id = conversation.getId();
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> list.list(ORG, id, null, null)).isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> list.list(ORG, unknown, null, null)).isInstanceOf(ConversationNotFoundException.class);
    }
}
