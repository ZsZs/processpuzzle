package com.processpuzzle.event.usecase;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventKind;
import com.processpuzzle.event.domain.EventParameter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Everything a definition is checked for when it is saved or imported: its own rules ({@link EventDefinition#validate})
 * and, for a SYSTEM definition, that every declared parameter names an attribute of the subject's entity type — the
 * attribute it is filled from — as far as {@link SubjectAttributePort} can tell.
 */
@Component
public class EventDefinitionValidator {

    private final SubjectAttributePort attributes;

    @Autowired
    public EventDefinitionValidator(ObjectProvider<SubjectAttributePort> attributes) {
        this(attributes.getIfUnique(() -> SubjectAttributePort.UNKNOWN));
    }

    public EventDefinitionValidator(SubjectAttributePort attributes) {
        this.attributes = attributes;
    }

    /** @return the violations, empty when the definition is valid */
    public List<String> validate(EventDefinition definition) {
        List<String> errors = new ArrayList<>(definition.validate());
        if (errors.isEmpty() && definition.getKind() == EventKind.SYSTEM) {
            for (EventParameter parameter : definition.getParameters()) {
                boolean missing = attributes.hasAttribute(definition.getOrgKey(), definition.getSubjectType(), parameter.getName())
                        .map(has -> !has).orElse(false);
                if (missing) {
                    errors.add("parameter '%s' names no attribute of '%s', so nothing would fill it"
                            .formatted(parameter.getName(), definition.getSubjectType()));
                }
            }
        }
        return errors;
    }
}
