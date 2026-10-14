package com.processpuzzle.baseentity.instances.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The one place that decides which organization's objects a request may see, and the queries that
 * apply the decision. Every read and write of {@link EntityObject} — the REST use cases, the published
 * {@code EntityObjectAccess}, the delete guard, the seed loader — goes through here instead of
 * filtering on {@code orgKey} itself.
 *
 * <p><b>Why a seam rather than a {@code WHERE org_key = ?} at each call.</b> Entity types will come in
 * two kinds. A <em>tenant</em> type, the only kind today, keeps its objects per organization. A
 * <em>global</em> type is shared reference data — currencies, countries — whose objects every tenant
 * reads alike. Those will be stored under a reserved organization key ({@code _global}), so
 * {@code org_key} stays not-null and {@code (org_key, code)} stays unique; the definition will carry the
 * flag. When it does, {@link #storageOrgKey} answers {@code _global} for a flagged type and nothing
 * else changes. Who may <em>write</em> a global type — platform staff only, presumably — is a decision for
 * that change, not this class.
 */
@Component
@RequiredArgsConstructor
public class EntityObjectScope {

    private final EntityObjectRepository repository;

    /**
     * The organization key the objects of {@code entityDefinitionCode} are stored under, as seen from a
     * request addressed to {@code orgKey}. Today always {@code orgKey}: every entity type is a tenant type.
     */
    public String storageOrgKey(String orgKey, String entityDefinitionCode) {
        return orgKey;
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

    public List<EntityObject> findAll(String orgKey, String entityDefinitionCode) {
        return repository.findAllByOrgKeyAndEntityDefinitionCode(storageOrgKey(orgKey, entityDefinitionCode), entityDefinitionCode);
    }

    /** The payloads alone of every object of {@code entityDefinitionCode}, without loading the objects as entities. */
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
     * @return how many objects were written
     */
    public long rewriteAll(String orgKey, String entityDefinitionCode, UnaryOperator<Map<String, Object>> rewrite) {
        long written = 0;
        for (EntityObject object : findAll(orgKey, entityDefinitionCode)) {
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
