package com.processpuzzle.chat.conversation.usecases;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.conversation;
import static com.processpuzzle.chat.conversation.ConversationFixtures.message;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.ConversationStatus;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.MessageKind;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.domain.PostedMessage;
import com.processpuzzle.chat.conversation.events.MessagePostedEvent;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotWritableException;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.chat.conversation.usecases.inbound.PostMessage;
import com.processpuzzle.chat.conversation.usecases.inbound.ReadMarkers;
import com.processpuzzle.chat.conversation.usecases.inbound.ResolveAccess;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class PostMessageTest {

    private ConversationRepository conversations;
    private MessageRepository messages;
    private ResolveAccess access;
    private OrganizationGuard guard;
    private CurrentPrincipal principal;
    private ReadMarkers markers;
    private ApplicationEventPublisher events;
    private PostMessage post;
    private Conversation conversation;
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        messages = mock(MessageRepository.class);
        access = mock(ResolveAccess.class);
        guard = mock(OrganizationGuard.class);
        principal = mock(CurrentPrincipal.class);
        markers = mock(ReadMarkers.class);
        events = mock(ApplicationEventPublisher.class);
        post = new PostMessage(conversations, messages, access, guard, principal, markers, events, 50);

        conversation = conversation();
        when(conversations.findByIdAndOrgKey(conversation.getId(), ORG)).thenReturn(Optional.of(conversation));
        when(access.of(conversation)).thenReturn(new ConversationAccess(true, true, true, true));
        when(principal.subject()).thenReturn(Optional.of("sub-author"));
        when(principal.displayName()).thenReturn(Optional.of("Ann Author"));
        when(conversations.lockIfStatus(conversation.getId(), ORG, ConversationStatus.OPEN)).thenReturn(1);
        when(conversations.lastSeqOf(conversation.getId())).thenReturn(7L);
        when(messages.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void storesTheMessageUnderTheNextNumberTakenUnderTheLock() {
        PostedMessage posted = post.post(ORG, conversation.getId(), clientId, "Hello");

        assertThat(posted.created()).isTrue();
        Message stored = posted.message();
        assertThat(stored.getSeq()).isEqualTo(7);
        assertThat(stored.getConversationId()).isEqualTo(conversation.getId());
        assertThat(stored.getOrgKey()).isEqualTo(ORG);
        assertThat(stored.getClientMessageId()).isEqualTo(clientId);
        assertThat(stored.getAuthorSub()).isEqualTo("sub-author");
        assertThat(stored.getAuthorDisplayName()).isEqualTo("Ann Author");
        assertThat(stored.getKind()).isEqualTo(MessageKind.TEXT);
        assertThat(stored.getBody()).isEqualTo("Hello");
        assertThat(stored.getCreatedAt()).isNotNull();
        var order = inOrder(conversations, messages);
        order.verify(conversations).lockIfStatus(conversation.getId(), ORG, ConversationStatus.OPEN);
        order.verify(messages).findByConversationIdAndAuthorSubAndClientMessageId(conversation.getId(), "sub-author", clientId);
        order.verify(conversations).advanceSeq(conversation.getId(), ORG);
        order.verify(conversations).lastSeqOf(conversation.getId());
        order.verify(messages).save(stored);
    }

    @Test
    void theAuthorFollowsAndTheStoredMessageIsAnnouncedForAfterCommit() {
        Message stored = post.post(ORG, conversation.getId(), clientId, "Hello").message();

        verify(markers).followAfterPosting(ORG, conversation.getId(), "sub-author", 7);
        ArgumentCaptor<MessagePostedEvent> published = ArgumentCaptor.forClass(MessagePostedEvent.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue().orgKey()).isEqualTo(ORG);
        assertThat(published.getValue().conversationId()).isEqualTo(conversation.getId());
        assertThat(published.getValue().message()).isSameAs(stored);
    }

    @Test
    void aReplayAnnouncesNothingAndFollowsNothing() {
        when(messages.findByConversationIdAndAuthorSubAndClientMessageId(conversation.getId(), "sub-author", clientId))
                .thenReturn(Optional.of(message(conversation, 3)));

        post.post(ORG, conversation.getId(), clientId, "Hello again");

        verify(events, never()).publishEvent(any(Object.class));
        verify(markers, never()).followAfterPosting(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void aRefusedPostAnnouncesNothing() {
        when(access.of(conversation)).thenReturn(new ConversationAccess(true, false, true, true));

        assertThatThrownBy(() -> post.post(ORG, conversation.getId(), clientId, "Hi")).isInstanceOf(ChatAccessDeniedException.class);

        verify(events, never()).publishEvent(any(Object.class));
        verify(markers, never()).followAfterPosting(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void aReplayReturnsTheStoredMessageAndTakesNoNumber() {
        Message earlier = message(conversation, 3);
        when(messages.findByConversationIdAndAuthorSubAndClientMessageId(conversation.getId(), "sub-author", clientId))
                .thenReturn(Optional.of(earlier));

        PostedMessage posted = post.post(ORG, conversation.getId(), clientId, "Hello again");

        assertThat(posted.created()).isFalse();
        assertThat(posted.message()).isSameAs(earlier);
        verify(conversations, never()).advanceSeq(any(), any());
        verify(messages, never()).save(any());
    }

    @Test
    void aCallerWhoMayNotReadItIsToldTheConversationDoesNotExist() {
        when(access.of(conversation)).thenReturn(new ConversationAccess(false, false, false, true));

        assertThatThrownBy(() -> post.post(ORG, conversation.getId(), clientId, "Hi")).isInstanceOf(ConversationNotFoundException.class);
        verify(conversations, never()).lockIfStatus(any(), any(), any());
    }

    @Test
    void aReaderWithoutAWriteRoleIsRefused() {
        when(access.of(conversation)).thenReturn(new ConversationAccess(true, false, true, true));

        assertThatThrownBy(() -> post.post(ORG, conversation.getId(), clientId, "Hi")).isInstanceOf(ChatAccessDeniedException.class);
        verify(messages, never()).save(any());
    }

    @Test
    void aConversationThatIsNotWritableRightNowIsAConflict() {
        when(access.of(conversation)).thenReturn(new ConversationAccess(true, true, true, false));

        assertThatThrownBy(() -> post.post(ORG, conversation.getId(), clientId, "Hi")).isInstanceOf(ConversationNotWritableException.class);
        verify(conversations, never()).lockIfStatus(any(), any(), any());
    }

    @Test
    void aConversationClosedWhileWeWereDecidingIsAConflictToo() {
        when(conversations.lockIfStatus(conversation.getId(), ORG, ConversationStatus.OPEN)).thenReturn(0);

        assertThatThrownBy(() -> post.post(ORG, conversation.getId(), clientId, "Hi")).isInstanceOf(ConversationNotWritableException.class);
        verify(messages, never()).save(any());
    }

    @Test
    void anUnknownConversationIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(conversations.findByIdAndOrgKey(unknown, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> post.post(ORG, unknown, clientId, "Hi")).isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void validatesTheRequestBeforeTouchingTheConversation() {
        assertThatThrownBy(() -> post.post(ORG, conversation.getId(), null, "Hi")).isInstanceOf(InvalidChatRequestException.class);
        assertThatThrownBy(() -> post.post(ORG, conversation.getId(), clientId, null)).isInstanceOf(InvalidChatRequestException.class);
        assertThatThrownBy(() -> post.post(ORG, conversation.getId(), clientId, "   ")).isInstanceOf(InvalidChatRequestException.class);
        assertThatThrownBy(() -> post.post(ORG, conversation.getId(), clientId, "x".repeat(51)))
                .isInstanceOf(InvalidChatRequestException.class).hasMessageContaining("50");
        verify(conversations, never()).findByIdAndOrgKey(any(), any());

        assertThat(post.post(ORG, conversation.getId(), clientId, "x".repeat(50)).created()).isTrue();
    }

    @Test
    void theConfiguredLimitCannotExceedWhatTheColumnHolds() {
        PostMessage generous = new PostMessage(conversations, messages, access, guard, principal, markers, events, Integer.MAX_VALUE);

        assertThatThrownBy(() -> generous.post(ORG, conversation.getId(), clientId, "x".repeat(10_001)))
                .isInstanceOf(InvalidChatRequestException.class).hasMessageContaining("10000");
    }

    @Test
    void needsASignedInUserAndMembershipOfTheOrganization() {
        when(principal.subject()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> post.post(ORG, conversation.getId(), clientId, "Hi")).isInstanceOf(ChatAccessDeniedException.class);

        when(principal.subject()).thenReturn(Optional.of("sub-author"));
        org.mockito.Mockito.doThrow(new OrganizationAccessDeniedException(ORG)).when(guard).requireAccess(ORG);
        assertThatThrownBy(() -> post.post(ORG, conversation.getId(), clientId, "Hi")).isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void anAnonymousNameIsStoredAsNull() {
        when(principal.displayName()).thenReturn(Optional.empty());

        ArgumentCaptor<Message> stored = ArgumentCaptor.forClass(Message.class);
        post.post(ORG, conversation.getId(), clientId, "Hi");
        verify(messages).save(stored.capture());

        assertThat(stored.getValue().getAuthorDisplayName()).isNull();
    }
}
