package com.processpuzzle.state.usecase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.state.adapter.inbound.dto.StateMachineYamlDocument;
import com.processpuzzle.state.adapter.inbound.dto.StateMachineYamlEntry;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, rollbackFor = Exception.class)
public class ExportStateMachineDefinitions {

    private final StateMachineDefinitionRepository repository;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public ExportStateMachineDefinitions(StateMachineDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * The exported entries carry no {@code orgKey} — same discipline as {@code ExportRules} —
     * which is what makes an export from one organization importable into another.
     */
    public byte[] execute(String orgKey, String entityName) throws IOException {
        List<StateMachineDefinition> definitions = entityName != null
                ? repository.findByOrgKeyAndEntityName(orgKey, entityName)
                        .map(List::of)
                        .orElseGet(List::of)
                : repository.findByOrgKey(orgKey);

        List<StateMachineYamlEntry> entries = definitions.stream()
                .map(d -> new StateMachineYamlEntry(d.getEntityName(), d.getName(), d.getDescription(),
                        d.getStateAttributeKey(), d.getInitialStateKey(), d.getStates(), d.getTransitions()))
                .toList();

        return yamlMapper.writeValueAsBytes(new StateMachineYamlDocument(entries));
    }
}
