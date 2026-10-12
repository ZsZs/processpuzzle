package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.RouteTarget;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import java.util.ArrayList;
import java.util.List;

/** What the routes of an app or a module refer to: the entity an ENTITY route renders, the document a DOCUMENT route shows. */
final class RouteReferences {

    private RouteReferences() {
    }

    static List<DefinitionReference> of(String fromKind, String fromKey, List<AppRoute> routes) {
        List<DefinitionReference> references = new ArrayList<>();
        for (AppRoute route : routes) {
            RouteTarget target = route == null ? null : route.target();
            if (target == null || target.kind() == null) {
                continue;
            }
            switch (target.kind()) {
                case ENTITY -> addIfNamed(references, fromKind, fromKey, DefinitionKinds.ENTITY, target.entityName());
                case DOCUMENT -> addIfNamed(references, fromKind, fromKey, DefinitionKinds.DOCUMENT, target.documentSlug());
                case WIDGETS -> {
                    // Widgets come from the compiled registry, not from a released definition.
                }
            }
        }
        return references;
    }

    private static void addIfNamed(List<DefinitionReference> references, String fromKind, String fromKey, String toKind, String toKey) {
        if (toKey != null && !toKey.isBlank()) {
            DefinitionReference reference = DefinitionReference.required(fromKind, fromKey, toKind, toKey);
            if (!references.contains(reference)) {
                references.add(reference);
            }
        }
    }
}
