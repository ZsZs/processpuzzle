package com.processpuzzle.calculation.adapter.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.processpuzzle.calculation.domain.CalculationDefinition;
import com.processpuzzle.calculation.domain.CalculationDefinitionRepository;
import com.processpuzzle.calculation.domain.definition.CalculationParser;
import com.processpuzzle.calculation.port.EntityCatalogPort;
import com.processpuzzle.calculation.usecase.CreateCalculationDefinition;
import com.processpuzzle.calculation.usecase.ReplaceCalculationDefinition;
import com.processpuzzle.calculation.usecase.assist.SourceChecker;
import com.processpuzzle.calculation.usecase.exception.CalculationAlreadyExistsException;
import com.processpuzzle.calculation.usecase.exception.InvalidCalculationException;
import com.processpuzzle.core.definition.ApiContractSchema;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionAuthoringParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Authors calculation drafts as JSON for the design assistant. The JSON is the contract's {@code CalculationDefinitionInput}:
 * a {@code source} holding the YAML document, whose {@code calculation} key is the id the draft is keyed by. A draft is saved
 * only when the same checks the Source tab's assist applies pass: the validator's own (parse, references, FEEL, cycles) and
 * the entity references (an output attribute the entity does not have, an embedded input that is not a list). An ERROR
 * refuses the save and comes back for repair; warnings do not.
 */
@Component
public class CalculationAuthoringParticipant implements DefinitionAuthoringParticipant {

    private static final String API_CONTRACT = "base-calculation-api.yaml";
    private static final String INPUT_COMPONENT = "CalculationDefinitionInput";
    private static final ObjectMapper READ = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
    private static final ObjectMapper WRITE = JsonMapper.builder().build();

    /** The JSON the assistant reads and writes: only what it may set. */
    private record SourceDraft(String source) {
    }

    private final CalculationDefinitionRepository repository;
    private final CreateCalculationDefinition createCalculation;
    private final ReplaceCalculationDefinition replaceCalculation;
    private final SourceChecker checker;
    private final ObjectProvider<EntityCatalogPort> catalog;
    private final String schema;

    public CalculationAuthoringParticipant(CalculationDefinitionRepository repository, CreateCalculationDefinition createCalculation,
                                           ReplaceCalculationDefinition replaceCalculation, SourceChecker checker,
                                           ObjectProvider<EntityCatalogPort> catalog) {
        this.repository = repository;
        this.createCalculation = createCalculation;
        this.replaceCalculation = replaceCalculation;
        this.checker = checker;
        this.catalog = catalog;
        this.schema = ApiContractSchema.ofComponent(API_CONTRACT, INPUT_COMPONENT);
    }

    @Override
    public String kind() {
        return DefinitionKinds.CALCULATION;
    }

    @Override
    public String schema() {
        return schema;
    }

    @Override
    public List<String> list(String orgKey) {
        return repository.findByOrgKeyAndChannelOrderByIdAsc(orgKey, Channel.DRAFT).stream()
            .map(CalculationDefinition::getId)
            .toList();
    }

    @Override
    public Optional<String> get(String orgKey, String key) {
        return repository.findByOrgKeyAndIdAndChannel(orgKey, key, Channel.DRAFT)
            .map(draft -> write(new SourceDraft(draft.getSource())));
    }

    @Override
    public List<AuthoringIssue> saveDraft(String orgKey, String key, String text) {
        SourceDraft draft;
        try {
            draft = READ.readValue(text, SourceDraft.class);
        } catch (JsonProcessingException e) {
            return List.of(AuthoringIssue.error("", "The draft is not valid JSON for this schema: " + e.getOriginalMessage()));
        }
        CalculationParser.Parsed parsed = CalculationParser.parse(draft.source() == null ? "" : draft.source());
        if (parsed.isParsed() && !key.equals(parsed.model().id())) {
            throw new IllegalArgumentException("The draft's calculation id '%s' does not match the key '%s'".formatted(parsed.model().id(), key));
        }

        List<AuthoringIssue> errors = checker.check(orgKey, catalogOf(), "", draft.source()).stream()
            .filter(issue -> issue.severity() == AuthoringIssue.Severity.ERROR)
            .toList();
        if (!errors.isEmpty()) {
            return errors;
        }
        try {
            if (repository.existsByOrgKeyAndIdAndChannel(orgKey, key, Channel.DRAFT)) {
                replaceCalculation.replace(orgKey, key, draft.source(), null);
            } else {
                createCalculation.create(orgKey, draft.source());
            }
            return List.of();
        } catch (InvalidCalculationException e) {
            return e.errors().stream().map(error -> AuthoringIssue.error("", error)).toList();
        } catch (CalculationAlreadyExistsException | IllegalArgumentException | IllegalStateException e) {
            return List.of(AuthoringIssue.error("", e.getMessage()));
        }
    }

    @Override
    public List<AuthoringIssue> validate(String orgKey, String key) {
        return repository.findByOrgKeyAndIdAndChannel(orgKey, key, Channel.DRAFT)
            .map(draft -> checker.check(orgKey, catalogOf(), "", draft.getSource()))
            .orElse(List.of());
    }

    private EntityCatalogPort catalogOf() {
        return catalog.getIfAvailable(() -> EntityCatalogPort.UNKNOWN);
    }

    private String write(Object model) {
        try {
            return WRITE.writeValueAsString(model);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A stored calculation cannot be written as JSON", e);
        }
    }
}
