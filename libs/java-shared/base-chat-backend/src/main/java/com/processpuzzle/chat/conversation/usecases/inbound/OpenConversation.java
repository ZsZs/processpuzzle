package com.processpuzzle.chat.conversation.usecases.inbound;

import com.processpuzzle.chat.conversation.domain.Conversation;
import com.processpuzzle.chat.conversation.domain.ConversationAccess;
import com.processpuzzle.chat.conversation.domain.ConversationRepository;
import com.processpuzzle.chat.conversation.domain.ConversationStatus;
import com.processpuzzle.chat.conversation.domain.ConversationView;
import com.processpuzzle.chat.conversation.usecases.exception.ChatAccessDeniedException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationConflictException;
import com.processpuzzle.chat.conversation.usecases.exception.ConversationNotFoundException;
import com.processpuzzle.chat.conversation.usecases.exception.InvalidChatRequestException;
import com.processpuzzle.chat.conversation.usecases.outbound.PermitAllSubjectResolverPort;
import com.processpuzzle.chat.conversation.usecases.outbound.SubjectInfo;
import com.processpuzzle.chat.conversation.usecases.outbound.SubjectResolverPort;
import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatLifecycle;
import com.processpuzzle.chat.definition.domain.Multiplicity;
import com.processpuzzle.chat.definition.usecases.exception.ChatDefinitionNotFoundException;
import com.processpuzzle.chat.definition.usecases.inbound.FindChatDefinition;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens a conversation on a subject under a released chat definition. The opener follows it from then on.
 *
 * <p>Answers 404 when the definition or the subject is unknown <em>or the caller may not read the chat</em>,
 * 403 when they may read but hold no create role, and 409 when the one conversation of a SINGLE definition
 * exists or the lifecycle does not let anybody open one by hand. A SINGLE definition's uniqueness is also a
 * database constraint, so two concurrent opens end in one conversation and one 409.
 */
@Service
@Transactional
public class OpenConversation {

    static final int MAX_TITLE_LENGTH = 200;

    private final ConversationRepository conversations;
    private final FindChatDefinition definitions;
    private final SubjectResolverPort subjects;
    private final ResolveAccess access;
    private final OrganizationGuard guard;
    private final CurrentPrincipal principal;
    private final ConversationPlatformFacts facts;
    private final ReadMarkers markers;

    public OpenConversation(ConversationRepository conversations, FindChatDefinition definitions,
                            ObjectProvider<SubjectResolverPort> subjects, ResolveAccess access, OrganizationGuard guard,
                            CurrentPrincipal principal, ConversationPlatformFacts facts, ReadMarkers markers) {
        this.conversations = conversations;
        this.definitions = definitions;
        this.subjects = subjects.getIfUnique(PermitAllSubjectResolverPort::new);
        this.access = access;
        this.guard = guard;
        this.principal = principal;
        this.facts = facts;
        this.markers = markers;
    }

    public ConversationView open(String orgKey, String definitionKey, String subjectId, String title) {
        guard.requireAccess(orgKey);
        String opener = principal.subject().orElseThrow(() -> new ChatAccessDeniedException("Opening a conversation needs a signed-in user"));
        if (subjectId == null || subjectId.isBlank()) {
            throw new InvalidChatRequestException("'subjectId' is required");
        }
        ChatDefinition definition = definitions.released(orgKey, definitionKey)
                .orElseThrow(() -> new ChatDefinitionNotFoundException(orgKey, String.valueOf(definitionKey)));
        SubjectInfo subject = subjects.resolve(orgKey, definition.getSubjectType(), definition.getSubjectDefinition(), subjectId)
                .orElseThrow(ConversationNotFoundException::subject);

        ConversationAccess allowed = access.resolve(definition, subject, null);
        if (!allowed.canRead()) {
            throw ConversationNotFoundException.conversation();
        }
        if (!allowed.mayCreate()) {
            throw new ChatAccessDeniedException("Your roles do not allow opening a conversation of '%s'".formatted(definitionKey));
        }
        if (definition.getLifecycle() == ChatLifecycle.AUTO_CREATE) {
            throw new ConversationConflictException(
                    "Conversations of '%s' are created together with their subject, not opened by hand".formatted(definitionKey));
        }

        Conversation conversation = build(orgKey, definition, subjectId, titleFor(definition, title), opener);
        save(conversation, definition);
        facts.opened(conversation);
        long read = markers.advanceTo(orgKey, conversation.getId(), opener, 0);
        return new ConversationView(conversation, access.resolve(definition, subject, conversation), read);
    }

    private Conversation build(String orgKey, ChatDefinition definition, String subjectId, String title, String opener) {
        boolean single = definition.getMultiplicity() == Multiplicity.SINGLE;
        return Conversation.builder()
                .orgKey(orgKey)
                .definitionKey(definition.getDefinitionKey())
                .subjectType(definition.getSubjectType())
                .subjectDefinition(definition.getSubjectDefinition())
                .subjectId(subjectId)
                .title(title)
                .status(ConversationStatus.OPEN)
                .bindingKey(single ? Conversation.bindingKeyOf(definition.getDefinitionKey(), subjectId)
                        : Conversation.bindingKeyOf(definition.getDefinitionKey(), subjectId, title))
                .createdBy(opener)
                .createdByName(principal.displayName().orElse(null))
                .createdAt(Instant.now())
                .build();
    }

    private void save(Conversation conversation, ChatDefinition definition) {
        String bindingKey = conversation.getBindingKey();
        if (conversations.existsByOrgKeyAndBindingKey(conversation.getOrgKey(), bindingKey)) {
            throw alreadyExists(definition, conversation.getTitle());
        }
        try {
            conversations.saveAndFlush(conversation);
        } catch (DataIntegrityViolationException e) {
            // A concurrent open won the race for the same binding.
            throw alreadyExists(definition, conversation.getTitle());
        }
    }

    private static ConversationConflictException alreadyExists(ChatDefinition definition, String title) {
        if (definition.getMultiplicity() == Multiplicity.SINGLE) {
            return new ConversationConflictException("The subject already has its conversation of '%s'".formatted(definition.getDefinitionKey()));
        }
        return new ConversationConflictException("The subject already has a topic titled '%s' in '%s'".formatted(title, definition.getDefinitionKey()));
    }

    private static String titleFor(ChatDefinition definition, String title) {
        if (definition.getMultiplicity() == Multiplicity.SINGLE) {
            return null;
        }
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidChatRequestException("A topic needs a title");
        }
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new InvalidChatRequestException("A title is at most %d characters".formatted(MAX_TITLE_LENGTH));
        }
        return trimmed;
    }
}
