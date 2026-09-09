package com.processpuzzle.widget.usecase;

import com.processpuzzle.widget.domain.Port;
import com.processpuzzle.widget.domain.WidgetDefinition;
import com.processpuzzle.widget.domain.WidgetDefinitionRepository;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionAlreadyExistsException;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionInvalidException;
import com.processpuzzle.widget.usecase.exception.WidgetDefinitionNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The widget-definition use cases, gathered in one service rather than split one-class-per-operation.
 *
 * <p>base-app splits them ({@code CreateAppDefinition}, {@code UpdateAppDefinition}, …) because each
 * carries real behaviour — graph conversion, rule validation, publish snapshotting. These are plain
 * CRUD over a single row with one shared validation routine, so six near-empty classes would be
 * ceremony. If a use case here grows behaviour of its own, splitting it out is a rename.
 */
@Service
public class WidgetDefinitionCrud {

    /** Mirrors the contract's WidgetKey pattern. Kept here so the check cannot drift from the schema. */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final WidgetDefinitionRepository repository;

    public WidgetDefinitionCrud(WidgetDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<WidgetDefinition> findAll(String orgKey, Pageable pageable) {
        return repository.findAll((root, query, builder) -> builder.equal(root.get("orgKey"), orgKey), pageable);
    }

    @Transactional(readOnly = true)
    public WidgetDefinition find(String orgKey, String key) {
        return repository.findByOrgKeyAndKey(orgKey, key)
                .orElseThrow(() -> new WidgetDefinitionNotFoundException(orgKey, key));
    }

    @Transactional
    public WidgetDefinition create(String orgKey, WidgetDefinitionDraft draft) {
        validate(draft);
        if (repository.existsByOrgKeyAndKey(orgKey, draft.key())) {
            throw new WidgetDefinitionAlreadyExistsException(orgKey, draft.key());
        }
        WidgetDefinition definition = new WidgetDefinition(orgKey, draft.key(), draft.name());
        apply(definition, draft);
        return repository.save(definition);
    }

    /**
     * Full replacement, as the contract specifies: a field absent from the draft is written as null.
     * {@code key} is immutable — it is what every stored {@code WidgetInstance.type} references — so
     * a draft naming a different key is rejected rather than silently moving the row.
     */
    @Transactional
    public WidgetDefinition update(String orgKey, String key, WidgetDefinitionDraft draft) {
        validate(draft);
        if (!key.equals(draft.key())) {
            throw new WidgetDefinitionInvalidException("Widget key is immutable: cannot rename '" + key + "' to '" + draft.key() + "'.");
        }
        WidgetDefinition definition = find(orgKey, key);
        apply(definition, draft);
        definition.markEdited();
        return repository.save(definition);
    }

    @Transactional
    public WidgetDefinition publish(String orgKey, String key) {
        WidgetDefinition definition = find(orgKey, key);
        definition.publish();
        return repository.save(definition);
    }

    @Transactional
    public void delete(String orgKey, String key) {
        WidgetDefinition definition = find(orgKey, key);
        repository.delete(definition);
    }

    private void apply(WidgetDefinition definition, WidgetDefinitionDraft draft) {
        definition.setName(draft.name());
        definition.setTranslocoId(draft.translocoId());
        definition.setDescription(draft.description());
        definition.setCategory(draft.category());
        definition.setIcon(draft.icon());
        definition.setPropsSchema(draft.propsSchema());
        definition.setInputPorts(draft.inputPorts());
        definition.setOutputPorts(draft.outputPorts());
    }

    /**
     * Structural checks only. Notably absent: any validation of {@code propsSchema} — neither that it
     * is well-formed JSON Schema nor that instance props satisfy it. The backend stores it verbatim
     * and the widget owns its props; see the contract's note.
     */
    private void validate(WidgetDefinitionDraft draft) {
        if (draft.key() == null || !KEY_PATTERN.matcher(draft.key()).matches()) {
            throw new WidgetDefinitionInvalidException("Widget key must be kebab-case: '" + draft.key() + "'.");
        }
        if (draft.name() == null || draft.name().isBlank()) {
            throw new WidgetDefinitionInvalidException("Widget name is required.");
        }
        rejectDuplicatePortNames(draft.inputPorts(), "input");
        rejectDuplicatePortNames(draft.outputPorts(), "output");
    }

    /**
     * A binding names a port by name, so two ports sharing one is not a cosmetic problem — it makes
     * the binding ambiguous and unresolvable at render time.
     */
    private void rejectDuplicatePortNames(List<Port> ports, String direction) {
        if (ports == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Port port : ports) {
            if (port.name() == null || port.name().isBlank()) {
                throw new WidgetDefinitionInvalidException("A " + direction + " port has no name.");
            }
            if (!seen.add(port.name())) {
                throw new WidgetDefinitionInvalidException("Duplicate " + direction + " port name '" + port.name() + "'.");
            }
        }
    }

    /**
     * The writable half of a definition, as the use cases receive it. A record rather than the JPA
     * entity so that a caller cannot hand in a half-managed entity, and so the immutable fields
     * ({@code version}, timestamps) have nowhere to be set from outside.
     */
    public record WidgetDefinitionDraft(
            String key,
            String name,
            String translocoId,
            String description,
            String category,
            String icon,
            Map<String, Object> propsSchema,
            List<Port> inputPorts,
            List<Port> outputPorts) {
    }
}
