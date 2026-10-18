package com.processpuzzle.calculation.usecase;

import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.usecase.exception.CalculationNotFoundException;
import com.processpuzzle.calculation.usecase.exception.InvalidCalculationException;
import com.processpuzzle.calculation.usecase.exception.StaleCalculationException;
import com.processpuzzle.core.definition.Channel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces a draft's source. A supplied {@code version} that no longer matches is refused rather than overwritten;
 * none overwrites unconditionally. Open results are not recomputed: a changed definition applies to the next run.
 */
@Service
@Transactional
public class ReplaceCalculationDefinition {

    private final CalculationDefinitionRepository repository;
    private final CalculationValidator validator;

    public ReplaceCalculationDefinition(CalculationDefinitionRepository repository, CalculationValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public CalculationDefinition replace(String orgKey, String id, String source, Long expectedVersion) {
        CalculationDefinition existing = repository.findByOrgKeyAndIdAndChannel(orgKey, id, Channel.DRAFT)
                .orElseThrow(() -> new CalculationNotFoundException(orgKey, id));
        if (expectedVersion != null && !expectedVersion.equals(existing.getVersion())) {
            throw new StaleCalculationException(id);
        }
        CalculationValidator.Validation validation = validator.validate(source);
        List<String> errors = new ArrayList<>(validation.errors());
        if (validation.model() != null && !id.equals(validation.model().id())) {
            errors.add("the source is calculation '%s', not '%s' — the id cannot change".formatted(validation.model().id(), id));
        }
        if (!errors.isEmpty()) {
            throw new InvalidCalculationException(id, errors);
        }
        existing.applySource(source, validation.model());
        return repository.save(existing);
    }
}
