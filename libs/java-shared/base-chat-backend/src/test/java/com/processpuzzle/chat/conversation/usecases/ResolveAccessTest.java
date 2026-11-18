package com.processpuzzle.chat.conversation.usecases;

import static com.processpuzzle.chat.conversation.ConversationFixtures.ORG;
import static com.processpuzzle.chat.conversation.ConversationFixtures.conversation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.domain.ConversationStatus;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.inbound.ResolveAccess;
import com.processpuzzle.chat.conversation.usecases.outbound.SubjectInfo;
import com.processpuzzle.chat.conversation.usecases.outbound.SubjectResolverPort;
import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionTest;
import com.processpuzzle.chat.definition.domain.ChatLifecycle;
import com.processpuzzle.chat.definition.usecases.inbound.FindChatDefinition;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ResolveAccessTest {

    private FindChatDefinition definitions;
    private SubjectResolverPort subjects;
    private OrganizationGuard guard;
    private CurrentPrincipal principal;
    private ResolveAccess access;
    private ChatDefinition definition;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        definitions = mock(FindChatDefinition.class);
        subjects = mock(SubjectResolverPort.class);
        guard = mock(OrganizationGuard.class);
        principal = mock(CurrentPrincipal.class);
        ObjectProvider<SubjectResolverPort> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(org.mockito.ArgumentMatchers.any())).thenReturn(subjects);
        access = new ResolveAccess(definitions, provider, guard, principal);

        definition = ChatDefinitionTest.definition("order-discussion");
        when(definitions.released(ORG, "order-discussion")).thenReturn(Optional.of(definition));
        when(subjects.resolve(ORG, definition.getSubjectType(), "order", conversation().getSubjectId()))
                .thenReturn(Optional.of(new SubjectInfo(null, "Running")));
        when(principal.subject()).thenReturn(Optional.of("sub-caller"));
        // No role list is satisfied unless a test says so; an empty one means "any member".
        when(guard.isVisible(List.of())).thenReturn(true);
    }

    private void roles(List<String> roles, boolean held) {
        when(guard.isVisible(roles)).thenReturn(held);
    }

    @Test
    void emptyRoleListsAdmitAnyMemberToReadWriteAndOpen() {
        ConversationAccess resolved = access.of(conversation());

        assertThat(resolved).isEqualTo(new ConversationAccess(true, true, true, true));
        assertThat(resolved.canWrite()).isTrue();
    }

    @Test
    void aCallerWithoutAReadRoleCannotReadAndSoCannotWriteOrOpenEither() {
        definition.setReadRoles(new ArrayList<>(List.of("sailor")));
        definition.setWriteRoles(new ArrayList<>(List.of("sailor")));
        roles(List.of("sailor"), false);

        ConversationAccess resolved = access.of(conversation());

        assertThat(resolved.canRead()).isFalse();
        assertThat(resolved.mayWrite()).isFalse();
        assertThat(resolved.mayCreate()).isFalse();
        assertThat(resolved.canWrite()).isFalse();
    }

    @Test
    void aReaderWithoutAWriteRoleMayReadButNotWrite() {
        definition.setWriteRoles(new ArrayList<>(List.of("judge")));
        roles(List.of("judge"), false);

        ConversationAccess resolved = access.of(conversation());

        assertThat(resolved.canRead()).isTrue();
        assertThat(resolved.mayWrite()).isFalse();
        assertThat(resolved.canWrite()).isFalse();
        assertThat(resolved.mayCreate()).isTrue();
    }

    @Test
    void openingNeedsACreateRoleIndependentlyOfWriting() {
        definition.setCreateRoles(new ArrayList<>(List.of("dispatcher")));
        roles(List.of("dispatcher"), false);

        ConversationAccess resolved = access.of(conversation());

        assertThat(resolved.mayWrite()).isTrue();
        assertThat(resolved.mayCreate()).isFalse();
    }

    @Test
    void aClosedOrArchivedConversationIsNotWritableWhateverTheRoles() {
        Conversation closed = conversation();
        closed.setStatus(ConversationStatus.CLOSED);
        Conversation archived = conversation();
        archived.setStatus(ConversationStatus.ARCHIVED);

        assertThat(access.of(closed)).satisfies(resolved -> {
            assertThat(resolved.canRead()).isTrue();
            assertThat(resolved.mayWrite()).isTrue();
            assertThat(resolved.writable()).isFalse();
            assertThat(resolved.canWrite()).isFalse();
        });
        assertThat(access.of(archived).writable()).isFalse();
    }

    @Test
    void aStateBoundChatIsWritableOnlyInTheListedStates() {
        definition.setLifecycle(ChatLifecycle.STATE_BOUND);
        definition.setWritableStates(new ArrayList<>(List.of("Running", "Protest")));

        assertThat(access.of(conversation()).writable()).isTrue();

        definition.setWritableStates(new ArrayList<>(List.of("Finished")));
        assertThat(access.of(conversation()).writable()).isFalse();
    }

    @Test
    void theSubjectsOwnerIsAdmittedWhateverTheirRolesWhenTheDefinitionSaysSo() {
        definition.setReadRoles(new ArrayList<>(List.of("sailor")));
        definition.setWriteRoles(new ArrayList<>(List.of("sailor")));
        definition.setCreateRoles(new ArrayList<>(List.of("sailor")));
        roles(List.of("sailor"), false);
        when(subjects.resolve(ORG, definition.getSubjectType(), "order", conversation().getSubjectId()))
                .thenReturn(Optional.of(new SubjectInfo("sub-caller", "Running")));

        assertThat(access.of(conversation()).canRead()).isFalse();

        definition.setIncludeSubjectOwner(true);
        assertThat(access.of(conversation())).isEqualTo(new ConversationAccess(true, true, true, true));
    }

    @Test
    void anotherUserIsNotTheOwnerAndASubjectWithoutOneHasNoOwnerToAdmit() {
        definition.setReadRoles(new ArrayList<>(List.of("sailor")));
        definition.setIncludeSubjectOwner(true);
        roles(List.of("sailor"), false);

        when(subjects.resolve(ORG, definition.getSubjectType(), "order", conversation().getSubjectId()))
                .thenReturn(Optional.of(new SubjectInfo("sub-someone-else", null)));
        assertThat(access.of(conversation()).canRead()).isFalse();

        when(subjects.resolve(ORG, definition.getSubjectType(), "order", conversation().getSubjectId()))
                .thenReturn(Optional.of(new SubjectInfo(null, null)));
        assertThat(access.of(conversation()).canRead()).isFalse();
    }

    @Test
    void aConversationWhoseDefinitionIsNoLongerReleasedIsNotFound() {
        when(definitions.released(ORG, "order-discussion")).thenReturn(Optional.empty());
        Conversation stored = conversation();

        assertThatThrownBy(() -> access.of(stored)).isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void aConversationWhoseSubjectIsGoneIsNotFound() {
        when(subjects.resolve(ORG, definition.getSubjectType(), "order", conversation().getSubjectId())).thenReturn(Optional.empty());
        Conversation stored = conversation();

        assertThatThrownBy(() -> access.of(stored)).isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void withoutAnAdapterEverySubjectExists() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SubjectResolverPort> none = mock(ObjectProvider.class);
        when(none.getIfUnique(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            java.util.function.Supplier<SubjectResolverPort> fallback = invocation.getArgument(0);
            return fallback.get();
        });

        ConversationAccess resolved = new ResolveAccess(definitions, none, guard, principal).of(conversation());

        assertThat(resolved.canRead()).isTrue();
    }
}
