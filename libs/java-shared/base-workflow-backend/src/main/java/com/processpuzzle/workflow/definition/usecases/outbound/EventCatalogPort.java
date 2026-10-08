package com.processpuzzle.workflow.definition.usecases.outbound;

import com.processpuzzle.shared.event.CatalogEventKind;
import java.util.Optional;

/**
 * Asks the organization's event catalog about an event definition — when a workflow naming one, in a
 * TRIGGERING_EVENT start event or an intermediate event, is saved, so a typo is refused then rather
 * than surfacing as a workflow that silently never starts or never moves on.
 *
 * <p>The catalog is base-event's, which this module does not depend on: the host application
 * supplies the adapter. Without one, {@code WorkflowValidator} falls back to
 * {@link PermitAllEventCatalogPort}.
 */
public interface EventCatalogPort {

    boolean exists(String orgKey, String eventDefinitionId);

    /**
     * The definition's kind, or empty when it is unknown — no such definition, or an adapter that
     * cannot tell. Empty skips the kind rules of an intermediate event (a THROW may not name a SYSTEM
     * event, a MESSAGE needs a correlation key) rather than failing them.
     */
    default Optional<CatalogEventKind> kindOf(String orgKey, String eventDefinitionId) {
        return Optional.empty();
    }
}
