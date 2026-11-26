package com.processpuzzle.ai.assist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.processpuzzle.ai.adapter.inbound.AiApiExceptionHandler;
import com.processpuzzle.ai.assistant.AssistantAccess;
import com.processpuzzle.ai.assistant.AssistantNotConfiguredException;
import com.processpuzzle.ai.assistant.AssistantQuotaExceededException;
import com.processpuzzle.core.assist.AssistRequest;
import com.processpuzzle.core.definition.AuthoringIssue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthoringAssistEndpointTest {

    private static final String RUN = "/organizations/acme/ai/assist/rule.expression";

    private AuthoringAssist assist;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        assist = mock(AuthoringAssist.class);
        AssistantAccess access = mock(AssistantAccess.class);
        when(access.currentOrganization()).thenReturn("acme");
        mvc = MockMvcBuilders.standaloneSetup(new AuthoringAssistEndpoint(assist, access))
            .setControllerAdvice(new AiApiExceptionHandler()).build();
    }

    @Test
    void theTasksOfTheCallersOrganizationAreListed() throws Exception {
        when(assist.availableTasks("acme")).thenReturn(List.of("rule.expression"));

        mvc.perform(get("/organizations/acme/ai/assist"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks[0]").value("rule.expression"));
    }

    @Test
    void anotherOrganizationsTasksAreRefused() throws Exception {
        mvc.perform(get("/organizations/other/ai/assist")).andExpect(status().isForbidden());
        mvc.perform(post("/organizations/other/ai/assist/rule.expression").contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"x\"}")).andExpect(status().isForbidden());
        verify(assist, never()).assist(any(), any(), any());
    }

    @Test
    void aTaskRunsOnTheRequestAndAnswersTheResult() throws Exception {
        when(assist.assist(eq("acme"), eq("rule.expression"), any())).thenReturn(new AssistResult("entity.total > 0",
            "Positive.", List.of("Zero is not positive."), List.of(AuthoringIssue.warning("", "Careful")), 2, "pp"));

        mvc.perform(post(RUN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Total must be positive\",\"parameters\":{\"context\":\"Order\"},\"current\":\"old\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidate").value("entity.total > 0"))
            .andExpect(jsonPath("$.explanation").value("Positive."))
            .andExpect(jsonPath("$.assumptions[0]").value("Zero is not positive."))
            .andExpect(jsonPath("$.issues[0].severity").value("WARNING"))
            .andExpect(jsonPath("$.rounds").value(2))
            .andExpect(jsonPath("$.model").value("pp"));

        ArgumentCaptor<AssistRequest> request = ArgumentCaptor.forClass(AssistRequest.class);
        verify(assist).assist(eq("acme"), eq("rule.expression"), request.capture());
        assertThat(request.getValue()).isEqualTo(new AssistRequest("Total must be positive", Map.of("context", "Order"), "old"));
    }

    @Test
    void aMissingBlankOrOversizedTextIsABadRequest() throws Exception {
        for (String body : List.of("{}", "{\"text\":\"  \"}", "{\"text\":\"" + "x".repeat(4001) + "\"}")) {
            mvc.perform(post(RUN).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verify(assist, never()).assist(any(), any(), any());
    }

    @Test
    void anOversizedCurrentValueOrTooManyOrTooLongParametersAreABadRequest() throws Exception {
        String current = "{\"text\":\"x\",\"current\":\"" + "y".repeat(20_001) + "\"}";
        String longValue = "{\"text\":\"x\",\"parameters\":{\"a\":\"" + "y".repeat(501) + "\"}}";
        StringBuilder many = new StringBuilder("{\"text\":\"x\",\"parameters\":{");
        for (int i = 0; i <= 20; i++) {
            many.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":\"v\"");
        }
        many.append("}}");
        for (String body : List.of(current, longValue, many.toString(), "{\"text\":\"x\",\"parameters\":{\"a\":null}}")) {
            mvc.perform(post(RUN).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void theModulesRefusalsKeepTheirStatusAndTranslationKey() throws Exception {
        String body = "{\"text\":\"x\"}";
        when(assist.assist(any(), eq("rule.expression"), any())).thenThrow(new AssistantNotConfiguredException("acme"));
        mvc.perform(post(RUN).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.errorId").value("ai.assist.not-configured"));

        when(assist.assist(any(), eq("rule.expression"), any())).thenThrow(new AssistantQuotaExceededException("acme"));
        mvc.perform(post(RUN).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.errorId").value("ai.assist.quota-exceeded"));

        when(assist.assist(any(), eq("rule.expression"), any())).thenThrow(new UnknownAssistTaskException("rule.expression"));
        mvc.perform(post(RUN).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorId").value("ai.assist.unknown-task"));

        when(assist.assist(any(), eq("rule.expression"), any())).thenThrow(new AssistUnavailableException(new RuntimeException("x")));
        mvc.perform(post(RUN).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.errorId").value("ai.assist.model-unavailable"));
    }
}
