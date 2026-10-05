package com.processpuzzle.baseentity.definition.usecases.inbound;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.baseentity.adapter.inbound.dto.DefaultEntitiesDocument;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionValidator;
import com.processpuzzle.baseentity.model.BaseEntityDefinitionInput;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * All-or-nothing bulk import of entity definitions into one organization, the same contract as
 * base-rule's {@code ImportRules} and base-state's {@code ImportStateMachineDefinitions}: if any entry
 * fails validation, nothing in the file is written and every problem is returned.
 *
 * <p>Reads the {@code default-entities/} file format, {@link DefaultEntitiesDocument}, but only its
 * {@code entityDefinitions}; sample instances are not imported here. Existing definitions are
 * replaced, new ones created, through the same use cases the REST endpoint uses.
 */
@Component
@Transactional(rollbackFor = Exception.class)
public class ImportEntityDefinitions {

    private final EntityDefinitionRepository repository;
    private final EntityDefinitionValidator validator;
    private final EntityDefinitionMapper mapper;
    private final CreateEntityDefinitionUseCase createUseCase;
    private final ReplaceEntityDefinitionUseCase replaceUseCase;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ImportEntityDefinitions(EntityDefinitionRepository repository,
                                   EntityDefinitionValidator validator,
                                   EntityDefinitionMapper mapper,
                                   CreateEntityDefinitionUseCase createUseCase,
                                   ReplaceEntityDefinitionUseCase replaceUseCase) {
        this.repository = repository;
        this.validator = validator;
        this.mapper = mapper;
        this.createUseCase = createUseCase;
        this.replaceUseCase = replaceUseCase;
    }

    public Outcome execute(String orgKey, InputStream input) throws IOException {
        DefaultEntitiesDocument document = yamlMapper.readValue(input, DefaultEntitiesDocument.class);
        List<BaseEntityDefinitionInput> entries = document == null ? List.of() : document.entityDefinitions();

        List<String> errors = new ArrayList<>();
        Map<String, BaseEntityDefinition> byCode = collectByCode(entries, errors);
        validateComponentParents(orgKey, byCode, errors);
        validateStructure(byCode, errors);
        if (!errors.isEmpty()) {
            return new Outcome(List.of(), List.of(), errors);
        }

        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        for (BaseEntityDefinition definition : byCode.values()) {
            if (repository.existsByOrgKeyAndCode(orgKey, definition.getCode())) {
                replaceUseCase.replace(orgKey, definition.getCode(), definition);
                updated.add(definition.getCode());
            } else {
                createUseCase.create(orgKey, definition);
                created.add(definition.getCode());
            }
        }
        return new Outcome(created, updated, List.of());
    }

    private Map<String, BaseEntityDefinition> collectByCode(List<BaseEntityDefinitionInput> entries, List<String> errors) {
        Map<String, BaseEntityDefinition> byCode = new LinkedHashMap<>();
        for (BaseEntityDefinitionInput entry : entries) {
            if (entry == null || entry.getCode() == null || entry.getCode().isBlank()) {
                errors.add("An entity definition entry is missing 'code'.");
            } else if (byCode.put(entry.getCode(), mapper.toDomain(entry)) != null) {
                errors.add("Duplicate entity definition code within the import file: '" + entry.getCode() + "'.");
            }
        }
        return byCode;
    }

    /** A component parent may be defined already, or arrive in the same file. */
    private void validateComponentParents(String orgKey, Map<String, BaseEntityDefinition> byCode, List<String> errors) {
        for (BaseEntityDefinition definition : byCode.values()) {
            for (String parent : definition.getComponentParents()) {
                if (!byCode.containsKey(parent) && !repository.existsByOrgKeyAndCode(orgKey, parent)) {
                    errors.add("'" + definition.getCode() + "' names unknown componentParent '" + parent + "'.");
                }
            }
        }
    }

    /** The validator the create and replace use cases run, run up front so a bad entry writes nothing. */
    private void validateStructure(Map<String, BaseEntityDefinition> byCode, List<String> errors) {
        for (BaseEntityDefinition definition : byCode.values()) {
            try {
                validator.validate(definition);
            } catch (ValidationException e) {
                String violations = e.getViolations().stream()
                        .map(v -> v.attributeCode() == null ? v.message() : v.attributeCode() + ": " + v.message())
                        .collect(Collectors.joining("; "));
                errors.add("'" + definition.getCode() + "': " + violations);
            }
        }
    }

    /**
     * @param created codes of the definitions that were new
     * @param updated codes of the definitions that replaced an existing one
     * @param errors  why the file was refused; non-empty means nothing was written
     */
    public record Outcome(List<String> created, List<String> updated, List<String> errors) {
    }
}
