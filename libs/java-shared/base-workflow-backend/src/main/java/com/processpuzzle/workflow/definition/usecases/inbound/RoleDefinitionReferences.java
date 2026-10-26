package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.RoleReference;
import com.processpuzzle.core.tenancy.RoleReferenceContributor;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The roles that exist because a workflow role definition has their id.
 *
 * <p>This is what makes a role {@code workflow}-managed in the organization's role catalog: such a role
 * is projected into the identity provider from the designer and is edited and deleted there, so the
 * administrator's role screen must not offer to. Both channels are read, because the released
 * definition is what the running application still uses after a draft is deleted.
 */
@Component
public class RoleDefinitionReferences implements RoleReferenceContributor {

    static final String FEATURE = "workflow";

    private final RoleDefinitionRepository roles;

    public RoleDefinitionReferences(RoleDefinitionRepository roles) {
        this.roles = roles;
    }

    @Override
    public Map<String, List<RoleReference>> referencedRoles(String orgKey) {
        Map<String, Set<RoleReference>> referenced = new LinkedHashMap<>();
        for (Channel channel : Channel.values()) {
            for (RoleDefinition definition : roles.findByOrgKeyAndChannel(orgKey, channel)) {
                referenced.computeIfAbsent(definition.getId(), id -> new LinkedHashSet<>()).add(
                        new RoleReference(FEATURE, RoleReference.ROLE_DEFINITION, definition.getId(), definition.getName()));
            }
        }
        Map<String, List<RoleReference>> result = new LinkedHashMap<>();
        referenced.forEach((role, references) -> result.put(role, List.copyOf(references)));
        return result;
    }
}
