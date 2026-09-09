package com.processpuzzle.app.usecase;

import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Validates a candidate definition without persisting it, for live feedback in the designer. Returns
 * the problems rather than throwing — an invalid candidate is the expected case here, not an error.
 */
@Service
public class ValidateAppDefinition {

    private final AppDefinitionValidator validator;
    private final OrganizationGuard guard;

    public ValidateAppDefinition(AppDefinitionValidator validator, OrganizationGuard guard) {
        this.validator = validator;
        this.guard = guard;
    }

    public List<AppValidationProblem> execute(String orgKey, AppDefinitionInput input) {
        guard.requireDesign(orgKey);
        return validator.validate(orgKey, input);
    }
}
