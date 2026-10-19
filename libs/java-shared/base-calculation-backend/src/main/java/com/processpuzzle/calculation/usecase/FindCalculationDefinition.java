package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.domain.definition.OutputSpec;
import com.processpuzzle.calculation.usecase.exception.CalculationNotFoundException;
import com.processpuzzle.core.definition.Channel;
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
        for (CalculationDefinition definition : repository.findByOrgKeyAndChannelAndOutputEntity(orgKey, Channel.RELEASED, entity)) {
            OutputSpec output = definition.parsedModel().output();
            if (output != null && output.isStored() && !output.isGrouped()) {
                attributes.addAll(output.mapping().keySet());
            }
        }
        return attributes;
    }
}
