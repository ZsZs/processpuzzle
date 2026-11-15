package com.processpuzzle.chat.conversation.usecases;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.conversation.domain.ReadMarker;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.conversation.events.ReadMarkerChangedEvent;
import com.processpuzzle.chat.conversation.usecases.inbound.ReadMarkers;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Following and reading: the marker is created once, only moves forward, and every change is announced. */
class ReadMarkersTest {

    private static final String USER = "sub-ann";

    private ReadMarkerRepository repository;
    private ApplicationEventPublisher events;
    private ReadMarkers markers;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(ReadMarkerRepository.class);
        events = mock(ApplicationEventPublisher.class);
        markers = new ReadMarkers(repository, events);
    }

    private ReadMarkerChangedEvent announced() {
        ArgumentCaptor<ReadMarkerChangedEvent> event = ArgumentCaptor.forClass(ReadMarkerChangedEvent.class);
        verify(events).publishEvent(event.capture());
        return event.getValue();
    }

    private ReadMarker held(long lastRead) {
        return ReadMarker.builder().conversationId(id).userSub(USER).orgKey(ORG).lastReadSeq(lastRead).build();
    }

    /** The user has no marker yet: the insert creates it. */
    private void noMarkerYet() {
        when(repository.insertIfAbsent(any(), any(), any(), anyLong(), any())).thenReturn(1);
    }

    /** The user already follows the conversation, at {@code lastRead}: the insert is refused and the row can be locked. */
    private void followingAt(long lastRead) {
        when(repository.insertIfAbsent(any(), any(), any(), anyLong(), any())).thenReturn(0);
        when(repository.lockByConversationIdAndUserSub(id, USER)).thenReturn(Optional.of(held(lastRead)));
    }

    @Test
    void followsByInsertingAMarkerAtTheGivenNumber() {
        noMarkerYet();

        long standing = markers.advanceTo(ORG, id, USER, 4);

        assertThat(standing).isEqualTo(4);
        verify(repository).insertIfAbsent(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.eq(USER),
                org.mockito.ArgumentMatchers.eq(ORG), org.mockito.ArgumentMatchers.eq(4L), any());
        verify(repository, never()).lockByConversationIdAndUserSub(any(), any());
        assertThat(announced()).isEqualTo(new ReadMarkerChangedEvent(ORG, USER, id, 4, true));
    }

    @Test
    void movesAnExistingMarkerForward() {
        followingAt(2);

        assertThat(markers.advanceTo(ORG, id, USER, 5)).isEqualTo(5);

        verify(repository).advance(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.eq(USER),
                org.mockito.ArgumentMatchers.eq(5L), any());
    }

    @Test
    void neverMovesAMarkerBackAndStillAnnouncesWhereItStands() {
        followingAt(8);

        assertThat(markers.advanceTo(ORG, id, USER, 3)).isEqualTo(8);

        verify(repository, never()).advance(any(), any(), anyLong(), any());
        assertThat(announced().lastReadSeq()).isEqualTo(8);
    }

    @Test
    void aMarkerRemovedBetweenTheRefusedInsertAndTheLockIsSimplyCreatedAgain() {
        when(repository.insertIfAbsent(any(), any(), any(), anyLong(), any())).thenReturn(0).thenReturn(1);
        when(repository.lockByConversationIdAndUserSub(id, USER)).thenReturn(Optional.empty());

        assertThat(markers.advanceTo(ORG, id, USER, 3)).isEqualTo(3);

        verify(repository, times(2)).insertIfAbsent(any(), any(), any(), anyLong(), any());
    }

    @Test
    void aMarkerThatKeepsDisappearingIsNotRetriedForEver() {
        when(repository.insertIfAbsent(any(), any(), any(), anyLong(), any())).thenReturn(0);
        when(repository.lockByConversationIdAndUserSub(id, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> markers.advanceTo(ORG, id, USER, 3)).isInstanceOf(IllegalStateException.class);

        verify(repository, times(3)).insertIfAbsent(any(), any(), any(), anyLong(), any());
    }

    @Test
    void aPosterWhoWasUpToDateMovesToTheirOwnMessage() {
        when(repository.lastReadOf(id, USER)).thenReturn(Optional.of(6L));
        followingAt(6);

        assertThat(markers.followAfterPosting(ORG, id, USER, 7)).isEqualTo(7);

        verify(repository).advance(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.eq(USER),
                org.mockito.ArgumentMatchers.eq(7L), any());
    }

    @Test
    void aPosterWhoWasBehindStaysBehindAndAnnouncesNothing() {
        when(repository.lastReadOf(id, USER)).thenReturn(Optional.of(3L));

        assertThat(markers.followAfterPosting(ORG, id, USER, 7)).isEqualTo(3);

        verify(repository, never()).advance(any(), any(), anyLong(), any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void aFirstTimePosterFollowsAtTheirOwnMessage() {
        noMarkerYet();

        assertThat(markers.followAfterPosting(ORG, id, USER, 7)).isEqualTo(7);

        verify(repository).insertIfAbsent(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.eq(USER),
                org.mockito.ArgumentMatchers.eq(ORG), org.mockito.ArgumentMatchers.eq(7L), any());
    }

    @Test
    void unfollowingDeletesTheMarkerAndAnnouncesIt() {
        markers.unfollow(ORG, id, USER);

        verify(repository).unfollow(ORG, id, USER);
        assertThat(announced()).isEqualTo(new ReadMarkerChangedEvent(ORG, USER, id, 0, false));
    }

    @Test
    void readsTheMarkersAmongSomeConversationsAndSkipsTheQueryForNone() {
        ReadMarker marker = ReadMarker.builder().conversationId(id).userSub(USER).orgKey(ORG).lastReadSeq(5).build();
        when(repository.findByUserSubAndConversationIdIn(USER, List.of(id))).thenReturn(List.of(marker));

        assertThat(markers.lastRead(USER, List.of(id))).isEqualTo(Map.of(id, 5L));
        assertThat(markers.lastRead(USER, List.of())).isEmpty();
        verify(repository, times(1)).findByUserSubAndConversationIdIn(any(), any());
    }
}
