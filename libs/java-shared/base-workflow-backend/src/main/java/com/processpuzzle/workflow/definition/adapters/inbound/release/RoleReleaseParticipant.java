package com.processpuzzle.workflow.definition.adapters.inbound.release;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.workflow.definition.domain.RoleDefinition;
import com.processpuzzle.workflow.definition.domain.RoleDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.event.RoleDefinitionChangedEvent;
import com.processpuzzle.workflow.definition.domain.event.RoleDefinitionDeletedEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Releases role definitions. A role refers to the artifacts it is responsible for.
 *
 * <p>The organization's directory follows the released roles, not the drafts: a role reaches Keycloak as a group when
 * it is released, and leaves it when its removal is. So the release, not a designer's save, announces
 * {@link RoleDefinitionChangedEvent} and {@link RoleDefinitionDeletedEvent}, which {@code RoleDirectorySyncListener}
 * converges after the release commits.
 */
@Component
public class RoleReleaseParticipant extends DefinitionReleaseParticipant<RoleDefinition> {

    private final RoleDefinitionRepository repository;
    private final ApplicationEventPublisher events;

    public RoleReleaseParticipant(RoleDefinitionRepository repository, ApplicationEventPublisher events) {
        super(DefinitionKinds.ROLE, DefinitionKinds.ROLE_ORDER, repository);
        this.repository = repository;
        this.events = events;
    }

    @Override
    protected List<RoleDefinition> rows(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannel(orgKey, channel);
    }

    @Override
    protected String keyOf(RoleDefinition row) {
        return row.getId();
    }

    @Override
    protected Map<String, Object> contentOf(RoleDefinition row) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", row.getName());
        content.put("description", row.getDescription());
        content.put("responsibleFor", row.getResponsibleFor());
        content.put("entityRoleId", row.getEntityRoleId());
        return content;
    }

    @Override
    protected RoleDefinition blankLike(RoleDefinition draft) {
        return RoleDefinition.builder().orgKey(draft.getOrgKey()).id(draft.getId()).build();
    }

    @Override
    protected void copy(RoleDefinition draft, RoleDefinition released) {
        released.setName(draft.getName());
        released.setDescription(draft.getDescription());
        released.setResponsibleFor(draft.getResponsibleFor() == null ? null : new ArrayList<>(draft.getResponsibleFor()));
        released.setEntityRoleId(draft.getEntityRoleId());
    }

    @Override
    protected List<DefinitionReference> referencesOf(RoleDefinition draft) {
        return draft.getResponsibleFor() == null ? List.of() : draft.getResponsibleFor().stream()
                .filter(ArtifactReleaseParticipant::isNamed)
                .distinct()
                .map(artifact -> DefinitionReference.required(kind(), draft.getId(), DefinitionKinds.ARTIFACT, artifact))
                .toList();
    }

    @Override
    protected void released(String orgKey, List<RoleDefinition> releasedRows, Set<String> removedKeys) {
        releasedRows.forEach(role ->
                events.publishEvent(new RoleDefinitionChangedEvent(orgKey, role.getId(), role.getName(), role.getDescription())));
        removedKeys.forEach(roleId -> events.publishEvent(new RoleDefinitionDeletedEvent(orgKey, roleId)));
    }
}
