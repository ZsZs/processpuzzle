package com.processpuzzle.chat.conversation.usecases;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.conversation.domain.ReadMarker;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.conversation.events.ReadMarkerChangedEvent;
import com.processpuzzle.chat.conversation.usecases.inbound.ReadMarkerInsert;
import com.processpuzzle.chat.conversation.usecases.inbound.ReadMarkers;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

/** Following and reading: the marker is created once, only moves forward, and every change is announced. */
class ReadMarkersTest {

    private static final String USER = "sub-ann";

    private ReadMarkerRepository repository;
    private ReadMarkerInsert inserter;
    private ApplicationEventPublisher events;
    private ReadMarkers markers;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(ReadMarkerRepository.class);
        inserter = mock(ReadMarkerInsert.class);
        events = mock(ApplicationEventPublisher.class);
        markers = new ReadMarkers(repository, inserter, events);
    }

    private ReadMarkerChangedEvent announced() {
        ArgumentCaptor<ReadMarkerChangedEvent> event = ArgumentCaptor.forClass(ReadMarkerChangedEvent.class);
        verify(events).publishEvent(event.capture());
        return event.getValue();
    }

    @Test
    void followsByInsertingAMarkerAtTheGivenNumber() {
        long standing = markers.advanceTo(ORG, id, USER, 4);

        assertThat(standing).isEqualTo(4);
        ArgumentCaptor<ReadMarker> inserted = ArgumentCaptor.forClass(ReadMarker.class);
        verify(inserter).insert(inserted.capture());
        assertThat(inserted.getValue().getConversationId()).isEqualTo(id);
        assertThat(inserted.getValue().getUserSub()).isEqualTo(USER);
        assertThat(inserted.getValue().getOrgKey()).isEqualTo(ORG);
        assertThat(inserted.getValue().getLastReadSeq()).isEqualTo(4);
        assertThat(inserted.getValue().getUpdatedAt()).isNotNull();
        assertThat(announced()).isEqualTo(new ReadMarkerChangedEvent(ORG, USER, id, 4, true));
    }

    @Test
    void movesAnExistingMarkerForward() {
        when(repository.lastReadOf(id, USER)).thenReturn(Optional.of(2L)).thenReturn(Optional.of(5L));

        assertThat(markers.advanceTo(ORG, id, USER, 5)).isEqualTo(5);

        verify(repository).advance(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.eq(USER),
                org.mockito.ArgumentMatchers.eq(5L), any());
        verify(inserter, never()).insert(any());
    }

    @Test
    void neverMovesAMarkerBackAndStillAnnouncesWhereItStands() {
        when(repository.lastReadOf(id, USER)).thenReturn(Optional.of(8L));

        assertThat(markers.advanceTo(ORG, id, USER, 3)).isEqualTo(8);

        verify(repository, never()).advance(any(), any(), anyLong(), any());
        assertThat(announced().lastReadSeq()).isEqualTo(8);
    }

    @Test
    void aLostRaceForTheSameMarkerFallsBackToMovingTheWinnersForward() {
        when(repository.lastReadOf(id, USER)).thenReturn(Optional.empty()).thenReturn(Optional.of(6L));
        doThrow(new DataIntegrityViolationException("pk")).when(inserter).insert(any());

        assertThat(markers.advanceTo(ORG, id, USER, 3)).isEqualTo(6);

        verify(repository).advance(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.eq(USER),
                org.mockito.ArgumentMatchers.eq(3L), any());
    }

    @Test
    void aPosterWhoWasUpToDateMovesToTheirOwnMessage() {
        when(repository.lastReadOf(id, USER)).thenReturn(Optional.of(6L)).thenReturn(Optional.of(6L)).thenReturn(Optional.of(7L));

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
        assertThat(markers.followAfterPosting(ORG, id, USER, 7)).isEqualTo(7);

        ArgumentCaptor<ReadMarker> inserted = ArgumentCaptor.forClass(ReadMarker.class);
        verify(inserter).insert(inserted.capture());
        assertThat(inserted.getValue().getLastReadSeq()).isEqualTo(7);
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
        verify(repository, org.mockito.Mockito.times(1)).findByUserSubAndConversationIdIn(any(), any());
    }
}
