package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectScope;
import com.processpuzzle.baseentity.instances.usecases.outbound.RsqlToInstanceSpecificationPort;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SearchEntityInstancesUseCase {

    private final EntityObjectRepository repository;
    private final EntityObjectScope scope;
    private final RsqlToInstanceSpecificationPort rsqlSpecificationPort;

    @Transactional(readOnly = true)
    public Page<EntityObject> search(String orgKey, String entityDefinitionCode, String rsql, Pageable pageable) {
        return search(orgKey, entityDefinitionCode, rsql, pageable, false);
    }

    /** @param includeRetired the retired objects of a shared type too — for the common's members reinstating one */
    @Transactional(readOnly = true)
    public Page<EntityObject> search(String orgKey, String entityDefinitionCode, String rsql, Pageable pageable, boolean includeRetired) {
        String storageOrgKey = scope.storageOrgKey(orgKey, entityDefinitionCode);
        Specification<EntityObject> specification = rsqlSpecificationPort
            .toSpecification(orgKey, rsql, entityDefinitionCode)
            .and((root, query, cb) -> cb.equal(root.get("orgKey"), storageOrgKey))
            .and((root, query, cb) -> cb.equal(root.get("entityDefinitionCode"), entityDefinitionCode))
            .and(retiredFilter(orgKey, storageOrgKey, includeRetired))
            .and(restrictionFilter(orgKey, entityDefinitionCode));
        return repository.findAll(specification, pageable);
    }

    /**
     * Retired objects are left out, unless asked for — by the common's own members, who see all of them, or by an owner
     * of an owner-written type, who sees its own.
     */
    private Specification<EntityObject> retiredFilter(String orgKey, String storageOrgKey, boolean includeRetired) {
        if (!includeRetired) {
            return (root, query, cb) -> cb.isNull(root.get("retiredAt"));
        }
        if (storageOrgKey.equals(orgKey)) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.or(cb.isNull(root.get("retiredAt")), cb.equal(root.get("ownerOrgKey"), orgKey));
    }

    /**
     * A member's list of a shared type holds its own records and, unless the type is owner-only, the others' that are
     * in a visible state — or, for a type whose records follow another, whose followed record is visible — read from the payload attribute the state machine keeps the state in, or the machine's
     * initial state when a record holds none.
     */
    private Specification<EntityObject> restrictionFilter(String orgKey, String entityDefinitionCode) {
        return (root, query, cb) -> scope.listRestriction(orgKey, entityDefinitionCode).map(restriction -> {
            Predicate own = cb.equal(root.get("ownerOrgKey"), orgKey);
            if (restriction.ownOnly()) {
                return own;
            }
            if (restriction.followed() != null) {
                // A record follows another: visible when the one it follows is, which was worked out as a list of ids.
                if (restriction.followedIds().isEmpty()) {
                    return own;
                }
                Expression<String> followedId = cb.function("jsonb_extract_path_text", String.class, root.get("payload"),
                    cb.literal(restriction.followed()));
                return cb.or(own, followedId.in(restriction.followedIds()));
            }
            Expression<String> held = cb.function("jsonb_extract_path_text", String.class, root.get("payload"),
                cb.literal(restriction.tracking().attributeKey()));
            return cb.or(own, cb.coalesce(held, restriction.tracking().initialState()).in(restriction.states()));
        }).orElseGet(cb::conjunction);
    }
}
