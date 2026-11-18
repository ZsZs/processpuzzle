package com.processpuzzle.ai.assistant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.processpuzzle.ai.domain.AiProvider;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class OrganizationAiSubscriptionEndpointTest {

    private static final String PATH = "/organizations/acme/ai-subscription";
    private static final OrganizationAiSubscriptions.View VIEW = new OrganizationAiSubscriptions.View(
        AiProvider.MISTRAL, "mistral-large-latest", null, Instant.parse("2026-11-18T10:00:00Z"), "Ada");

    private OrganizationAiSubscriptions subscriptions;
    private OrganizationGuard guard;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        subscriptions = mock(OrganizationAiSubscriptions.class);
        guard = mock(OrganizationGuard.class);
        AssistantAccess access = mock(AssistantAccess.class);
        when(access.principal()).thenReturn("Ada");
        mvc = MockMvcBuilders.standaloneSetup(new OrganizationAiSubscriptionEndpoint(subscriptions, guard, access)).build();
    }

    @Test
    void anAdministratorReadsTheSubscriptionWithoutItsKey() throws Exception {
        when(subscriptions.find("acme")).thenReturn(Optional.of(VIEW));

        mvc.perform(get(PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("MISTRAL"))
            .andExpect(jsonPath("$.model").value("mistral-large-latest"))
            .andExpect(jsonPath("$.keySet").value(true))
            .andExpect(jsonPath("$.apiKey").doesNotExist());
        verify(guard).requireAdministration("acme");
    }

    @Test
    void anOrganizationWithoutASubscriptionIsNotFound() throws Exception {
        when(subscriptions.find("acme")).thenReturn(Optional.empty());

        mvc.perform(get(PATH)).andExpect(status().isNotFound());
    }

    @Test
    void anAdministratorSavesTheSubscription() throws Exception {
        when(subscriptions.save(any(), any(), any())).thenReturn(VIEW);

        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"MISTRAL\",\"model\":\"mistral-large-latest\",\"apiKey\":\"sk-secret\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updatedBy").value("Ada"))
            .andExpect(jsonPath("$.apiKey").doesNotExist());
        verify(guard).requireAdministration("acme");
        verify(subscriptions).save("acme",
            new OrganizationAiSubscriptions.Change(AiProvider.MISTRAL, "mistral-large-latest", null, "sk-secret"), "Ada");
    }

    @Test
    void aRefusedSubscriptionIsABadRequestWithTheReason() throws Exception {
        when(subscriptions.save(any(), any(), any())).thenThrow(new IllegalArgumentException("The API key is required"));

        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"MISTRAL\",\"model\":\"m\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void anAdministratorListsTheModelsOfAProvider() throws Exception {
        when(subscriptions.listModels("acme", AiProvider.MISTRAL, null, "sk-typed")).thenReturn(java.util.List.of("a", "b"));

        mvc.perform(post(PATH + "/models").contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"MISTRAL\",\"apiKey\":\"sk-typed\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.models[0]").value("a"))
            .andExpect(jsonPath("$.models[1]").value("b"));
        verify(guard).requireAdministration("acme");
    }

    @Test
    void aRefusedRequestToListIsBadRequestAndAFailingProviderIsBadGateway() throws Exception {
        when(subscriptions.listModels(any(), any(), any(), any())).thenThrow(new IllegalArgumentException("Enter the API key"))
            .thenThrow(new ModelLister.ModelListException("The provider rejected the API key"));

        mvc.perform(post(PATH + "/models").contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"MISTRAL\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(PATH + "/models").contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"MISTRAL\"}"))
            .andExpect(status().isBadGateway());
    }

    @Test
    void anAdministratorRemovesTheSubscription() throws Exception {
        mvc.perform(delete(PATH)).andExpect(status().isNoContent());

        verify(guard).requireAdministration("acme");
        verify(subscriptions).delete("acme");
    }

    @Test
    void aCallerWhoIsNotAnAdministratorIsForbiddenAndTouchesNothing() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(guard).requireAdministration("acme");

        mvc.perform(get(PATH)).andExpect(status().isForbidden());
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"MISTRAL\",\"model\":\"m\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete(PATH)).andExpect(status().isForbidden());
        mvc.perform(post(PATH + "/models").contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"MISTRAL\"}"))
            .andExpect(status().isForbidden());
        verify(subscriptions, never()).listModels(any(), any(), any(), any());
        verify(subscriptions, never()).find(any());
        verify(subscriptions, never()).save(any(), any(), any());
        verify(subscriptions, never()).delete(any());
    }
}
