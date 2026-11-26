package com.processpuzzle.calculation.usecase.assist;

import com.processpuzzle.calculation.port.EntityCatalogPort.Attribute;
import com.processpuzzle.calculation.port.EntityCatalogPort.EntityType;
import java.util.List;
import java.util.stream.Collectors;

/** The organization's entity types written out for the model, one line each, in the words the guide uses. */
public final class CatalogDescription {

    /** The most entity types listed; an organization with more has the rest left out, and the model is told. */
    static final int MAX_ENTITIES = 80;

    private CatalogDescription() {
    }

    public static String of(List<EntityType> entities) {
        if (entities.isEmpty()) {
            return "The entity types are not known, so none are listed. Refer only to entities and attributes the "
                + "description or the current source names.";
        }
        StringBuilder text = new StringBuilder("Entity types of the organization. A source names an entity by its code, "
            + "and an attribute by the name before the colon:\n");
        entities.stream().limit(MAX_ENTITIES).forEach(entity -> append(text, entity));
        if (entities.size() > MAX_ENTITIES) {
            text.append("(").append(entities.size() - MAX_ENTITIES).append(" more entity types are not listed.)\n");
        }
        return text.toString();
    }

    private static void append(StringBuilder text, EntityType entity) {
        text.append("- ").append(entity.code());
        if (entity.name() != null && !entity.name().equals(entity.code())) {
            text.append(" (").append(entity.name()).append(')');
        }
        if (entity.embedded()) {
            text.append(" [embedded component: it exists only inside another entity's objects]");
        }
        text.append(": ").append(entity.attributes().stream().map(CatalogDescription::attribute).collect(Collectors.joining("; ")))
            .append('\n');
    }

    private static String attribute(Attribute attribute) {
        String type = attribute.measure() != null ? attribute.measure()
            : attribute.embedded() ? "embedded " + attribute.entity()
            : attribute.entity() != null ? "link to " + attribute.entity() : attribute.type();
        String list = attribute.multiValued() ? "list of " : "";
        return attribute.name() + ": " + list + type + (attribute.required() ? " (required)" : "");
    }
}
