package com.processpuzzle.rule.adapter.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.processpuzzle.core.definition.ApiContractSchema;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionAuthoringParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.model.RuleDefinitionInput;
import com.processpuzzle.rule.port.RuleContextDirectory;
import com.processpuzzle.rule.usecase.CreateRule;
import com.processpuzzle.rule.usecase.UpdateRule;
import com.processpuzzle.rule.usecase.assist.ExpressionChecker;
import com.processpuzzle.rule.usecase.exception.RuleAlreadyExistsException;
import com.processpuzzle.rule.usecase.exception.RuleNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Authors rule drafts as JSON for the design assistant, through the same use cases the REST endpoints call. A rule is keyed
 * by its id. A draft is saved only when its expression passes the same checks the assist applies (syntax, attribute paths,
 * a dry run on sample objects): an ERROR refuses the save and is handed back for repair, while warnings do not. The form
 * still saves what it is given; this stricter bar is the assistant's own.
 */
@Component
public class RuleAuthoringParticipant implements DefinitionAuthoringParticipant {

    private static final String API_CONTRACT = "base-rule-api.yaml";
    private static final String INPUT_COMPONENT = "RuleDefinitionInput";
    private static final ObjectMapper READ = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
    /** The model carries timestamps, which are written as ISO text rather than as numbers for the assistant to read. */
    private static final ObjectMapper WRITE = JsonMapper.builder()
        .findAndAddModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();

    private final RuleDefinitionRepository repository;
    private final RuleMapper mapper;
    private final CreateRule createRule;
    private final UpdateRule updateRule;
    private final ExpressionChecker checker;
    private final ObjectProvider<RuleContextDirectory> directory;
    private final String schema;

    public RuleAuthoringParticipant(RuleDefinitionRepository repository, RuleMapper mapper, CreateRule createRule,
                                    UpdateRule updateRule, ExpressionChecker checker,
                                    ObjectProvider<RuleContextDirectory> directory) {
        this.repository = repository;
        this.mapper = mapper;
        this.createRule = createRule;
        this.updateRule = updateRule;
        this.checker = checker;
        this.directory = directory;
        this.schema = ApiContractSchema.ofComponent(API_CONTRACT, INPUT_COMPONENT);
    }

    @Override
    public String kind() {
        return DefinitionKinds.RULE;
    }

    @Override
    public String schema() {
        return schema;
    }

    @Override
    public List<String> list(String orgKey) {
        return repository.findByOrgKeyAndChannel(orgKey, Channel.DRAFT).stream()
            .map(RuleDefinition::getId)
            .sorted()
            .toList();
    }

    @Override
    public Optional<String> get(String orgKey, String key) {
        return repository.findByOrgKeyAndIdAndChannel(orgKey, key, Channel.DRAFT)
            .map(mapper::toModel)
            .map(this::write);
    }

    @Override
    public List<AuthoringIssue> saveDraft(String orgKey, String key, String text) {
        RuleDefinitionInput input;
        try {
            input = READ.readValue(text, RuleDefinitionInput.class);
        } catch (JsonProcessingException e) {
            return List.of(AuthoringIssue.error("", "The draft is not valid JSON for this schema: " + e.getOriginalMessage()));
        }
        if (!key.equals(input.getId())) {
            throw new IllegalArgumentException("The draft's id '%s' does not match the key '%s'".formatted(input.getId(), key));
        }

        List<AuthoringIssue> errors = review(orgKey, input.getContext(), input.getExpression()).stream()
            .filter(issue -> issue.severity() == AuthoringIssue.Severity.ERROR)
            .toList();
        if (!errors.isEmpty()) {
            return errors;
        }
        try {
            if (repository.existsByOrgKeyAndIdAndChannel(orgKey, key, Channel.DRAFT)) {
                updateRule.execute(orgKey, key, input);
            } else {
                createRule.execute(orgKey, input);
            }
            return List.of();
        } catch (RuleAlreadyExistsException | RuleNotFoundException | IllegalArgumentException | IllegalStateException e) {
            return List.of(AuthoringIssue.error("", e.getMessage()));
        }
    }

    @Override
    public List<AuthoringIssue> validate(String orgKey, String key) {
        return repository.findByOrgKeyAndIdAndChannel(orgKey, key, Channel.DRAFT)
            .map(draft -> review(orgKey, draft.getContext(), draft.getExpression()))
            .orElse(List.of());
    }

    private List<AuthoringIssue> review(String orgKey, String context, String expression) {
        return checker.review(orgKey, directory.getIfAvailable(() -> RuleContextDirectory.UNKNOWN), context, expression);
    }

    private String write(Object model) {
        try {
            return WRITE.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A stored rule cannot be written as JSON", e);
        }
    }
}
