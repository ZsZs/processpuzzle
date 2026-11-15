package com.processpuzzle.chat.conversation.adapters.inbound;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.SUBJECT_ID;
import static com.processpuzzle.chat.conversation.ConversationFixtures.conversation;
import static com.processpuzzle.chat.conversation.ConversationFixtures.message;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.processpuzzle.chat.common.ChatApiExceptionHandler;
import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.domain.ConversationView;
import com.processpuzzle.chat.conversation.domain.Message;
import com.processpuzzle.chat.conversation.domain.PostedMessage;
import com.processpuzzle.chat.conversation.domain.ReadState;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationConflictException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotWritableException;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.chat.conversation.usecases.inbound.FindConversations;
import com.processpuzzle.chat.conversation.usecases.inbound.GetConversation;
import com.processpuzzle.chat.conversation.usecases.inbound.ListMessages;
import com.processpuzzle.chat.conversation.usecases.inbound.ListUnread;
import com.processpuzzle.chat.conversation.usecases.inbound.MarkRead;
import com.processpuzzle.chat.conversation.usecases.inbound.OpenConversation;
import com.processpuzzle.chat.conversation.usecases.inbound.PostMessage;
import com.processpuzzle.chat.conversation.usecases.inbound.Unfollow;
import com.processpuzzle.chat.definition.domain.SubjectType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The HTTP contract: status codes, the shape of the bodies, and the 404 / 403 / 409 / 400 rules. */
class ConversationEndpointTest {

    private static final String BASE = "/organizations/org-1/chat/conversations";

