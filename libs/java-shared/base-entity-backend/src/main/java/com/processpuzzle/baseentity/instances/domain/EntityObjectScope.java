package com.processpuzzle.baseentity.instances.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import com.processpuzzle.baseentity.common.ForbiddenException;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.core.definition.RecordStates;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort.SharingView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The one place that decides which organization's objects a request may see, and the queries that
 * apply the decision. Every read and write of {@link EntityObject} — the REST use cases, the published
 * {@code EntityObjectAccess}, the delete guard, the seed loader — goes through here instead of
 * filtering on {@code orgKey} itself.
 *
 * <p><b>Why a seam rather than a {@code WHERE org_key = ?} at each call.</b> Entity types come in two kinds.
 * A <em>tenant</em> type keeps its objects per organization. A <em>shared</em> type belongs to a common — the
 * organization the platform keeps for a business starter — whose members all read its objects alike; they are stored
 * under the common's key, so {@code org_key} stays not-null and {@code (org_key, code)} stays unique.
 * {@link #storageOrgKey} answers which, and nothing else changes; {@link #requireWritable} keeps a member from
 * writing what is not its own. See {@code docs/shared-data-commons-concept}.
 */
@Component
public class EntityObjectScope {

    private final EntityObjectRepository repository;
    private final EntityDefinitionLookupPort definitions;
    private final ObjectProvider<RecordStates> recordStates;

    @Autowired
    public EntityObjectScope(EntityObjectRepository repository, EntityDefinitionLookupPort definitions,
                             ObjectProvider<RecordStates> recordStates) {
        this.repository = repository;
        this.definitions = definitions;
        this.recordStates = recordStates;
    }

    /** A scope of a host without state machines: a type limited to some states is then visible to its owners only. */
    public EntityObjectScope(EntityObjectRepository repository, EntityDefinitionLookupPort definitions) {
        this(repository, definitions, null);
    }

    /** A scope in which no type is shared. */
    public EntityObjectScope(EntityObjectRepository repository) {
        this(repository, (orgKey, code, channel) -> Optional.empty());
    }

    /**
     * The organization key the objects of {@code entityDefinitionCode} are stored under, as seen from a
     * request addressed to {@code orgKey}: {@code orgKey} itself for a tenant type, the common's key for a type its
     * common shares with it.
     */
    public String storageOrgKey(String orgKey, String entityDefinitionCode) {
        return definitions.sharedFrom(orgKey, entityDefinitionCode).orElse(orgKey);
    }

    /**
     * Refuses a write to a shared type from a member, unless the type lets its owners write: the objects of a shared
     * type are the common's, and a member only reads them. The common's own requests, addressed to its key, write
     * all of them. For an owner-written type a member may still only change the objects it owns —
     * {@link #requireOwnedBy}.
     */
    public String requireWritable(String orgKey, String entityDefinitionCode) {
        String storage = storageOrgKey(orgKey, entityDefinitionCode);
        if (!storage.equals(orgKey) && !sharingOf(orgKey, entityDefinitionCode).is(SharingView::ownerWrites)) {
            throw new ForbiddenException(
                "'%s' is shared by '%s' and cannot be changed from '%s'".formatted(entityDefinitionCode, storage, orgKey));
        }
        return storage;
    }

    /** The key to record as owner of a new object: the member creating it in an owner-written shared type, else none. */
    public Optional<String> ownerForNew(String orgKey, String entityDefinitionCode) {
        return storageOrgKey(orgKey, entityDefinitionCode).equals(orgKey) ? Optional.empty() : Optional.of(orgKey);
    }

    /**
     * Refuses a member the change of an object another member owns. The common's own requests may change any object;
     * a type without owners has nothing to check, because {@link #requireWritable} has refused the member already.
     */
    public void requireOwnedBy(String orgKey, String entityDefinitionCode, EntityObject object) {
        if (!storageOrgKey(orgKey, entityDefinitionCode).equals(orgKey) && !orgKey.equals(object.getOwnerOrgKey())) {
            throw new ForbiddenException("'%s' belongs to another organization and cannot be changed from '%s'"
                .formatted(object.getId(), orgKey));
        }
    }

    /**
     * What a member's reads of a shared type are limited to, beyond the records it owns, which it always sees: nothing
     * more for an `OWNER_ONLY` type; the records in a visible state for a type with {@code visibleWhen}; nothing at
     * all — no restriction — for the common's own requests and a type that limits no one.
     *
     * @param states   the states in which a record is visible to a non-owner; used when {@code tracking} is set
     * @param tracking where a record's state is kept; null when only the owner sees a record
     */
    public record ListRestriction(List<String> states, RecordStates.StateTracking tracking) {

        public boolean ownOnly() {
            return tracking == null;
        }
    }

    public Optional<ListRestriction> listRestriction(String orgKey, String entityDefinitionCode) {
        String storage = storageOrgKey(orgKey, entityDefinitionCode);
        if (storage.equals(orgKey)) {
            return Optional.empty();
        }
        return definitions.sharingOf(orgKey, entityDefinitionCode).flatMap(sharing -> {
            if (sharing.ownerOnly()) {
                return Optional.of(new ListRestriction(List.of(), null));
            }
            if (!sharing.limitedByState()) {
                return Optional.empty();
            }
            // Without a state machine to read the state from, nothing can be shown to a non-owner.
            RecordStates states = recordStates == null ? null : recordStates.getIfAvailable();
            return Optional.of(new ListRestriction(sharing.visibleWhen(),
                states == null ? null : states.trackingOf(storage, entityDefinitionCode).orElse(null)));
        });
    }

    /**
     * Whether {@code orgKey} may see {@code object}: the common's own requests and an object's owner see it; any
     * other member sees it unless the type is `OWNER_ONLY`, or limited to some states and the object is in another.
     */
    public boolean isVisibleTo(String orgKey, String entityDefinitionCode, EntityObject object) {
        return orgKey.equals(object.getOwnerOrgKey()) || listRestriction(orgKey, entityDefinitionCode)
            .map(restriction -> !restriction.ownOnly() && restriction.states().contains(restriction.tracking().stateIn(object.getPayload())))
            .orElse(true);
    }

    private Sharing sharingOf(String orgKey, String entityDefinitionCode) {
        return new Sharing(definitions.sharingOf(orgKey, entityDefinitionCode));
    }

    private record Sharing(Optional<SharingView> view) {
        boolean is(java.util.function.Predicate<SharingView> test) {
            return view.filter(test).isPresent();
        }
    }

    /**
     * The object, if it is of type {@code entityDefinitionCode} and visible to {@code orgKey}. An id of
     * another organization's object, or of an object of another type, is not found — the same answer, so
     * a caller cannot probe which of the two it was.
     */
    public Optional<EntityObject> find(String orgKey, String entityDefinitionCode, UUID id) {
        return repository.findByIdAndOrgKey(id, storageOrgKey(orgKey, entityDefinitionCode))
            .filter(object -> object.getEntityDefinitionCode().equals(entityDefinitionCode))
            .filter(object -> isVisibleTo(orgKey, entityDefinitionCode, object));
    }

    /** The objects of the type that are not retired. */
    public List<EntityObject> findAll(String orgKey, String entityDefinitionCode) {
        return findEvery(orgKey, entityDefinitionCode).stream()
            .filter(object -> !object.isRetired() && isVisibleTo(orgKey, entityDefinitionCode, object)).toList();
    }

    private List<EntityObject> findEvery(String orgKey, String entityDefinitionCode) {
        return repository.findAllByOrgKeyAndEntityDefinitionCode(storageOrgKey(orgKey, entityDefinitionCode), entityDefinitionCode);
    }

    /** Whether the type is a shared type as {@code orgKey} sees it — its common's, or its own when it is a common. */
    public boolean isShared(String orgKey, String entityDefinitionCode) {
        return definitions.isShared(orgKey, entityDefinitionCode);
    }

    /**
     * The organizations whose objects can refer to an object of {@code orgKey}'s: the organization itself and, for a
     * member, its common. The delete guard looks no further.
     */
    public Collection<String> referringOrgKeys(String orgKey, String entityDefinitionCode) {
        return List.of(orgKey, storageOrgKey(orgKey, entityDefinitionCode)).stream().distinct().toList();
    }

    /** The payloads alone of every object of {@code entityDefinitionCode}, retired ones included, without loading the objects as entities. */
    public List<Map<String, Object>> payloads(String orgKey, String entityDefinitionCode) {
        return repository.findPayloadsByOrgKeyAndEntityDefinitionCodeIn(storageOrgKey(orgKey, entityDefinitionCode),
                List.of(entityDefinitionCode)).stream()
            .map(EntityObjectRepository.PayloadView::getPayload)
            .toList();
    }

    /**
     * Rewrites the payload of every object of {@code entityDefinitionCode} — a release migrating the objects to fit
     * the definition it releases. {@code rewrite} gets a copy of each payload and returns the payload the object should
     * have; an object whose payload it leaves equal is not written. Each written object's version moves on, so an edit
     * made from the old payload fails on the optimistic lock instead of undoing the rewrite. Not validated and
     * publishes no event: the release checks the result, and a migration is not a business event.
     *
     * @throws ForbiddenException for a shared type: a member's release never rewrites the common's objects
     * @return how many objects were written
     */
    public long rewriteAll(String orgKey, String entityDefinitionCode, UnaryOperator<Map<String, Object>> rewrite) {
        requireWritable(orgKey, entityDefinitionCode);
        long written = 0;
        for (EntityObject object : findEvery(orgKey, entityDefinitionCode)) {
            Map<String, Object> rewritten = rewrite.apply(new LinkedHashMap<>(object.getPayload()));
            if (!rewritten.equals(object.getPayload())) {
                object.setPayload(rewritten);
                repository.save(object);
                written++;
            }
        }
        return written;
    }

    public boolean existsAny(String orgKey, String entityDefinitionCode) {
        return repository.existsByOrgKeyAndEntityDefinitionCode(storageOrgKey(orgKey, entityDefinitionCode), entityDefinitionCode);
    }
}
