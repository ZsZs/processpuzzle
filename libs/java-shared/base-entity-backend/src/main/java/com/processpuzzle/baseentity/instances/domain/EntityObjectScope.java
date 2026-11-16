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

    @Autowired
    public EntityObjectScope(EntityObjectRepository repository, EntityDefinitionLookupPort definitions) {
        this.repository = repository;
        this.definitions = definitions;
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
     * Refuses a write to a shared type from a member: the objects of a shared type are the common's, and a member
     * only reads them. The common's own requests, addressed to its key, are the ones that write.
     */
    public String requireWritable(String orgKey, String entityDefinitionCode) {
        String storage = storageOrgKey(orgKey, entityDefinitionCode);
        if (!storage.equals(orgKey)) {
            throw new ForbiddenException(
                "'%s' is shared by '%s' and cannot be changed from '%s'".formatted(entityDefinitionCode, storage, orgKey));
        }
        return storage;
    }

    /**
     * The object, if it is of type {@code entityDefinitionCode} and visible to {@code orgKey}. An id of
     * another organization's object, or of an object of another type, is not found — the same answer, so
     * a caller cannot probe which of the two it was.
     */
    public Optional<EntityObject> find(String orgKey, String entityDefinitionCode, UUID id) {
        return repository.findByIdAndOrgKey(id, storageOrgKey(orgKey, entityDefinitionCode))
            .filter(object -> object.getEntityDefinitionCode().equals(entityDefinitionCode));
    }

    /** The objects of the type that are not retired. */
    public List<EntityObject> findAll(String orgKey, String entityDefinitionCode) {
        return findEvery(orgKey, entityDefinitionCode).stream().filter(object -> !object.isRetired()).toList();
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
