package com.processpuzzle.calculation.usecase.assist;

import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.CalculationParser;
import com.processpuzzle.calculation.domain.definition.InputSpec;
import com.processpuzzle.calculation.domain.definition.OutputSpec;
import com.processpuzzle.calculation.domain.definition.ParameterSpec;
import com.processpuzzle.calculation.port.EntityCatalogPort;
import com.processpuzzle.calculation.port.EntityCatalogPort.Attribute;
import com.processpuzzle.calculation.port.EntityCatalogPort.EntityType;
import com.processpuzzle.calculation.usecase.CalculationValidator;
import com.processpuzzle.core.definition.AuthoringIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Checks a candidate calculation source the way the platform will meet it, short of saving it: the validator's own check
 * (parse, references, FEEL syntax, cycles), then what the validator cannot say because it knows no entities — that every
 * entity the source names exists, that an embedded input's rows are really an attribute of the entity, and that the output
 * only writes attributes the output entity has. A misspelt attribute in the output mapping is the commonest fault of a
 * generated calculation, and nothing else would catch it before a run.
 *
 * <p>The id, and the description the user wrote, must survive: the model is asked to build on the user's own words, not to
 * replace them.
 */
@Component
public class SourceChecker {

    private static final String SOURCE = "source";
    private static final int MAX_LISTED = 15;

    private final CalculationValidator validator;

    public SourceChecker(CalculationValidator validator) {
        this.validator = validator;
    }

    public List<AuthoringIssue> check(String orgKey, EntityCatalogPort catalog, String current, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return List.of(AuthoringIssue.error(SOURCE, "The source is empty."));
        }
        CalculationValidator.Validation validation = validator.validate(candidate);
        List<AuthoringIssue> issues = new ArrayList<>(validation.errors().stream().map(e -> AuthoringIssue.error(SOURCE, e)).toList());
        CalculationModel model = validation.model();
        if (model == null) {
            return issues;
        }
        issues.addAll(keptWhatTheUserWrote(current, model));
        if (!catalog.all(orgKey).isEmpty()) {
            issues.addAll(new References(orgKey, catalog, model).issues());
        }
        return issues;
    }

    private static List<AuthoringIssue> keptWhatTheUserWrote(String current, CalculationModel candidate) {
        if (current == null || current.isBlank()) {
            return List.of();
        }
        CalculationParser.Parsed parsed = CalculationParser.parse(current);
        if (!parsed.isParsed()) {
            return List.of();
        }
        List<AuthoringIssue> issues = new ArrayList<>();
        String id = parsed.model().id();
        if (id != null && !id.isBlank() && !id.equals(candidate.id())) {
            issues.add(AuthoringIssue.error("calculation", "The calculation id must stay '%s'.".formatted(id)));
        }
        String description = parsed.model().description();
        if (description != null && !description.isBlank() && !description.strip().equals(Objects.toString(candidate.description(), "").strip())) {
            issues.add(AuthoringIssue.error("description", "Keep the 'description' exactly as the user wrote it."));
        }
        return issues;
    }

    /** What a source says about entities, held against the catalog. */
    private static final class References {
        private final String orgKey;
        private final EntityCatalogPort catalog;
        private final CalculationModel model;
        private final List<AuthoringIssue> issues = new ArrayList<>();

        References(String orgKey, EntityCatalogPort catalog, CalculationModel model) {
            this.orgKey = orgKey;
            this.catalog = catalog;
            this.model = model;
        }

        List<AuthoringIssue> issues() {
            model.parameters().forEach((name, parameter) -> {
                if (parameter.isEntity()) {
                    entity(parameter.entity(), "parameters." + name + ".entity");
                }
            });
            model.inputs().forEach(this::input);
            output(model.output());
            return issues;
        }

        private void input(String name, InputSpec input) {
            String path = "inputs." + name;
            if (!input.isEmbedded()) {
                entity(input.entity(), path + ".entity");
                return;
            }
            ParameterSpec from = model.parameters().get(input.from());
            Optional<EntityType> owner = from == null || !from.isEntity() ? Optional.empty() : catalog.find(orgKey, from.entity());
            if (owner.isEmpty() || input.path() == null) {
                return;
            }
            Optional<Attribute> rows = owner.get().attribute(input.path());
            if (rows.isEmpty()) {
                issues.add(AuthoringIssue.error(path + ".path", "'%s' is not an attribute of %s (it has: %s).".formatted(
                    input.path(), owner.get().code(), attributeNames(owner.get()))));
            } else if (!rows.get().embedded()) {
                issues.add(AuthoringIssue.error(path + ".path", "'%s' of %s is not an embedded list, so its rows cannot be read with from/path."
                    .formatted(input.path(), owner.get().code())));
            } else if (input.entity() != null && !input.entity().equals(rows.get().entity())) {
                issues.add(AuthoringIssue.warning(path + ".entity", "The rows of '%s' are %s, not %s.".formatted(
                    input.path(), rows.get().entity(), input.entity())));
            }
        }

        private void output(OutputSpec output) {
            if (output == null || output.entity() == null) {
                return;
            }
            Optional<EntityType> target = entity(output.entity(), "output.entity");
            target.ifPresent(type -> output.mapping().keySet().forEach(attribute -> {
                if (type.attribute(attribute).isEmpty()) {
                    issues.add(AuthoringIssue.error("output.mapping." + attribute, "'%s' is not an attribute of %s (it has: %s)."
                        .formatted(attribute, type.code(), attributeNames(type))));
                }
            }));
        }

        private Optional<EntityType> entity(String code, String path) {
            if (code == null || code.isBlank()) {
                return Optional.empty();
            }
            Optional<EntityType> found = catalog.find(orgKey, code);
            if (found.isEmpty()) {
                issues.add(AuthoringIssue.error(path, "There is no entity '%s' (the entities are: %s).".formatted(code,
                    catalog.all(orgKey).stream().map(EntityType::code).limit(MAX_LISTED).collect(Collectors.joining(", ")))));
            }
            return found;
        }

        private static String attributeNames(EntityType type) {
            return type.attributes().stream().map(Attribute::name).collect(Collectors.joining(", "));
        }
    }
}
