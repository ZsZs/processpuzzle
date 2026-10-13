package com.processpuzzle.workflow.definition.adapters.inbound.release;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Releases tool definitions, which refer to nothing: a tool names an external service and its operations. */
@Component
public class ToolReleaseParticipant extends DefinitionReleaseParticipant<ToolDefinition> {

    private final ToolDefinitionRepository repository;

    public ToolReleaseParticipant(ToolDefinitionRepository repository) {
        super(DefinitionKinds.TOOL, DefinitionKinds.TOOL_ORDER, repository);
        this.repository = repository;
    }

    @Override
    protected List<ToolDefinition> rows(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannel(orgKey, channel);
    }

    @Override
    protected String keyOf(ToolDefinition row) {
        return row.getId();
    }

    @Override
    protected Map<String, Object> contentOf(ToolDefinition row) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", row.getName());
        content.put("description", row.getDescription());
        content.put("baseUrl", row.getBaseUrl());
        content.put("auth", row.getAuth());
        content.put("operations", row.getOperations());
        return content;
    }

    @Override
    protected ToolDefinition blankLike(ToolDefinition draft) {
        return ToolDefinition.builder().orgKey(draft.getOrgKey()).id(draft.getId()).build();
    }

    @Override
    protected void copy(ToolDefinition draft, ToolDefinition released) {
        released.setName(draft.getName());
        released.setDescription(draft.getDescription());
        released.setBaseUrl(draft.getBaseUrl());
        released.setAuth(draft.getAuth());
        released.setOperations(draft.getOperations() == null ? null : new ArrayList<>(draft.getOperations()));
    }
}
