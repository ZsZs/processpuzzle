package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.outbound.PermitAllSubjectResolverPort;
import com.processpuzzle.chat.conversation.usecases.outbound.SubjectInfo;
import com.processpuzzle.chat.conversation.usecases.outbound.SubjectResolverPort;
import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatLifecycle;
import com.processpuzzle.chat.definition.usecases.inbound.FindChatDefinition;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place that decides what a caller may do with a conversation. Computed afresh on every call from
 * the released definition, the caller's roles and the subject's state, so a role change or a state
 * transition takes effect at once — nothing is copied into the conversation.
 *
 * <p>The policy only narrows access: a caller needs to be a member of the organization and the subject has
 * to be visible to them before any chat role counts.
 */
@Service
@Transactional(readOnly = true)
public class ResolveAccess {

    private final FindChatDefinition definitions;
    private final SubjectResolverPort subjects;
    private final OrganizationGuard guard;
    private final CurrentPrincipal principal;

    public ResolveAccess(FindChatDefinition definitions, ObjectProvider<SubjectResolverPort> subjects,
                         OrganizationGuard guard, CurrentPrincipal principal) {
        this.definitions = definitions;
        this.subjects = subjects.getIfUnique(PermitAllSubjectResolverPort::new);
        this.guard = guard;
        this.principal = principal;
    }

    /**
     * The access to an existing conversation.
     *
     * @throws ConversationNotFoundException when its definition is no longer released or its subject is gone —
     *         a conversation nobody can reach is as good as absent. A caller who may not read it gets the
     *         access with {@code canRead} false and decides what to answer.
     */
    public ConversationAccess of(Conversation conversation) {
        ChatDefinition definition = definitions.released(conversation.getOrgKey(), conversation.getDefinitionKey())
                .orElseThrow(ConversationNotFoundException::conversation);
        SubjectInfo subject = subjects.resolve(conversation.getOrgKey(), conversation.getSubjectType(),
                        conversation.getSubjectDefinition(), conversation.getSubjectId())
                .orElseThrow(ConversationNotFoundException::conversation);
        return resolve(definition, subject, conversation);
    }

    /**
     * The access to a conversation of {@code definition} on {@code subject}; {@code conversation} is null when
     * it does not exist yet, as when opening.
     */
    public ConversationAccess resolve(ChatDefinition definition, SubjectInfo subject, Conversation conversation) {
        boolean owner = isOwner(definition, subject);
        boolean canRead = owner || guard.isVisible(definition.getReadRoles());
        boolean mayWrite = canRead && (owner || guard.isVisible(definition.getWriteRoles()));
        boolean mayCreate = canRead && (owner || guard.isVisible(definition.getCreateRoles()));
        boolean open = conversation == null || conversation.isOpen();
        boolean stateAllows = definition.getLifecycle() != ChatLifecycle.STATE_BOUND
                || definition.getWritableStates().contains(subject.state());
        return new ConversationAccess(canRead, mayWrite, mayCreate, open && stateAllows);
    }

    private boolean isOwner(ChatDefinition definition, SubjectInfo subject) {
        if (!definition.isIncludeSubjectOwner() || subject.owner() == null) {
            return false;
        }
        Optional<String> caller = principal.subject();
        return caller.isPresent() && caller.get().equals(subject.owner());
    }
}
