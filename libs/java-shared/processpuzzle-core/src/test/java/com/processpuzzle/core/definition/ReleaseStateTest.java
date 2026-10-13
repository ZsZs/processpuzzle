package com.processpuzzle.core.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReleaseStateTest {

    @Test
    void ofDrafts_namesEachDraftByWhatAReleaseWouldDoToIt() {
        ReleaseParticipant participant = mock(ReleaseParticipant.class);
        when(participant.drafts("acme")).thenReturn(Set.of("order", "customer", "invoice"));
        when(participant.pending("acme")).thenReturn(List.of(
                new PendingChange("entity", "order", PendingChange.Change.ADDED, "{}"),
                new PendingChange("entity", "customer", PendingChange.Change.CHANGED, "{}"),
                new PendingChange("entity", "gone", PendingChange.Change.REMOVED, null)));

        assertThat(ReleaseState.ofDrafts(participant, "acme")).isEqualTo(Map.of(
                "order", ReleaseState.NEW,
                "customer", ReleaseState.CHANGED,
                "invoice", ReleaseState.RELEASED));
    }
}
