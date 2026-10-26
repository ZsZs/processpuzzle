package com.processpuzzle.baseentity.adapter.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.definition.adapters.inbound.EntityDefinitionMapper;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionValidator;
import com.processpuzzle.baseentity.definition.usecases.inbound.CreateEntityDefinitionUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.FindEntityDefinitionsByCodesUseCase;
import com.processpuzzle.baseentity.definition.usecases.inbound.ReplaceEntityDefinitionUseCase;
import com.processpuzzle.baseentity.model.BaseEntityDefinitionInput;
import com.processpuzzle.core.definition.ApiContractSchema;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionAuthoringParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Authors entity drafts as JSON through the same use cases the REST endpoints call, so the same validators apply.
 *
 * <p>The schema is the {@code BaseEntityDefinitionInput} component of the published API contract, with its
 * references inlined, so the schema and the parser cannot disagree about the shape. Unknown properties are ignored
 * on the way in, because {@link #get} returns the read-only fields too, and a client reads, edits and saves back.
 */
@Component
public class EntityDefinitionAuthoringParticipant implements DefinitionAuthoringParticipant {

    private static final String API_CONTRACT = "base-entity-api.yaml";
    private static final String INPUT_COMPONENT = "BaseEntityDefinitionInput";
    private static final ObjectMapper JSON = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    private final EntityDefinitionRepository repository;
    private final EntityDefinitionMapper mapper;
    private final EntityDefinitionValidator validator;
    private final CreateEntityDefinitionUseCase createUseCase;
    private final ReplaceEntityDefinitionUseCase replaceUseCase;
    private final FindEntityDefinitionsByCodesUseCase findByCodes;
    private final String schema;

    public EntityDefinitionAuthoringParticipant(EntityDefinitionRepository repository,
                                                EntityDefinitionMapper mapper,
                                                EntityDefinitionValidator validator,
                                                CreateEntityDefinitionUseCase createUseCase,
                                                ReplaceEntityDefinitionUseCase replaceUseCase,
                                                FindEntityDefinitionsByCodesUseCase findByCodes) {
        this.repository = repository;
        this.mapper = mapper;
        this.validator = validator;
        this.createUseCase = createUseCase;
        this.replaceUseCase = replaceUseCase;
        this.findByCodes = findByCodes;
        this.schema = ApiContractSchema.ofComponent(API_CONTRACT, INPUT_COMPONENT);
    }

    @Override
    public String kind() {
        return DefinitionKinds.ENTITY;
    }

    @Override
    public String schema() {
        return schema;
    }

    @Override
    public List<String> list(String orgKey) {
        return repository.findAllByOrgKeyAndChannel(orgKey, Channel.DRAFT).stream()
            .map(BaseEntityDefinition::getCode)
            .sorted()
            .toList();
    }

    @Override
    public Optional<String> get(String orgKey, String key) {
        return repository.findByOrgKeyAndCodeAndChannel(orgKey, key, Channel.DRAFT)
            .map(mapper::toModel)
            .map(EntityDefinitionAuthoringParticipant::withoutTimestamps)
            .map(EntityDefinitionAuthoringParticipant::write);
    }

    @Override
    public List<AuthoringIssue> saveDraft(String orgKey, String key, String text) {
        BaseEntityDefinitionInput input;
        try {
            input = JSON.readValue(text, BaseEntityDefinitionInput.class);
        } catch (JsonProcessingException e) {
            return List.of(AuthoringIssue.error("", "The draft is not valid JSON for this schema: " + e.getOriginalMessage()));
        }
        if (!key.equals(input.getCode())) {
            throw new IllegalArgumentException("The draft's code '%s' does not match the key '%s'".formatted(input.getCode(), key));
        }

        try {
            BaseEntityDefinition definition = mapper.toDomain(input);
            if (findByCodes.find(orgKey, Set.of(key), Channel.DRAFT).isEmpty()) {
                createUseCase.create(orgKey, definition);
            } else {
                replaceUseCase.replace(orgKey, key, definition);
            }
            return List.of();
        } catch (ValidationException e) {
            return violations(e);
        } catch (ConflictException e) {
            return List.of(AuthoringIssue.error("", e.getMessage()));
        }
    }

    @Override
    public List<AuthoringIssue> validate(String orgKey, String key) {
        return findByCodes.find(orgKey, Set.of(key), Channel.DRAFT).stream()
            .findFirst()
            .map(this::validationIssues)
            .orElse(List.of());
    }

    private List<AuthoringIssue> validationIssues(BaseEntityDefinition definition) {
        try {
            validator.validate(definition);
            return List.of();
        } catch (ValidationException e) {
            return violations(e);
        }
    }

    private static List<AuthoringIssue> violations(ValidationException e) {
        return e.getViolations().stream()
            .map(violation -> AuthoringIssue.error(
                violation.attributeCode() == null ? "" : violation.attributeCode(), violation.message()))
            .toList();
    }

    /** Read-only timestamps are left out, as the fingerprint leaves them out: canonical JSON has no java.time module. */
    private static com.processpuzzle.baseentity.model.BaseEntityDefinition withoutTimestamps(
        com.processpuzzle.baseentity.model.BaseEntityDefinition model) {
        model.setCreatedAt(null);
        model.setUpdatedAt(null);
        return model;
    }

    private static String write(Object model) {
        try {
            return JSON.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A stored entity definition cannot be written as JSON", e);
        }
    }
}