    private FindConversations find;
    private OpenConversation open;
    private GetConversation get;
    private ListMessages list;
    private PostMessage post;
    private MarkRead markRead;
    private Unfollow unfollow;
    private ListUnread listUnread;
    private MockMvc mvc;
    private Conversation conversation;
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        find = mock(FindConversations.class);
        open = mock(OpenConversation.class);
        get = mock(GetConversation.class);
        list = mock(ListMessages.class);
        post = mock(PostMessage.class);
        markRead = mock(MarkRead.class);
        unfollow = mock(Unfollow.class);
        listUnread = mock(ListUnread.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new ConversationEndpoint(find, open, get, list, post, markRead, unfollow, listUnread, new ConversationMapper()))
                .setControllerAdvice(new ChatApiExceptionHandler())
                .build();
        conversation = conversation();
        conversation.setLastSeq(4);
    }

    private ConversationView view(boolean canWrite) {
        return new ConversationView(conversation, new ConversationAccess(true, canWrite, true, true));
    }

    private String postBody() {
        return "{\"clientMessageId\":\"" + clientId + "\",\"body\":\"Hello\"}";
    }

    // --- conversations

    @Test
    void findsTheConversationsOfASubject() throws Exception {
        when(find.find(ORG, SubjectType.ENTITY, SUBJECT_ID, "order", "order-discussion")).thenReturn(List.of(view(true)));

        mvc.perform(get(BASE).param("subjectType", "ENTITY").param("subjectId", SUBJECT_ID)
                        .param("subjectDefinition", "order").param("definition", "order-discussion"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(conversation.getId().toString()))
                .andExpect(jsonPath("$[0].definitionKey").value("order-discussion"))
                .andExpect(jsonPath("$[0].subjectType").value("ENTITY"))
                .andExpect(jsonPath("$[0].subjectDefinition").value("order"))
                .andExpect(jsonPath("$[0].subjectId").value(SUBJECT_ID))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].lastSeq").value(4))
                .andExpect(jsonPath("$[0].createdBy").value("Olga Opener"))
                .andExpect(jsonPath("$[0].permissions.canWrite").value(true));
    }

    @Test
    void findingNeedsTheSubject() throws Exception {
        mvc.perform(get(BASE).param("subjectType", "ENTITY")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("subjectId", SUBJECT_ID)).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("subjectType", "PLANET").param("subjectId", SUBJECT_ID)).andExpect(status().isBadRequest());
    }

    @Test
    void opensAConversationAnswers201() throws Exception {
        when(open.open(ORG, "order-discussion", SUBJECT_ID, "Protest")).thenReturn(view(true));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"definitionKey\":\"order-discussion\",\"subjectId\":\"" + SUBJECT_ID + "\",\"title\":\"Protest\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(conversation.getId().toString()));
    }

    @Test
    void openingFollowsTheErrorRules() throws Exception {
        String body = "{\"definitionKey\":\"d\",\"subjectId\":\"s\"}";
        when(open.open(ORG, "d", "s", null))
                .thenThrow(ConversationNotFoundException.subject())
                .thenThrow(new ChatAccessDeniedException("no create role"))
                .thenThrow(new ConversationConflictException("exists"))
                .thenThrow(new InvalidChatRequestException("A topic needs a title"));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorId").value("chat.conversation.not-found"));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorId").value("chat.access-denied"));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorId").value("chat.conversation.conflict"));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorId").value("chat.invalid-request"));
    }

    @Test
    void getsAConversationWithWhatTheCallerMayDo() throws Exception {
        when(get.get(ORG, conversation.getId())).thenReturn(view(false));
        UUID unknown = UUID.randomUUID();
        when(get.get(ORG, unknown)).thenThrow(ConversationNotFoundException.conversation());

        mvc.perform(get(BASE + "/" + conversation.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.canWrite").value(false));
        mvc.perform(get(BASE + "/" + unknown)).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void aConversationShowsWhetherTheCallerFollowsItAndHowMuchIsUnread() throws Exception {
        when(get.get(ORG, conversation.getId())).thenReturn(new ConversationView(conversation, new ConversationAccess(true, true, true, true), 1L));

        mvc.perform(get(BASE + "/" + conversation.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.lastReadSeq").value(1))
                .andExpect(jsonPath("$.unread").value(3));
    }

    @Test
    void aConversationTheCallerDoesNotFollowHasNoMarkerAndNothingUnread() throws Exception {
        when(get.get(ORG, conversation.getId())).thenReturn(view(true));

        mvc.perform(get(BASE + "/" + conversation.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.lastReadSeq").doesNotExist())
                .andExpect(jsonPath("$.unread").value(0));
    }

    // --- read markers

    @Test
    void marksAConversationReadAndReportsWhereTheCallerStands() throws Exception {
        when(markRead.markRead(ORG, conversation.getId(), 3)).thenReturn(new ReadState(conversation.getId(), 3, 4));

        mvc.perform(put(BASE + "/" + conversation.getId() + "/read").contentType(MediaType.APPLICATION_JSON).content("{\"lastReadSeq\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(conversation.getId().toString()))
                .andExpect(jsonPath("$.lastReadSeq").value(3))
                .andExpect(jsonPath("$.lastSeq").value(4))
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void markingReadFollowsTheErrorRules() throws Exception {
        when(markRead.markRead(any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(ConversationNotFoundException.conversation())
                .thenThrow(new InvalidChatRequestException("'lastReadSeq' must not be negative"));
        String url = BASE + "/" + conversation.getId() + "/read";

        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"lastReadSeq\":1}")).andExpect(status().isNotFound());
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"lastReadSeq\":1}")).andExpect(status().isBadRequest());
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content("{\"lastReadSeq\":-1}")).andExpect(status().isBadRequest());
    }

    @Test
    void unfollowingAnswers204() throws Exception {
        mvc.perform(delete(BASE + "/" + conversation.getId() + "/follow")).andExpect(status().isNoContent());

        verify(unfollow).unfollow(ORG, conversation.getId());
    }

    @Test
    void listsWhatTheCallerFollowsWithUnreadCounts() throws Exception {
        when(listUnread.list(ORG)).thenReturn(List.of(new ConversationView(conversation, new ConversationAccess(true, true, true, true), 4L)));

        mvc.perform(get("/organizations/org-1/chat/unread")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(conversation.getId().toString()))
                .andExpect(jsonPath("$[0].following").value(true))
                .andExpect(jsonPath("$[0].unread").value(0));
    }

    // --- messages

    @Test
    void listsHistoryNewestFirstByCursor() throws Exception {
        Message newer = message(conversation, 5);
        Message older = message(conversation, 4);
        when(list.list(ORG, conversation.getId(), 6L, 2)).thenReturn(List.of(newer, older));

        mvc.perform(get(BASE + "/" + conversation.getId() + "/messages").param("before", "6").param("limit", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].seq").value(5))
                .andExpect(jsonPath("$[0].clientMessageId").value(newer.getClientMessageId().toString()))
                .andExpect(jsonPath("$[0].authorId").value("sub-author"))
                .andExpect(jsonPath("$[0].authorName").value("Ann Author"))
                .andExpect(jsonPath("$[0].kind").value("TEXT"))
                .andExpect(jsonPath("$[0].body").value("message 5"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[1].seq").value(4));
    }

    @Test
    void theNewestPageNeedsNoCursorAndTheLimitIsBoundByTheContract() throws Exception {
        when(list.list(eq(ORG), eq(conversation.getId()), eq(null), eq(50))).thenReturn(List.of());

        mvc.perform(get(BASE + "/" + conversation.getId() + "/messages")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        verify(list).list(ORG, conversation.getId(), null, 50);
    }

    @Test
    void postingAnswers201ForANewMessageAnd200ForAReplay() throws Exception {
        Message stored = message(conversation, 5);
        when(post.post(ORG, conversation.getId(), clientId, "Hello"))
                .thenReturn(new PostedMessage(stored, true))
                .thenReturn(new PostedMessage(stored, false));

        mvc.perform(post(BASE + "/" + conversation.getId() + "/messages").contentType(MediaType.APPLICATION_JSON).content(postBody()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.seq").value(5));
        mvc.perform(post(BASE + "/" + conversation.getId() + "/messages").contentType(MediaType.APPLICATION_JSON).content(postBody()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seq").value(5));
    }

    @Test
    void postingFollowsTheErrorRules() throws Exception {
        when(post.post(any(), any(), any(), any()))
                .thenThrow(ConversationNotFoundException.conversation())
                .thenThrow(new ChatAccessDeniedException("read-only for you"))
                .thenThrow(new ConversationNotWritableException("closed"))
                .thenThrow(new InvalidChatRequestException("A message needs a body"));
        String url = BASE + "/" + conversation.getId() + "/messages";

        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(postBody())).andExpect(status().isNotFound());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(postBody())).andExpect(status().isForbidden());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(postBody()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorId").value("chat.conversation.not-writable"));
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(postBody())).andExpect(status().isBadRequest());
    }

    @Test
    void aPostWithoutAClientIdOrBodyNeverReachesTheUseCase() throws Exception {
        String url = BASE + "/" + conversation.getId() + "/messages";

        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Hello\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"clientMessageId\":\"" + clientId + "\"}")).andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(post);
    }
}
