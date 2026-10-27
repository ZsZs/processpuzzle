package com.processpuzzle.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AssistantEndpointTest {

    private static final String TURN = "/organizations/acme/assistant/turns";

    private DesignAssistant assistant;
    private AssistantAccess access;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        assistant = mock(DesignAssistant.class);
        access = mock(AssistantAccess.class);
        when(access.currentOrganization()).thenReturn("acme");
        when(access.mayDesign()).thenReturn(true);
        mvc = MockMvcBuilders.standaloneSetup(new AssistantEndpoint(assistant, access)).build();
    }

    @Test
    void aTurnStreamsTheReplyThenDone() throws Exception {
        when(assistant.respond("Define invoice.")).thenReturn("Saved the draft.");

        MvcResult result = mvc.perform(post(TURN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Define invoice.\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();
        String body = mvc.perform(asyncDispatch(result)).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("event:reply", "data:Saved the draft.", "event:done");
    }

    @Test
    void aCallerWhoMayNotDesignIsForbiddenBeforeAnyModelCall() throws Exception {
        when(access.mayDesign()).thenReturn(false);

        mvc.perform(post(TURN).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Define invoice.\"}"))
            .andExpect(status().isForbidden());
        verify(assistant, never()).respond(anyString());
    }

    @Test
    void aTurnForAnotherOrganizationIsForbidden() throws Exception {
        mvc.perform(post("/organizations/globex/assistant/turns").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Define invoice.\"}"))
            .andExpect(status().isForbidden());
        verify(assistant, never()).respond(anyString());
    }

    @Test
    void anEmptyOrOversizedMessageIsRejected() throws Exception {
        mvc.perform(post(TURN).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\" \"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(TURN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + "x".repeat(AssistantEndpoint.MAX_MESSAGE_LENGTH + 1) + "\"}"))
            .andExpect(status().isBadRequest());
        verify(assistant, never()).respond(anyString());
    }

    @Test
    void aFailingModelCallBecomesAnErrorEventNotABrokenStream() throws Exception {
        when(assistant.respond(anyString())).thenThrow(new IllegalStateException("gateway down"));

        MvcResult result = mvc.perform(post(TURN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Define invoice.\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();
        String body = mvc.perform(asyncDispatch(result)).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("event:error").doesNotContain("gateway down");
    }
}
