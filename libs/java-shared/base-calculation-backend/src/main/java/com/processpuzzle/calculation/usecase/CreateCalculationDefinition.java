package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.usecase.exception.CalculationAlreadyExistsException;
import com.processpuzzle.calculation.usecase.exception.InvalidCalculationException;
import com.processpuzzle.core.definition.Channel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a draft from a valid source; its id is the source's {@code calculation} key. */
@Service
@Transactional
public class CreateCalculationDefinition {

    private final CalculationDefinitionRepository repository;
    private final CalculationValidator validator;

    public CreateCalculationDefinition(CalculationDefinitionRepository repository, CalculationValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public CalculationDefinition create(String orgKey, String source) {
        CalculationValidator.Validation validation = validator.validate(source);
        String id = validation.model() == null ? null : validation.model().id();
        if (!validation.isValid()) {
            throw new InvalidCalculationException(id, validation.errors());
        }
        if (repository.existsByOrgKeyAndIdAndChannel(orgKey, id, Channel.DRAFT)) {
            throw new CalculationAlreadyExistsException(orgKey, id);
        }
        CalculationDefinition definition = CalculationDefinition.builder().orgKey(orgKey).id(id).channel(Channel.DRAFT).build();
        definition.applySource(source, validation.model());
        return repository.save(definition);
    }
}
