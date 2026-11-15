package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.ReadMarker;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import com.processpuzzle.chat.conversation.events.ReadMarkerChangedEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Following and reading, in one place: a user follows a conversation by having a marker, and the marker only
 * ever moves forward. Callers have decided whether the user may read the conversation; this does not ask.
 *
 * <p>Every change is announced with a {@link ReadMarkerChangedEvent}, which the user's open streams observe
 * after the transaction commits.
 */
@Service
@Transactional
public class ReadMarkers {

    private final ReadMarkerRepository markers;
    private final ReadMarkerInsert inserter;
    private final ApplicationEventPublisher events;

    public ReadMarkers(ReadMarkerRepository markers, ReadMarkerInsert inserter, ApplicationEventPublisher events) {
        this.markers = markers;
        this.inserter = inserter;
        this.events = events;
    }

    /**
     * Follows the conversation if the user does not yet, and moves their marker to {@code seq} unless it is
     * already past it.
     *
     * @return where the marker stands now
     */
    public long advanceTo(String orgKey, UUID conversationId, String userSub, long seq) {
        long standing = move(orgKey, conversationId, userSub, seq);
        events.publishEvent(new ReadMarkerChangedEvent(orgKey, userSub, conversationId, standing, true));
        return standing;
    }

    /**
     * For somebody who just posted: they follow the conversation, and their marker moves to their own message
     * if they had read everything before it. A poster who was behind stays behind — posting does not mark
     * what they have not seen as read.
     *
     * @return where the marker stands now
     */
    public long followAfterPosting(String orgKey, UUID conversationId, String userSub, long postedSeq) {
        Optional<Long> standing = markers.lastReadOf(conversationId, userSub);
        if (standing.isEmpty() || standing.get() >= postedSeq - 1) {
            return advanceTo(orgKey, conversationId, userSub, postedSeq);
        }
        return standing.get();
    }

    /** Stops following. Idempotent: a conversation the user did not follow stays unfollowed. */
    public void unfollow(String orgKey, UUID conversationId, String userSub) {
        markers.unfollow(orgKey, conversationId, userSub);
        events.publishEvent(new ReadMarkerChangedEvent(orgKey, userSub, conversationId, 0, false));
    }

    /** The user's markers among {@code conversationIds}, by conversation; the ones they do not follow are absent. */
    @Transactional(readOnly = true)
    public Map<UUID, Long> lastRead(String userSub, Collection<UUID> conversationIds) {
        Map<UUID, Long> lastRead = new HashMap<>();
        if (!conversationIds.isEmpty()) {
            markers.findByUserSubAndConversationIdIn(userSub, conversationIds)
                    .forEach(marker -> lastRead.put(marker.getConversationId(), marker.getLastReadSeq()));
        }
        return lastRead;
    }

    private long move(String orgKey, UUID conversationId, String userSub, long seq) {
        Optional<Long> standing = markers.lastReadOf(conversationId, userSub);
        if (standing.isPresent()) {
            return standing.get() >= seq ? standing.get() : advance(conversationId, userSub, seq);
        }
        try {
            inserter.insert(ReadMarker.builder().conversationId(conversationId).userSub(userSub).orgKey(orgKey)
                    .lastReadSeq(seq).updatedAt(Instant.now()).build());
            return seq;
        } catch (DataIntegrityViolationException e) {
            // Another session of the same user followed first; their marker stands unless ours is further.
            return advance(conversationId, userSub, seq);
        }
    }

    private long advance(UUID conversationId, String userSub, long seq) {
        markers.advance(conversationId, userSub, seq, Instant.now());
        return markers.lastReadOf(conversationId, userSub).orElse(seq);
    }
}
