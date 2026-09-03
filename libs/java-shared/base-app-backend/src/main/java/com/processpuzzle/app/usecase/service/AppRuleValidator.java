package com.processpuzzle.app.usecase.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.Severity;
import com.processpuzzle.app.usecase.port.RuleEvaluator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Checks a candidate app definition against the organization's own {@code base-rule} records, which is
 * what makes governance of an app definition configuration rather than code: the conventions
 * {@link AppDefinitionValidator} deliberately leaves out — id shapes, navigation depth, role naming,
 * CSS units, translatability — are PPCL expressions a tenant can add, disable or override without a
 * release.
 *
 * <p>Rules reach this feature through {@link RuleEvaluator}, base-app's own outbound port. They used
 * to reach it through Base Rule's {@code usecase} named interface, called directly — one feature
 * library compiled against another for a single method and an enum. The port says what base-app needs
 * without naming who provides it; the composition root wires an adapter over base-rule, and would
 * wire an HTTP one just as easily. It applies {@code orgKey}'s rules alone, so one tenant's
 * expressions never judge another's app.
 *
 * <p>The port is injected through an {@link ObjectProvider} rather than required, the same way
 * {@link AppDefinitionValidator} treats its entity registry: a host application that wires Base App
 * without a rule engine keeps the structural checks and silently skips the rule pass, instead of
 * failing to start.
 */
@Component
public class AppRuleValidator {

    /**
     * The {@code context} value the app-definition rules declare. It is the {@code entityName} of the
     * designer's {@code BaseEntityDescriptor}, which is what makes one rule set author-able from the
     * generated CRUD UI and evaluable here — see {@code sample-rules/processpuzzle-rules.yaml}.
     */
    public static final String RULE_CONTEXT = "App Definition";

    /** Prefix for the synthesized {@code errorId} of a rule that declares no Transloco key. */
    private static final String RULE_ERROR_ID_PREFIX = "app.validation.rule.";

    /**
     * Dates as ISO strings rather than epoch numbers or, worse, nested objects: the stored-definition
     * path passes a model carrying {@code createdAt} / {@code updatedAt}, and a rule comparing them
     * should see what the REST response shows.
     */
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private static final TypeReference<Map<String, Object>> AS_MAP = new TypeReference<>() { };

    private final ObjectProvider<RuleEvaluator> ruleEvaluatorProvider;

    public AppRuleValidator(ObjectProvider<RuleEvaluator> ruleEvaluatorProvider) {
        this.ruleEvaluatorProvider = ruleEvaluatorProvider;
    }

    /**
     * Evaluates every enabled {@code App Definition} rule of {@code orgKey} against the given
     * definition, which may be an {@code AppDefinitionInput} or a stored {@code AppDefinition} — both
     * are generated API models, so both convert to the same JSON shape a rule expression expects.
     *
     * @return one problem per violated rule, empty when the definition satisfies all of them or no
     *     rule engine is wired
     */
    public List<AppValidationProblem> validate(String orgKey, Object definition) {
        RuleEvaluator evaluator = ruleEvaluatorProvider.getIfAvailable();
        if (evaluator == null || definition == null) {
            return List.of();
        }

        return evaluator.evaluate(orgKey, RULE_CONTEXT, asRuleInput(definition)).stream()
                .map(AppRuleValidator::toProblem)
                .toList();
    }

    /**
     * The object a PPCL expression sees as {@code entity}. Converting the API model rather than
     * hand-building a map is what keeps rules and contract in step: the generated enums carry
     * {@code @JsonValue}, so a sidenav region arrives as {@code type: 'sidenav'} exactly as it does
     * over REST, and a field added to the contract is visible to rules with no change here.
     */
    private static Map<String, Object> asRuleInput(Object definition) {
        return JSON.convertValue(definition, AS_MAP);
    }

    /**
     * A violation is reported against the whole definition. A rule declares the {@code fields} it
     * reads, but the violation does not carry them and an expression is free to walk the entire graph,
     * so pointing at one node would be a guess; the designer shows these at form level.
     */
    private static AppValidationProblem toProblem(RuleEvaluator.Violation violation) {
        return new AppValidationProblem("/", errorIdOf(violation), violation.message(),
                violation.severity() == null ? Severity.ERROR : violation.severity());
    }

    /**
     * The rule's own Transloco key when it has one — a rule author's key is more useful to the
     * designer than anything synthesized here — otherwise a stable id derived from the rule id.
     */
    private static String errorIdOf(RuleEvaluator.Violation violation) {
        if (violation.translocoId() != null && !violation.translocoId().isBlank()) {
            return violation.translocoId();
        }
        return RULE_ERROR_ID_PREFIX + violation.ruleId();
    }
}
