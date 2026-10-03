package com.processpuzzle.ai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class VisionJobTicketTest {

    @Test
    void acceptsOnlyTheLatestCallbackToken() {
        VisionJobTicket ticket = VisionJobTicket.open("my-org", VisionJobTicket.Kind.ENROLLMENT, Instant.now());
        String first = ticket.newCallbackToken();
        assertThat(ticket.accepts(first)).isTrue();

        String second = ticket.newCallbackToken();
        assertThat(ticket.accepts(second)).isTrue();
        assertThat(ticket.accepts(first)).isFalse();
        assertThat(ticket.accepts(null)).isFalse();
        assertThat(ticket.getCallbackTokenHash()).doesNotContain(second);
    }

    @Test
    void isOpenUntilCompletedOrFailed() {
        VisionJobTicket ticket = VisionJobTicket.open("my-org", VisionJobTicket.Kind.ENROLLMENT, Instant.now());
        assertThat(ticket.isOpen()).isTrue();
        ticket.submitting(Instant.now());
        ticket.submitted(Instant.now());
        assertThat(ticket.isOpen()).isTrue();
        assertThat(ticket.getSubmitAttempts()).isEqualTo(1);
        ticket.completed(Instant.now());
        assertThat(ticket.isOpen()).isFalse();
    }
}
