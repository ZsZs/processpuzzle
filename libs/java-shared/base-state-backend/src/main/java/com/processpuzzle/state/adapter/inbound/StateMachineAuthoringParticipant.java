package com.processpuzzle.state.adapter.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.processpuzzle.core.definition.ApiContractSchema;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionAuthoringParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.model.StateMachineDefinitionInput;
import com.processpuzzle.state.usecase.CreateStateMachineDefinition;
import com.processpuzzle.state.usecase.StateMachineTopologyValidator;
import com.processpuzzle.state.usecase.UpdateStateMachineDefinition;
import com.processpuzzle.state.usecase.exception.StateMachineAlreadyExistsException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Authors state machine drafts as JSON through the same use cases the REST endpoints call, so the same topology
 * checks apply. A state machine is keyed by the entity it governs, its {@code entityName}.
 *
 * <p>The schema is the {@code StateMachineDefinitionInput} component of the published API contract. Unknown
 * properties are ignored on the way in, because {@link #get} returns the read-only fields too.
 */
@Component
public class StateMachineAuthoringParticipant implements DefinitionAuthoringParticipant {

    private static final String API_CONTRACT = "base-state-api.yaml";
    private static final String INPUT_COMPONENT = "StateMachineDefinitionInput";
    private static final ObjectMapper JSON = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    private final StateMachineDefinitionRepository repository;
    private final StateMapper mapper;
    private final StateMachineTopologyValidator validator;
    private final CreateStateMachineDefinition createUseCase;
    private final UpdateStateMachineDefinition updateUseCase;
    private final String schema;

    public StateMachineAuthoringParticipant(StateMachineDefinitionRepository repository,
                                            StateMapper mapper,
                                            StateMachineTopologyValidator validator,
                                            CreateStateMachineDefinition createUseCase,
                                            UpdateStateMachineDefinition updateUseCase) {
        this.repository = repository;
        this.mapper = mapper;
        this.validator = validator;
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.schema = ApiContractSchema.ofComponent(API_CONTRACT, INPUT_COMPONENT);
    }

    @Override
    public String kind() {
        return DefinitionKinds.STATE;
    }

    @Override
    public String schema() {
        return schema;
    }

    @Override
    public List<String> list(String orgKey) {
        return repository.findByOrgKeyAndChannel(orgKey, Channel.DRAFT).stream()
            .map(StateMachineDefinition::getEntityName)
            .sorted()
            .toList();
    }

    @Override
    public Optional<String> get(String orgKey, String key) {
        return repository.findByOrgKeyAndEntityNameAndChannel(orgKey, key, Channel.DRAFT)
            .map(mapper::toModel)
            .map(this::write);
    }

    @Override
    public List<AuthoringIssue> saveDraft(String orgKey, String key, String text) {
        StateMachineDefinitionInput input;
        try {
            input = JSON.readValue(text, StateMachineDefinitionInput.class);
        } catch (JsonProcessingException e) {
            return List.of(AuthoringIssue.error("", "The draft is not valid JSON for this schema: " + e.getOriginalMessage()));
        }
        if (!key.equals(input.getEntityName())) {
            throw new IllegalArgumentException("The draft's entityName '%s' does not match the key '%s'".formatted(input.getEntityName(), key));
        }

        try {
            StateMachineDefinition definition = mapper.toDomain(orgKey, input);
            if (repository.existsByOrgKeyAndEntityNameAndChannel(orgKey, key, Channel.DRAFT)) {
                updateUseCase.execute(orgKey, key, definition);
            } else {
                createUseCase.execute(definition);
            }
            return List.of();
        } catch (StateMachineAlreadyExistsException | IllegalArgumentException e) {
            return List.of(AuthoringIssue.error("", e.getMessage()));
        }
    }

    /**
     * The topology validator reports the first problem it finds, so a draft gets one issue at a time; a client repairs
     * and asks again.
     */
    @Override
    public List<AuthoringIssue> validate(String orgKey, String key) {
        return repository.findByOrgKeyAndEntityNameAndChannel(orgKey, key, Channel.DRAFT)
            .map(draft -> {
                try {
                    validator.validate(orgKey, key, draft.getStateAttributeKey(), draft.getInitialStateKey(),
                        draft.getStates(), draft.getTransitions());
                    return List.<AuthoringIssue>of();
                } catch (IllegalArgumentException e) {
                    return List.of(AuthoringIssue.error("", e.getMessage()));
                }
            })
            .orElse(List.of());
    }

    private String write(Object model) {
        try {
            return JSON.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A stored state machine definition cannot be written as JSON", e);
        }
    }
}
