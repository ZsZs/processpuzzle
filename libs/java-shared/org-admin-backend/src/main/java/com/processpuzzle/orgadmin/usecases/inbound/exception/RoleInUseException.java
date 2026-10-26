package com.processpuzzle.orgadmin.usecases.inbound.exception;

import com.processpuzzle.core.tenancy.RoleReference;

import java.util.List;

/**
 * The role cannot be deleted yet: somebody still holds it, or something still names it. Surfaced as
 * 409 with both facts, so the administrator can release the role first.
 */
public class RoleInUseException extends RuntimeException {

    private final boolean held;
    private final transient List<RoleReference> references;

    public RoleInUseException(String name, boolean held, List<RoleReference> references) {
        super("Role '" + name + "' is still in use" + (held ? ": a user holds it" : "")
                + (references.isEmpty() ? "" : "; " + references.size() + " reference(s) name it") + ".");
        this.held = held;
        this.references = List.copyOf(references);
    }

    public boolean isHeld() {
        return held;
    }

    public List<RoleReference> getReferences() {
        return references;
    }
}
