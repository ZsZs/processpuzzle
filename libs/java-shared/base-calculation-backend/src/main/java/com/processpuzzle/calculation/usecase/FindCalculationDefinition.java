package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.OutputSpec;
import com.processpuzzle.calculation.usecase.exception.CalculationNotFoundException;
import com.processpuzzle.core.definition.Channel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindCalculationDefinition {

    private final CalculationDefinitionRepository repository;

    public FindCalculationDefinition(CalculationDefinitionRepository repository) {
        this.repository = repository;
    }

    public CalculationDefinition find(String orgKey, String id, Channel channel) {
        return repository.findByOrgKeyAndIdAndChannel(orgKey, id, channel)
                .orElseThrow(() -> new CalculationNotFoundException(orgKey, id));
    }

    public List<CalculationDefinition> findAll(String orgKey, Channel channel) {
        return repository.findByOrgKeyAndChannelOrderByIdAsc(orgKey, channel);
    }

    /**
     * The attributes of {@code entity} a released calculation stores its results in — what base-entity keeps
     * read-only, so a form cannot overwrite a derived value with a stale one.
     */
    public Set<String> storedAttributesOf(String orgKey, String entity) {
        Set<String> attributes = new LinkedHashSet<>();
        storedOutputsOf(orgKey, entity).forEach(output -> attributes.add(output.attribute()));
        return attributes;
    }

    /**
     * One stored attribute of an entity and where its value comes from.
     *
     * @param decision  the decision, group column or parameter the value comes from
     * @param parameter the entity parameter whose object is the output entity, null when there is none
     */
    public record StoredOutput(String calculationId, String attribute, String decision, String parameter) {
    }

    /** What the released calculations store into {@code entity}: one entry per attribute, grouped outputs left out. */
    public List<StoredOutput> storedOutputsOf(String orgKey, String entity) {
        List<StoredOutput> outputs = new ArrayList<>();
        for (CalculationDefinition definition : repository.findByOrgKeyAndChannelAndOutputEntity(orgKey, Channel.RELEASED, entity)) {
            CalculationModel model = definition.parsedModel();
            OutputSpec output = model.output();
            if (output != null && output.isStored() && !output.isGrouped()) {
                String parameter = OutputWriter.targetOf(model);
                output.mapping().forEach((attribute, decision) ->
                        outputs.add(new StoredOutput(definition.getId(), attribute, decision, parameter)));
            }
        }
        return outputs;
    }
}
