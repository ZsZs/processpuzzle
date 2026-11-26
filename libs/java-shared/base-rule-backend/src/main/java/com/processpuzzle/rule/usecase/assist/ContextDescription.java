package com.processpuzzle.rule.usecase.assist;

import com.processpuzzle.rule.port.RuleContext;
import com.processpuzzle.rule.port.RuleContext.ContextAttribute;
import java.util.List;
import java.util.Optional;

/** An entity written out for the model: what a rule's {@code entity} has, in the words the guide uses. */
public final class ContextDescription {

    private ContextDescription() {
    }

    public static String of(Optional<RuleContext> context) {
        if (context.isEmpty()) {
            return "The entity is not known, so its attributes are not listed. Refer only to attributes the description "
                + "or the current value names.";
        }
        StringBuilder text = new StringBuilder("Entity: ").append(context.get().name())
            .append("\nAttributes of `entity`:\n");
        append(text, context.get().attributes(), 0);
        return text.toString();
    }

    private static void append(StringBuilder text, List<ContextAttribute> attributes, int depth) {
        for (ContextAttribute attribute : attributes) {
            text.append("  ".repeat(depth)).append("- ").append(attribute.name()).append(": ").append(typeOf(attribute));
            if (attribute.required()) {
                text.append(", required");
            }
            text.append('\n');
            append(text, attribute.children(), depth + 1);
        }
    }

    private static String typeOf(ContextAttribute attribute) {
        String type = attribute.measure() != null ? attribute.measure() + measureOptions(attribute)
            : attribute.embedded() ? "embedded object" : attribute.type();
        return attribute.multiValued() ? "list of " + type : type;
    }

    private static String measureOptions(ContextAttribute attribute) {
        return attribute.measureOptions().isEmpty() ? "" : " (" + String.join(", ", attribute.measureOptions()) + ")";
    }
}
