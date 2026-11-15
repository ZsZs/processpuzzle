package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.ReadMarker;
import com.processpuzzle.chat.conversation.domain.ReadMarkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts a read marker in a transaction of its own, so that losing a race for the same marker — two tabs of
 * one user following at once — rolls back only this insert. In the caller's transaction a unique violation
 * would mark the whole of it rollback-only, and a post or an open would be lost with it.
 *
 * <p>A bean of its own because the new transaction needs the proxy.
 */
@Service
public class ReadMarkerInsert {

    private final ReadMarkerRepository markers;

    public ReadMarkerInsert(ReadMarkerRepository markers) {
        this.markers = markers;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(ReadMarker marker) {
        markers.saveAndFlush(marker);
    }
}
