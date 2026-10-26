package com.processpuzzle.orgadmin.usecases.inbound;

import com.processpuzzle.core.tenancy.RoleReference;
import com.processpuzzle.core.tenancy.RoleReferenceContributor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything that names a role, gathered from the features that hold such names.
 *
 * <p>An {@link ObjectProvider} rather than a list, so a deployment with no contributing feature has an
 * empty answer instead of a startup failure. See {@link RoleReferenceContributor}.
 */
@Component
public class RoleReferences {

    private final ObjectProvider<RoleReferenceContributor> contributors;

    public RoleReferences(ObjectProvider<RoleReferenceContributor> contributors) {
        this.contributors = contributors;
    }

    /** Role name to everything in {@code orgKey} that names it. */
    public Map<String, List<RoleReference>> in(String orgKey) {
        Map<String, List<RoleReference>> merged = new LinkedHashMap<>();
        contributors.orderedStream().forEach(contributor ->
                contributor.referencedRoles(orgKey).forEach((role, references) ->
                        merged.computeIfAbsent(role, name -> new ArrayList<>()).addAll(references)));
        return merged;
    }
}
