package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.ConversationView;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.definition.domain.SubjectType;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The conversations of one subject that the caller may read. The ones they may not are left out, not
 * reported, and so are conversations whose definition is no longer released or whose subject is gone.
 */
@Service
@Transactional(readOnly = true)
public class FindConversations {

    private final ConversationRepository conversations;
    private final ResolveAccess access;
    private final OrganizationGuard guard;
    private final ReadMarkers markers;
    private final CurrentPrincipal principal;

    public FindConversations(ConversationRepository conversations, ResolveAccess access, OrganizationGuard guard,
                             ReadMarkers markers, CurrentPrincipal principal) {
        this.conversations = conversations;
        this.access = access;
        this.guard = guard;
        this.markers = markers;
        this.principal = principal;
    }

    /**
     * @param subjectDefinition narrows to one subject definition; null for any
     * @param definitionKey     narrows to one chat definition; null for any
     */
    public List<ConversationView> find(String orgKey, SubjectType subjectType, String subjectId,
                                       String subjectDefinition, String definitionKey) {
        guard.requireAccess(orgKey);
        List<ConversationView> readable = new ArrayList<>();
        for (Conversation conversation : conversations.findByOrgKeyAndSubjectTypeAndSubjectIdOrderByCreatedAtAsc(
                orgKey, subjectType, subjectId)) {
            if (matches(conversation, subjectDefinition, definitionKey)) {
                readableView(conversation).ifPresent(readable::add);
            }
        }
        Map<UUID, Long> lastRead = principal.subject()
                .map(user -> markers.lastRead(user, readable.stream().map(view -> view.conversation().getId()).toList()))
                .orElseGet(Map::of);
        return readable.stream()
                .map(view -> new ConversationView(view.conversation(), view.access(), lastRead.get(view.conversation().getId())))
                .toList();
    }

    private java.util.Optional<ConversationView> readableView(Conversation conversation) {
        try {
            ConversationAccess resolved = access.of(conversation);
            return resolved.canRead() ? java.util.Optional.of(new ConversationView(conversation, resolved)) : java.util.Optional.empty();
        } catch (ConversationNotFoundException e) {
            return java.util.Optional.empty();
        }
    }

    private static boolean matches(Conversation conversation, String subjectDefinition, String definitionKey) {
        return (subjectDefinition == null || subjectDefinition.equals(conversation.getSubjectDefinition()))
                && (definitionKey == null || definitionKey.equals(conversation.getDefinitionKey()));
    }
}
