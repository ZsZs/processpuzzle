package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.usecase.exception.CalculationNotFoundException;
import com.processpuzzle.core.definition.Channel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deletes a draft. Its runs stay: each keeps a snapshot of the source it ran. */
@Service
@Transactional
public class DeleteCalculationDefinition {

    private final CalculationDefinitionRepository repository;

    public DeleteCalculationDefinition(CalculationDefinitionRepository repository) {
        this.repository = repository;
    }

    public void delete(String orgKey, String id) {
        CalculationDefinition existing = repository.findByOrgKeyAndIdAndChannel(orgKey, id, Channel.DRAFT)
                .orElseThrow(() -> new CalculationNotFoundException(orgKey, id));
        repository.delete(existing);
    }
}
