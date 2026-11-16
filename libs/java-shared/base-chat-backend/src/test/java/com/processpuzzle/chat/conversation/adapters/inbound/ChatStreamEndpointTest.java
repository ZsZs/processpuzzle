package com.processpuzzle.chat.conversation.adapters.inbound;

import com.processpuzzle.chat.common.ChatMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.conversation;
import static com.processpuzzle.chat.conversation.ConversationFixtures.message;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.processpuzzle.chat.common.ChatApiExceptionHandler;
import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.StreamGrant;
import com.processpuzzle.chat.conversation.events.MessagePostedEvent;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.chat.conversation.usecases.inbound.OpenChatStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The live stream over a real servlet-style response: headers, event wire format, and the refusals as JSON. */
class ChatStreamEndpointTest {

    private static final String URL = "/organizations/org-1/chat/stream";

    private OpenChatStream open;
    private ChatStreamRegistry registry;
    private MockMvc mvc;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        open = mock(OpenChatStream.class);
        registry = new ChatStreamRegistry(new ConversationMapper(), new ChatMetrics((MeterRegistry) null), 25);
        mvc = MockMvcBuilders.standaloneSetup(new ChatStreamEndpoint(open, registry))
                .setControllerAdvice(new ChatApiExceptionHandler())
                .build();
        conversation = conversation();
    }

    private StreamGrant grant(List<com.processpuzzle.chat.conversation.domain.Message> replay) {
        return new StreamGrant("sub-ann", Set.of(conversation.getId()), Instant.now().plusSeconds(600), replay, Set.of());
    }

    @Test
    void opensAnEventStreamThatTellsNginxNotToBufferIt() throws Exception {
        when(open.open(ORG, Map.of())).thenReturn(grant(List.of()));

        MvcResult result = mvc.perform(get(URL)).andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Content-Type", "text/event-stream"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-cache").contains("no-transform");
        assertThat(registry.openConnections()).isEqualTo(1);
        assertThat(result.getResponse().getContentAsString()).contains("event:stream.ready").contains("\"heartbeatSeconds\":25");
    }

    @Test
    void whatTheClientMissedAndLiveMessagesFollowOnTheSameConnection() throws Exception {
        when(open.open(ORG, Map.of(conversation.getId(), 3L))).thenReturn(grant(List.of(message(conversation, 4))));

        MvcResult result = mvc.perform(get(URL).param("after", conversation.getId() + ":3")).andExpect(request().asyncStarted()).andReturn();
        registry.onMessagePosted(new MessagePostedEvent(ORG, conversation.getId(), message(conversation, 5)));

        String body = result.getResponse().getContentAsString();
        assertThat(body.indexOf("event:stream.ready")).isLessThan(body.indexOf("id:" + conversation.getId() + ":4"));
        assertThat(body.indexOf("id:" + conversation.getId() + ":4")).isLessThan(body.indexOf("id:" + conversation.getId() + ":5"));
        assertThat(body).contains("event:message.posted").contains("\"body\":\"message 5\"");
    }

    @Test
    void aRefusalIsAnOrdinaryJsonErrorBecauseItHappensBeforeTheStreamExists() throws Exception {
        when(open.open(any(), any())).thenThrow(new ChatAccessDeniedException("A live stream needs a signed-in user"));

        mvc.perform(get(URL).accept("text/event-stream, application/json")).andExpect(status().isForbidden());
        assertThat(registry.openConnections()).isZero();
    }

    @Test
    void aMalformedAfterIs400() throws Exception {
        mvc.perform(get(URL).param("after", "not-a-pair").accept("application/json")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("after", UUID.randomUUID() + ":-1").accept("application/json")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("after", "nope:3").accept("application/json")).andExpect(status().isBadRequest());
        verify(open, never()).open(any(), any());
    }

    @Test
    void parsesPairsKeepsTheNewestOfARepeatedConversationAndToleratesAbsence() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(ChatStreamEndpoint.parseAfter(first + ":3, " + second + ":0," + first + ":9"))
                .containsEntry(first, 9L).containsEntry(second, 0L).hasSize(2);
        assertThat(ChatStreamEndpoint.parseAfter(null)).isEmpty();
        assertThat(ChatStreamEndpoint.parseAfter("  ")).isEmpty();
    }

    @Test
    void refusesAnAbsurdNumberOfConversations() {
        String many = String.join(",", java.util.stream.IntStream.range(0, ChatStreamEndpoint.MAX_AFTER_ENTRIES + 1)
                .mapToObj(i -> UUID.randomUUID() + ":1").toList());

        assertThatThrownBy(() -> ChatStreamEndpoint.parseAfter(many)).isInstanceOf(InvalidChatRequestException.class);
    }
}
