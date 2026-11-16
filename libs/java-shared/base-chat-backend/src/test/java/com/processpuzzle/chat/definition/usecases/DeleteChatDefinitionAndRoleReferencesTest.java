package com.processpuzzle.chat.definition.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.domain.ChatDefinitionTest;
import com.processpuzzle.chat.definition.usecases.exception.ChatDefinitionNotFoundException;
import com.processpuzzle.chat.definition.usecases.inbound.ChatRoleReferences;
import com.processpuzzle.chat.definition.usecases.inbound.DeleteChatDefinition;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.core.tenancy.RoleReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Deleting a draft, and telling the role catalog which roles chat definitions still name. */
class DeleteChatDefinitionAndRoleReferencesTest {

    private static final String ORG = "org-1";

    private ChatDefinitionRepository repository;
    private OrganizationGuard guard;

    @BeforeEach
    void setUp() {
        repository = mock(ChatDefinitionRepository.class);
        guard = mock(OrganizationGuard.class);
    }

    private static ChatDefinition roles(String key, List<String> read, List<String> write, List<String> create) {
        ChatDefinition definition = ChatDefinitionTest.definition(key);
        definition.setReadRoles(new ArrayList<>(read));
        definition.setWriteRoles(new ArrayList<>(write));
        definition.setCreateRoles(new ArrayList<>(create));
        return definition;
    }

    // --- delete

    @Test
    void deletesTheDraftAndNeedsTheDesignRight() {
        ChatDefinition draft = ChatDefinitionTest.definition("order-discussion");
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(ORG, "order-discussion", Channel.DRAFT)).thenReturn(Optional.of(draft));

        new DeleteChatDefinition(repository, guard).delete(ORG, "order-discussion");

        verify(guard).requireDesign(ORG);
        verify(repository).delete(draft);
    }

    @Test
    void anUnknownDraftIsNotFoundAndNothingIsDeleted() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(ORG, "nope", Channel.DRAFT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DeleteChatDefinition(repository, guard).delete(ORG, "nope")).isInstanceOf(ChatDefinitionNotFoundException.class);
        verify(repository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void withoutTheDesignRightNothingIsLookedUp() {
        doThrow(new OrganizationAccessDeniedException(ORG)).when(guard).requireDesign(ORG);
        DeleteChatDefinition delete = new DeleteChatDefinition(repository, guard);

        assertThatThrownBy(() -> delete.delete(ORG, "order-discussion")).isInstanceOf(OrganizationAccessDeniedException.class);
        verify(repository, never()).findByOrgKeyAndDefinitionKeyAndChannel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // --- role references

    @Test
    void namesEachRoleWithWhatNamesIt() {
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(ORG, Channel.DRAFT))
                .thenReturn(List.of(roles("order-crew", List.of("sailor", "captain"), List.of("captain"), List.of("judge"))));
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(ORG, Channel.RELEASED)).thenReturn(List.of());

        Map<String, List<RoleReference>> referenced = new ChatRoleReferences(repository).referencedRoles(ORG);

        assertThat(referenced).containsOnlyKeys("sailor", "captain", "judge");
        assertThat(referenced.get("captain")).extracting(RoleReference::kind).containsExactly("read-role", "write-role");
        assertThat(referenced.get("judge")).singleElement().satisfies(reference -> {
            assertThat(reference.feature()).isEqualTo("chat");
            assertThat(reference.kind()).isEqualTo("create-role");
            assertThat(reference.id()).isEqualTo("order-crew");
            assertThat(reference.title()).isEqualTo("Order discussion");
        });
    }

    @Test
    void aRoleOnlyTheReleasedRowStillNamesIsStillReferenced() {
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(ORG, Channel.DRAFT)).thenReturn(List.of());
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(ORG, Channel.RELEASED))
                .thenReturn(List.of(ChatDefinition.releasedCopyOf(roles("order-crew", List.of("sailor"), List.of(), List.of()))));

        assertThat(new ChatRoleReferences(repository).referencedRoles(ORG)).containsOnlyKeys("sailor");
    }

    @Test
    void aDefinitionThatIsBothDraftedAndReleasedIsReportedOnceAndAsTheDraftHasIt() {
        ChatDefinition draft = roles("order-crew", List.of("captain"), List.of(), List.of());
        ChatDefinition released = ChatDefinition.releasedCopyOf(roles("order-crew", List.of("sailor"), List.of(), List.of()));
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(ORG, Channel.DRAFT)).thenReturn(List.of(draft));
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(ORG, Channel.RELEASED)).thenReturn(List.of(released));

        Map<String, List<RoleReference>> referenced = new ChatRoleReferences(repository).referencedRoles(ORG);

        assertThat(referenced).containsOnlyKeys("captain");
    }

    @Test
    void definitionsWithNoRolesNameNone() {
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(ORG, Channel.DRAFT)).thenReturn(List.of(ChatDefinitionTest.definition("open")));
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(ORG, Channel.RELEASED)).thenReturn(List.of());

        assertThat(new ChatRoleReferences(repository).referencedRoles(ORG)).isEmpty();
    }
}
