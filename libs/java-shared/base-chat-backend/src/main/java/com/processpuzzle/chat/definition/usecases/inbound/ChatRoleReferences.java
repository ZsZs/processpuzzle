package com.processpuzzle.chat.definition.usecases.inbound;

import com.processpuzzle.chat.definition.domain.ChatDefinition;
import com.processpuzzle.chat.definition.domain.ChatDefinitionRepository;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.RoleReference;
import com.processpuzzle.core.tenancy.RoleReferenceContributor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The roles chat definitions name as readers, writers and openers, so that the organization's role catalog can tell
 * an administrator what still depends on a role before it is deleted — a chat whose only write role is gone can
 * be written to by nobody.
 *
 * <p>Both the draft and the released row count: a role the running application still uses is as referenced as one a
 * designer has only drafted. A definition that has both is reported once.
 */
@Component
@Transactional(readOnly = true)
public class ChatRoleReferences implements RoleReferenceContributor {

    static final String FEATURE = "chat";
    static final String READ = "read-role";
    static final String WRITE = "write-role";
    static final String CREATE = "create-role";

    private final ChatDefinitionRepository repository;

    public ChatRoleReferences(ChatDefinitionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, List<RoleReference>> referencedRoles(String orgKey) {
        Map<String, ChatDefinition> byKey = new LinkedHashMap<>();
        // The released row first, so that a draft that has moved on is what is named when both exist.
        for (Channel channel : List.of(Channel.RELEASED, Channel.DRAFT)) {
            repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(orgKey, channel)
                    .forEach(definition -> byKey.merge(definition.getDefinitionKey(), definition, (released, draft) -> draft));
        }
        Map<String, List<RoleReference>> referenced = new LinkedHashMap<>();
        byKey.values().forEach(definition -> {
            add(referenced, definition.getReadRoles(), READ, definition);
            add(referenced, definition.getWriteRoles(), WRITE, definition);
            add(referenced, definition.getCreateRoles(), CREATE, definition);
        });
        return referenced;
    }

    private static void add(Map<String, List<RoleReference>> referenced, Collection<String> roles, String kind, ChatDefinition definition) {
        for (String role : roles) {
            referenced.computeIfAbsent(role, name -> new ArrayList<>())
                    .add(new RoleReference(FEATURE, kind, definition.getDefinitionKey(), definition.getName()));
        }
    }
}
