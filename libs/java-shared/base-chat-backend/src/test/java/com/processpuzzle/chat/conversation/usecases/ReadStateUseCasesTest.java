package com.processpuzzle.chat.conversation.usecases;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.conversation;
import static com.processpuzzle.chat.conversation.ConversationFixtures.message;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.ConversationView;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.MessageRepository;
import com.processpuzzle.chat.conversation.domain.ReadMarker;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.conversation.domain.ReadState;
import com.processpuzzle.chat.conversation.domain.StreamGrant;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.chat.conversation.usecases.inbound.ListUnread;
import com.processpuzzle.chat.conversation.usecases.inbound.MarkRead;
import com.processpuzzle.chat.conversation.usecases.inbound.OpenChatStream;
import com.processpuzzle.chat.conversation.usecases.inbound.ReadMarkers;
import com.processpuzzle.chat.conversation.usecases.inbound.ResolveAccess;
import com.processpuzzle.chat.conversation.usecases.inbound.Unfollow;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

/** Marking read, unfollowing, listing what is followed, and what a live connection may carry. */
class ReadStateUseCasesTest {

    private static final ConversationAccess ALLOWED = new ConversationAccess(true, true, true, true);
    private static final ConversationAccess HIDDEN = new ConversationAccess(false, false, false, true);
    private static final String USER = "sub-ann";

