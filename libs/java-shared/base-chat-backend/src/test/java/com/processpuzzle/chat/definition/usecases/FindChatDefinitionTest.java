package com.processpuzzle.chat.definition.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.domain.ChatDefinitionTest;
import com.processpuzzle.chat.definition.usecases.exception.ChatDefinitionNotFoundException;
import com.processpuzzle.chat.definition.usecases.inbound.FindChatDefinition;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FindChatDefinitionTest {

    private ChatDefinitionRepository repository;
    private OrganizationGuard guard;
    private FindChatDefinition find;

    @BeforeEach
    void setUp() {
        repository = mock(ChatDefinitionRepository.class);
        guard = mock(OrganizationGuard.class);
        find = new FindChatDefinition(repository, guard);
    }

    @Test
    void readingADraftNeedsTheDesignRightAndReadingTheReleasedRowOnlyMembership() {
        ChatDefinition draft = ChatDefinitionTest.definition("order-discussion");
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "order-discussion", Channel.DRAFT)).thenReturn(Optional.of(draft));
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "order-discussion", Channel.RELEASED)).thenReturn(Optional.of(draft));

        assertThat(find.find("org-1", "order-discussion", Channel.DRAFT)).isSameAs(draft);
        verify(guard).requireDesign("org-1");

        assertThat(find.find("org-1", "order-discussion", Channel.RELEASED)).isSameAs(draft);
        verify(guard).requireAccess("org-1");
    }

    @Test
    void anUnknownKeyIsNotFound() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "nope", Channel.DRAFT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> find.find("org-1", "nope", Channel.DRAFT)).isInstanceOf(ChatDefinitionNotFoundException.class);
    }

    @Test
    void listsAnOrganizationsDefinitionsOfOneChannel() {
        ChatDefinition draft = ChatDefinitionTest.definition("order-discussion");
        when(repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc("org-1", Channel.RELEASED)).thenReturn(List.of(draft));

        assertThat(find.findAll("org-1", Channel.RELEASED)).containsExactly(draft);
    }

    @Test
    void refusesANonMemberOutright() {
        doThrow(new OrganizationAccessDeniedException("org-2")).when(guard).requireAccess("org-2");

        assertThatThrownBy(() -> find.findAll("org-2", Channel.RELEASED)).isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void theReleasedLookupForInternalCallersToleratesAMissingKeyAndRow() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "gone", Channel.RELEASED)).thenReturn(Optional.empty());

        assertThat(find.released("org-1", null)).isEmpty();
        assertThat(find.released("org-1", "gone")).isEmpty();
    }
}
