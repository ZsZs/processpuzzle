package com.processpuzzle.chat.definition.adapters.inbound;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.processpuzzle.chat.common.ChatApiExceptionHandler;
import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionTest;
import com.processpuzzle.chat.definition.domain.ChatLifecycle;
import com.processpuzzle.chat.definition.domain.Multiplicity;
import com.processpuzzle.chat.definition.usecases.exception.ChatDefinitionNotFoundException;
import com.processpuzzle.chat.definition.usecases.exception.InvalidChatDefinitionException;
import com.processpuzzle.chat.definition.usecases.exception.StaleChatDefinitionException;
import com.processpuzzle.chat.definition.usecases.inbound.DeleteChatDefinition;
import com.processpuzzle.chat.definition.usecases.inbound.FindChatDefinition;
import com.processpuzzle.chat.definition.usecases.inbound.SaveChatDefinition;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.PendingChange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatDefinitionEndpointTest {

    private static final String BASE = "/organizations/org-1/chat/definitions";
    private static final String BODY = """
            {"name":"Order discussion","subjectType":"ENTITY","subjectDefinition":"order",
             "multiplicity":"MANY","lifecycle":"STATE_BOUND","writableStates":["Running"],
             "readRoles":["sailor"],"createRoles":["judge"],"includeSubjectOwner":true,"retentionDays":30,"version":2}
            """;

    private FindChatDefinition find;
    private SaveChatDefinition save;
    private DeleteChatDefinition delete;
    private ChatDefinitionReleaseParticipant releaseParticipant;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        find = mock(FindChatDefinition.class);
        save = mock(SaveChatDefinition.class);
        delete = mock(DeleteChatDefinition.class);
        releaseParticipant = mock(ChatDefinitionReleaseParticipant.class);
        mvc = MockMvcBuilders.standaloneSetup(new ChatDefinitionEndpoint(find, save, delete, new ChatDefinitionMapper(), releaseParticipant))
                .setControllerAdvice(new ChatApiExceptionHandler())
                .build();
    }

    private static ChatDefinition stored() {
        ChatDefinition definition = ChatDefinitionTest.definition("order-discussion");
        definition.setDescription("One chat per order");
        definition.setReadRoles(new ArrayList<>(List.of("sailor")));
        definition.setVersion(2L);
        definition.setCreatedAt(Instant.parse("2026-11-15T10:00:00Z"));
        definition.setUpdatedAt(Instant.parse("2026-11-15T11:00:00Z"));
        return definition;
    }

    @Test
    void listsTheDraftsByDefaultAndTheReleasedRowsOnRequest() throws Exception {
        when(find.findAll("org-1", Channel.DRAFT)).thenReturn(List.of(stored()));
        when(find.findAll("org-1", Channel.RELEASED)).thenReturn(List.of(stored(), stored()));

        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").value("order-discussion"))
                .andExpect(jsonPath("$[0].name").value("Order discussion"))
                .andExpect(jsonPath("$[0].subjectType").value("ENTITY"))
                .andExpect(jsonPath("$[0].subjectDefinition").value("order"))
                .andExpect(jsonPath("$[0].multiplicity").value("SINGLE"))
                .andExpect(jsonPath("$[0].lifecycle").value("ON_DEMAND"))
                .andExpect(jsonPath("$[0].readRoles[0]").value("sailor"))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].createdAt").exists());
        mvc.perform(get(BASE).param("revision", "released")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getsOneDefinitionAndAnswers404ForAnUnknownKey() throws Exception {
        when(find.find("org-1", "order-discussion", Channel.DRAFT)).thenReturn(stored());
        when(find.find("org-1", "nope", Channel.DRAFT)).thenThrow(new ChatDefinitionNotFoundException("org-1", "nope"));

        mvc.perform(get(BASE + "/order-discussion")).andExpect(status().isOk()).andExpect(jsonPath("$.key").value("order-discussion"));
        mvc.perform(get(BASE + "/nope")).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorId").value("chat.definition.not-found"));
    }

    @Test
    void anUnknownRevisionIs400() throws Exception {
        mvc.perform(get(BASE).param("revision", "yesterday"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorId").value("chat.definition.invalid-revision"));
        mvc.perform(get(BASE + "/order-discussion").param("revision", "yesterday")).andExpect(status().isBadRequest());
    }

    @Test
    void savesWhatTheBodyDescribesUnderTheKeyOfThePath() throws Exception {
        when(save.save(eq("org-1"), eq("order-discussion"), any(ChatDefinition.class))).thenReturn(stored());

        mvc.perform(put(BASE + "/order-discussion").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.key").value("order-discussion"));

        ArgumentCaptor<ChatDefinition> desired = ArgumentCaptor.forClass(ChatDefinition.class);
        verify(save).save(eq("org-1"), eq("order-discussion"), desired.capture());
        ChatDefinition definition = desired.getValue();
        org.assertj.core.api.Assertions.assertThat(definition.getMultiplicity()).isEqualTo(Multiplicity.MANY);
        org.assertj.core.api.Assertions.assertThat(definition.getLifecycle()).isEqualTo(ChatLifecycle.STATE_BOUND);
        org.assertj.core.api.Assertions.assertThat(definition.getWritableStates()).containsExactly("Running");
        org.assertj.core.api.Assertions.assertThat(definition.getReadRoles()).containsExactly("sailor");
        org.assertj.core.api.Assertions.assertThat(definition.getCreateRoles()).containsExactly("judge");
        org.assertj.core.api.Assertions.assertThat(definition.isIncludeSubjectOwner()).isTrue();
        org.assertj.core.api.Assertions.assertThat(definition.getRetentionDays()).isEqualTo(30);
        org.assertj.core.api.Assertions.assertThat(definition.getVersion()).isEqualTo(2L);
    }

    @Test
    void aBodyWithoutMultiplicityOrLifecycleGetsTheDefaults() throws Exception {
        when(save.save(eq("org-1"), eq("order-discussion"), any(ChatDefinition.class))).thenReturn(stored());

        mvc.perform(put(BASE + "/order-discussion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Chat\",\"subjectType\":\"ENTITY\",\"subjectDefinition\":\"order\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChatDefinition> desired = ArgumentCaptor.forClass(ChatDefinition.class);
        verify(save).save(eq("org-1"), eq("order-discussion"), desired.capture());
        org.assertj.core.api.Assertions.assertThat(desired.getValue().getMultiplicity()).isEqualTo(Multiplicity.SINGLE);
        org.assertj.core.api.Assertions.assertThat(desired.getValue().getLifecycle()).isEqualTo(ChatLifecycle.ON_DEMAND);
    }

    @Test
    void anInvalidDefinitionIs400AndAStaleOneIs409() throws Exception {
        when(save.save(eq("org-1"), eq("bad"), any(ChatDefinition.class)))
                .thenThrow(new InvalidChatDefinitionException("bad", List.of("'name' is required")));
        when(save.save(eq("org-1"), eq("old"), any(ChatDefinition.class))).thenThrow(new StaleChatDefinitionException("old"));

        mvc.perform(put(BASE + "/bad").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorId").value("chat.definition.invalid"));
        mvc.perform(put(BASE + "/old").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorId").value("chat.definition.stale-write"));
    }

    @Test
    void aDraftCarriesWhereItStandsAgainstItsReleasedRow() throws Exception {
        when(find.findAll("org-1", Channel.DRAFT)).thenReturn(List.of(stored()));
        when(releaseParticipant.drafts("org-1")).thenReturn(java.util.Set.of("order-discussion"));
        when(releaseParticipant.pending("org-1")).thenReturn(List.of(
                new PendingChange("chat", "order-discussion", PendingChange.Change.CHANGED, "f")));

        mvc.perform(get(BASE)).andExpect(jsonPath("$[0].releaseState").value("CHANGED"));
    }

    @Test
    void aReleasedRowHasNoReleaseState() throws Exception {
        when(find.findAll("org-1", Channel.RELEASED)).thenReturn(List.of(stored()));

        mvc.perform(get(BASE).param("revision", "released")).andExpect(jsonPath("$[0].releaseState").doesNotExist());
    }

    @Test
    void deletesTheDraftAnswers204AndAnUnknownKeyIs404() throws Exception {
        org.mockito.Mockito.doThrow(new ChatDefinitionNotFoundException("org-1", "nope")).when(delete).delete("org-1", "nope");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(BASE + "/order-discussion")).andExpect(status().isNoContent());
        verify(delete).delete("org-1", "order-discussion");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(BASE + "/nope")).andExpect(status().isNotFound());
    }
}
