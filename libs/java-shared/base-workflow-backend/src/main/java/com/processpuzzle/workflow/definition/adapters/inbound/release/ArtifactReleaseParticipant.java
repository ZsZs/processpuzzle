package com.processpuzzle.workflow.definition.adapters.inbound.release;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Releases artifact definitions. An artifact of type ENTITY refers to its entity type, by name, and one with a state
 * machine to that machine; a document or widget artifact refers to a kind no participant releases yet.
 */
@Component
public class ArtifactReleaseParticipant extends DefinitionReleaseParticipant<ArtifactDefinition> {

    private final ArtifactDefinitionRepository repository;

    public ArtifactReleaseParticipant(ArtifactDefinitionRepository repository) {
        super(DefinitionKinds.ARTIFACT, DefinitionKinds.ARTIFACT_ORDER, repository);
        this.repository = repository;
    }

    @Override
    protected List<ArtifactDefinition> rows(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannel(orgKey, channel);
    }

    @Override
    protected String keyOf(ArtifactDefinition row) {
        return row.getId();
    }

    @Override
    protected Map<String, Object> contentOf(ArtifactDefinition row) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", row.getName());
        content.put("description", row.getDescription());
        content.put("artifactType", row.getArtifactType());
        content.put("artifactTypeId", row.getArtifactTypeId());
        content.put("stateMachineId", row.getStateMachineId());
        return content;
    }

    @Override
    protected ArtifactDefinition blankLike(ArtifactDefinition draft) {
        return ArtifactDefinition.builder().orgKey(draft.getOrgKey()).id(draft.getId()).build();
    }

    @Override
    protected void copy(ArtifactDefinition draft, ArtifactDefinition released) {
        released.setName(draft.getName());
        released.setDescription(draft.getDescription());
        released.setArtifactType(draft.getArtifactType());
        released.setArtifactTypeId(draft.getArtifactTypeId());
        released.setStateMachineId(draft.getStateMachineId());
    }

    @Override
    protected List<DefinitionReference> referencesOf(ArtifactDefinition draft) {
        List<DefinitionReference> references = new ArrayList<>();
        if (draft.getArtifactType() != null && isNamed(draft.getArtifactTypeId())) {
            String toKind = draft.getArtifactType() == ArtifactType.ENTITY ? DefinitionKinds.ENTITY
                    : draft.getArtifactType() == ArtifactType.DOCUMENT ? DefinitionKinds.DOCUMENT : DefinitionKinds.WIDGET;
            references.add(DefinitionReference.required(kind(), draft.getId(), toKind, draft.getArtifactTypeId()));
        }
        if (isNamed(draft.getStateMachineId())) {
            references.add(DefinitionReference.required(kind(), draft.getId(), DefinitionKinds.STATE, draft.getStateMachineId()));
        }
        return references;
    }

    static boolean isNamed(String key) {
        return key != null && !key.isBlank();
    }
}