    private ConversationRepository conversations;
    private MessageRepository messages;
    private ReadMarkerRepository markerRepository;
    private ReadMarkers markers;
    private ResolveAccess access;
    private OrganizationGuard guard;
    private CurrentPrincipal principal;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        messages = mock(MessageRepository.class);
        markerRepository = mock(ReadMarkerRepository.class);
        markers = mock(ReadMarkers.class);
        access = mock(ResolveAccess.class);
        guard = mock(OrganizationGuard.class);
        principal = mock(CurrentPrincipal.class);
        when(principal.subject()).thenReturn(Optional.of(USER));
        conversation = conversation();
        conversation.setLastSeq(10);
        when(conversations.findByIdAndOrgKey(conversation.getId(), ORG)).thenReturn(Optional.of(conversation));
        when(access.of(conversation)).thenReturn(ALLOWED);
    }

    private static ReadMarker marker(Conversation conversation, long lastRead) {
        return ReadMarker.builder().conversationId(conversation.getId()).userSub(USER).orgKey(ORG).lastReadSeq(lastRead)
                .updatedAt(Instant.now()).build();
    }

    // --- MarkRead

    @Test
    void marksReadUpToTheGivenNumberAndReportsWhereTheCallerStands() {
        MarkRead markRead = new MarkRead(conversations, access, markers, guard, principal);
        when(markers.advanceTo(ORG, conversation.getId(), USER, 7)).thenReturn(7L);

        ReadState state = markRead.markRead(ORG, conversation.getId(), 7);

        assertThat(state).isEqualTo(new ReadState(conversation.getId(), 7, 10));
        assertThat(state.unread()).isEqualTo(3);
        verify(guard).requireAccess(ORG);
    }

    @Test
    void aNumberPastTheNewestMessageIsCutBackToIt() {
        MarkRead markRead = new MarkRead(conversations, access, markers, guard, principal);
        when(markers.advanceTo(ORG, conversation.getId(), USER, 10)).thenReturn(10L);

        assertThat(markRead.markRead(ORG, conversation.getId(), 99).unread()).isZero();

        verify(markers).advanceTo(ORG, conversation.getId(), USER, 10);
    }

    @Test
    void markingReadRefusesTheUnreadableTheNegativeAndTheAnonymous() {
        MarkRead markRead = new MarkRead(conversations, access, markers, guard, principal);

        when(access.of(conversation)).thenReturn(HIDDEN);
        assertThatThrownBy(() -> markRead.markRead(ORG, conversation.getId(), 1)).isInstanceOf(ConversationNotFoundException.class);
        assertThatThrownBy(() -> markRead.markRead(ORG, UUID.randomUUID(), 1)).isInstanceOf(ConversationNotFoundException.class);
        when(access.of(conversation)).thenReturn(ALLOWED);
        assertThatThrownBy(() -> markRead.markRead(ORG, conversation.getId(), -1)).isInstanceOf(InvalidChatRequestException.class);
        when(principal.subject()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> markRead.markRead(ORG, conversation.getId(), 1)).isInstanceOf(ChatAccessDeniedException.class);

        verify(markers, never()).advanceTo(any(), any(), any(), anyLong());
    }

    // --- Unfollow

    @Test
    void unfollowingAsksNothingOfTheConversation() {
        new Unfollow(markers, guard, principal).unfollow(ORG, conversation.getId());

        verify(markers).unfollow(ORG, conversation.getId(), USER);
        org.mockito.Mockito.verifyNoInteractions(access);
    }

    @Test
    void unfollowingNeedsMembershipAndASignedInUser() {
        Unfollow unfollow = new Unfollow(markers, guard, principal);
        when(principal.subject()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> unfollow.unfollow(ORG, conversation.getId())).isInstanceOf(ChatAccessDeniedException.class);

        when(principal.subject()).thenReturn(Optional.of(USER));
        doThrow(new OrganizationAccessDeniedException(ORG)).when(guard).requireAccess(ORG);
        assertThatThrownBy(() -> unfollow.unfollow(ORG, conversation.getId())).isInstanceOf(OrganizationAccessDeniedException.class);
    }

    // --- ListUnread

    @Test
    void listsWhatTheCallerFollowsWithTheirMarkersAndSkipsWhatTheyCanNoLongerRead() {
        Conversation hidden = conversation();
        Conversation gone = conversation();
        Conversation elsewhere = conversation();
        elsewhere.setOrgKey("org-2");
        when(markerRepository.findByOrgKeyAndUserSubOrderByUpdatedAtDesc(org.mockito.ArgumentMatchers.eq(ORG),
                org.mockito.ArgumentMatchers.eq(USER), any(Pageable.class)))
                .thenReturn(List.of(marker(conversation, 4), marker(hidden, 0), marker(gone, 0), marker(elsewhere, 0)));
        when(conversations.findAllById(any())).thenReturn(List.of(conversation, hidden, gone, elsewhere));
        when(access.of(hidden)).thenReturn(HIDDEN);
        when(access.of(gone)).thenThrow(ConversationNotFoundException.conversation());

        List<ConversationView> followed = new ListUnread(markerRepository, conversations, access, guard, principal).list(ORG);

        assertThat(followed).singleElement().satisfies(view -> {
            assertThat(view.conversation()).isSameAs(conversation);
            assertThat(view.lastReadSeq()).isEqualTo(4);
            assertThat(view.unread()).isEqualTo(6);
        });
    }

    @Test
    void listingNeedsMembershipAndASignedInUser() {
        ListUnread list = new ListUnread(markerRepository, conversations, access, guard, principal);
        when(principal.subject()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> list.list(ORG)).isInstanceOf(ChatAccessDeniedException.class);
    }

    // --- OpenChatStream

    private OpenChatStream stream(long maxAgeMinutes) {
        return new OpenChatStream(markerRepository, conversations, messages, access, guard, principal, maxAgeMinutes);
    }

    private void follows(Conversation... followed) {
        when(markerRepository.findByOrgKeyAndUserSubOrderByUpdatedAtDesc(org.mockito.ArgumentMatchers.eq(ORG),
                org.mockito.ArgumentMatchers.eq(USER), any(Pageable.class)))
                .thenReturn(java.util.Arrays.stream(followed).map(c -> marker(c, 0)).toList());
        when(conversations.findAllById(any())).thenReturn(List.of(followed));
    }

    @Test
    void aStreamCarriesTheFollowedConversationsTheCallerMayReadNow() {
        Conversation hidden = conversation();
        Conversation gone = conversation();
        follows(conversation, hidden, gone);
        when(access.of(hidden)).thenReturn(HIDDEN);
        when(access.of(gone)).thenThrow(ConversationNotFoundException.conversation());

        StreamGrant grant = stream(15).open(ORG, Map.of());

        assertThat(grant.userSub()).isEqualTo(USER);
        assertThat(grant.conversations()).containsExactly(conversation.getId());
        assertThat(grant.replay()).isEmpty();
        assertThat(grant.resync()).isEmpty();
        verify(guard).requireAccess(ORG);
    }

    @Test
    void theStreamEndsAtTheTokensExpiryButNeverLaterThanTheMaximumAge() {
        follows(conversation);
        Instant soon = Instant.now().plusSeconds(90);
        when(principal.tokenExpiresAt()).thenReturn(Optional.of(soon));
        assertThat(stream(15).open(ORG, Map.of()).expiresAt()).isEqualTo(soon);

        when(principal.tokenExpiresAt()).thenReturn(Optional.of(Instant.now().plus(Duration.ofHours(2))));
        assertThat(stream(15).open(ORG, Map.of()).expiresAt())
                .isBetween(Instant.now().plus(Duration.ofMinutes(14)), Instant.now().plus(Duration.ofMinutes(15)).plusSeconds(1));

        when(principal.tokenExpiresAt()).thenReturn(Optional.empty());
        assertThat(stream(5).open(ORG, Map.of()).expiresAt())
                .isBetween(Instant.now().plus(Duration.ofMinutes(4)), Instant.now().plus(Duration.ofMinutes(5)).plusSeconds(1));
    }

    @Test
    void aReconnectingClientGetsWhatItMissedOldestFirst() {
        follows(conversation);
        List<Message> missed = List.of(message(conversation, 8), message(conversation, 9), message(conversation, 10));
        when(messages.findByConversationIdAndSeqGreaterThanOrderBySeqAsc(org.mockito.ArgumentMatchers.eq(conversation.getId()),
                org.mockito.ArgumentMatchers.eq(7L), any(Pageable.class))).thenReturn(missed);

        StreamGrant grant = stream(15).open(ORG, Map.of(conversation.getId(), 7L));

        assertThat(grant.replay()).isEqualTo(missed);
        assertThat(grant.resync()).isEmpty();
    }

    @Test
    void aClientThatIsUpToDateGetsNothingAndOneFarBehindIsToldToResync() {
        follows(conversation);
        conversation.setLastSeq(500);

        assertThat(stream(15).open(ORG, Map.of(conversation.getId(), 500L)).replay()).isEmpty();

        StreamGrant behind = stream(15).open(ORG, Map.of(conversation.getId(), 100L));
        assertThat(behind.replay()).isEmpty();
        assertThat(behind.resync()).containsExactly(conversation.getId());
        verify(messages, never()).findByConversationIdAndSeqGreaterThanOrderBySeqAsc(any(), anyLong(), any());
    }

    @Test
    void nothingIsReplayedForAConversationTheCallerDoesNotFollowOrMayNotRead() {
        Conversation other = conversation();
        other.setLastSeq(5);
        follows(conversation);

        StreamGrant grant = stream(15).open(ORG, Map.of(other.getId(), 0L));

        assertThat(grant.replay()).isEmpty();
        assertThat(grant.resync()).isEmpty();
    }

    @Test
    void aStreamNeedsMembershipAndASignedInUser() {
        when(principal.subject()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> stream(15).open(ORG, Map.of())).isInstanceOf(ChatAccessDeniedException.class);

        when(principal.subject()).thenReturn(Optional.of(USER));
        doThrow(new OrganizationAccessDeniedException(ORG)).when(guard).requireAccess(ORG);
        assertThatThrownBy(() -> stream(15).open(ORG, Map.of())).isInstanceOf(OrganizationAccessDeniedException.class);
    }
}
