package com.processpuzzle.chat.definition.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.chat.definition.domain.ChatDefinitionTest;
import com.processpuzzle.chat.definition.usecases.exception.InvalidChatDefinitionException;
import com.processpuzzle.chat.definition.usecases.exception.StaleChatDefinitionException;
import com.processpuzzle.chat.definition.usecases.inbound.SaveChatDefinition;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SaveChatDefinitionTest {

    private ChatDefinitionRepository repository;
    private OrganizationGuard guard;
    private SaveChatDefinition save;

    @BeforeEach
    void setUp() {
        repository = mock(ChatDefinitionRepository.class);
        guard = mock(OrganizationGuard.class);
        when(repository.save(any(ChatDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        save = new SaveChatDefinition(repository, guard);
    }

    private static ChatDefinition desired() {
        ChatDefinition desired = ChatDefinitionTest.definition("ignored");
        desired.setOrgKey("another-org");
        desired.setReadRoles(new ArrayList<>(List.of("sailor")));
        desired.setCreateRoles(new ArrayList<>(List.of("dispatcher")));
        return desired;
    }

    @Test
    void createsTheDraftUnderTheCallersOrganizationAndTheKeyOfThePath() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "order-discussion", Channel.DRAFT)).thenReturn(Optional.empty());
        ChatDefinition input = desired();
        input.setVersion(9L);

        ChatDefinition saved = save.save("org-1", "order-discussion", input);

        assertThat(saved.getOrgKey()).isEqualTo("org-1");
        assertThat(saved.getDefinitionKey()).isEqualTo("order-discussion");
        assertThat(saved.getChannel()).isEqualTo(Channel.DRAFT);
        assertThat(saved.getVersion()).isNull();
        inOrder(guard, repository).verify(guard).requireDesign("org-1");
    }

    @Test
    void announcesTheRolesItNamesToTheRoleCatalog() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(any(), any(), any())).thenReturn(Optional.empty());

        save.save("org-1", "order-discussion", desired());

        verify(guard).declareRoles(eq("org-1"), eq("chat definition 'order-discussion'"), eq(Set.of("sailor", "dispatcher")));
    }

    @Test
    void replacesTheExistingDraftKeepingItsIdentity() {
        ChatDefinition existing = ChatDefinitionTest.definition("order-discussion");
        existing.setVersion(3L);
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "order-discussion", Channel.DRAFT)).thenReturn(Optional.of(existing));
        ChatDefinition input = desired();
        input.setName("Renamed");
        input.setVersion(3L);

        ChatDefinition saved = save.save("org-1", "order-discussion", input);

        assertThat(saved).isSameAs(existing);
        assertThat(saved.getName()).isEqualTo("Renamed");
        assertThat(saved.getReadRoles()).containsExactly("sailor");
    }

    @Test
    void refusesAWriteBasedOnAStaleVersionButOverwritesWhenNoneIsGiven() {
        ChatDefinition existing = ChatDefinitionTest.definition("order-discussion");
        existing.setVersion(4L);
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel("org-1", "order-discussion", Channel.DRAFT)).thenReturn(Optional.of(existing));
        ChatDefinition stale = desired();
        stale.setVersion(3L);

        assertThatThrownBy(() -> save.save("org-1", "order-discussion", stale)).isInstanceOf(StaleChatDefinitionException.class);
        verify(repository, never()).save(any());

        assertThat(save.save("org-1", "order-discussion", desired()).getName()).isEqualTo("Order discussion");
    }

    @Test
    void refusesAnInvalidDefinitionAndStoresNothing() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(any(), any(), any())).thenReturn(Optional.empty());
        ChatDefinition invalid = desired();
        invalid.setName(" ");

        assertThatThrownBy(() -> save.save("org-1", "order-discussion", invalid))
                .isInstanceOfSatisfying(InvalidChatDefinitionException.class,
                        e -> assertThat(e.getViolations()).singleElement().asString().contains("'name'"));
        verify(repository, never()).save(any());
        verify(guard, never()).declareRoles(any(), any(), any());
    }

    @Test
    void refusesAMalformedKeyFromThePath() {
        when(repository.findByOrgKeyAndDefinitionKeyAndChannel(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> save.save("org-1", "Order Discussion", desired()))
                .isInstanceOf(InvalidChatDefinitionException.class).hasMessageContaining("'key'");
    }

    @Test
    void needsTheDesignRight() {
        doThrow(new OrganizationAccessDeniedException("org-1")).when(guard).requireDesign("org-1");

        assertThatThrownBy(() -> save.save("org-1", "order-discussion", desired()))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        verify(repository, never()).save(any());
    }
}
