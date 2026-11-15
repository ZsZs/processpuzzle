package com.processpuzzle.baseentity.instances.adapters.inbound;

import com.processpuzzle.baseentity.api.BaseEntitiesApi;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.usecases.inbound.CollectionAddress;
import com.processpuzzle.baseentity.instances.usecases.inbound.CreateEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.DeleteEntityInstanceUseCase;
import com.processpuzzle.baseentity.instances.usecases.inbound.FindEntityInstanceByIdUseCase;
import com.processpuzzle.baseentity.model.EntityObjectInput;
import com.processpuzzle.baseentity.model.EntityObjectUpdate;
import com.processpuzzle.baseentity.model.Page;
import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.core.rsql.SortParser;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Inbound REST adapter for the instances module, implementing the generated {@link BaseEntitiesApi}.
 * Talks only to usecases + the mapper; no domain/repository access here.
 */
@RestController
@LogClass
@RequiredArgsConstructor
public class EntityObjectEndpoint implements BaseEntitiesApi {

    private final CreateEntityInstanceUseCase createUseCase;
    private final FindEntityInstanceByIdUseCase findByIdUseCase;
    private final com.processpuzzle.baseentity.instances.usecases.inbound.SearchEntityInstancesUseCase searchUseCase;
    private final com.processpuzzle.baseentity.instances.usecases.inbound.UpdateEntityInstanceUseCase updateUseCase;
    private final DeleteEntityInstanceUseCase deleteUseCase;
    private final com.processpuzzle.baseentity.instances.usecases.inbound.EmbeddedCollectionUseCase embeddedUseCase;
    private final EntityObjectMapper mapper;

    @Override
    public ResponseEntity<Page> listEntities(
        String orgKey,
        String entityDefinitionCode,
        String rsql,
        String sort,
        Integer page,
        Integer size
    ) {
        Sort sortObj = SortParser.parse(sort);
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 20, sortObj);
        return ResponseEntity.ok(mapper.toPage(searchUseCase.search(orgKey, entityDefinitionCode, rsql, pageable)));
    }

    @Override
    public ResponseEntity<com.processpuzzle.baseentity.model.EntityObject> createEntity(
        String orgKey,
        String entityDefinitionCode,
        EntityObjectInput input
    ) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) input.getPayload();
        EntityObject created = createUseCase.create(orgKey, entityDefinitionCode, payload);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
        return ResponseEntity.created(location).body(mapper.toModel(afterCommit(orgKey, entityDefinitionCode, created)));
    }

    @Override
    public ResponseEntity<com.processpuzzle.baseentity.model.EntityObject> getEntity(String orgKey, String entityDefinitionCode, UUID id) {
        return ResponseEntity.ok(mapper.toModel(findByIdUseCase.findById(orgKey, entityDefinitionCode, id)));
    }

    @Override
    public ResponseEntity<com.processpuzzle.baseentity.model.EntityObject> updateEntity(String orgKey, String entityDefinitionCode, UUID id, EntityObjectUpdate request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) request.getPayload();
        Long version = request.getVersion() != null ? request.getVersion().longValue() : null;
        EntityObject updated = updateUseCase.update(orgKey, entityDefinitionCode, id, version, payload);
        return ResponseEntity.ok(mapper.toModel(afterCommit(orgKey, entityDefinitionCode, updated)));
    }

    /**
     * The object as it stands once the write has committed, rather than as the use case saved it. A listener running
     * after that commit may have written derived attributes onto it — base-calculation's order total — and the
     * caller should see them without a reload. Those writes keep the version, so the re-read answers the version
     * the caller saved. This endpoint opens no transaction of its own, which is what makes the use case's commit,
     * and its after-commit listeners, precede the re-read.
     */
    private EntityObject afterCommit(String orgKey, String entityDefinitionCode, EntityObject written) {
        try {
            return findByIdUseCase.findById(orgKey, entityDefinitionCode, written.getId());
        } catch (NotFoundException e) {
            // Deleted in between: what was written is still the honest answer to this request.
            return written;
        }
    }

    @Override
    public ResponseEntity<Void> deleteEntity(String orgKey, String entityDefinitionCode, UUID id, Boolean cascade) {
        deleteUseCase.delete(orgKey, entityDefinitionCode, id, Boolean.TRUE.equals(cascade));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<Map<String, Object>>> listEmbeddedItems(String orgKey, String entityDefinitionCode, UUID id, String attributeCode) {
        var items = embeddedUseCase.list(orgKey, entityDefinitionCode, id, attributeCode);
        return ResponseEntity.ok().eTag(etag(items.version())).body(items.items());
    }

    @Override
    public ResponseEntity<Map<String, Object>> appendEmbeddedItem(String orgKey, String entityDefinitionCode, UUID id, String attributeCode, Map<String, Object> body) {
        var result = embeddedUseCase.append(orgKey, entityDefinitionCode, id, attributeCode, body);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{itemId}").buildAndExpand(result.item().get("id")).toUri();
        return ResponseEntity.created(location).eTag(etag(result.itemVersion())).body(result.item());
    }

    @Override
    public ResponseEntity<Map<String, Object>> patchEmbeddedItem(String orgKey, String entityDefinitionCode, UUID id, String attributeCode, String itemId,
                                                                Map<String, Object> body, String ifMatch) {
        var result = embeddedUseCase.patch(orgKey, entityDefinitionCode, id, attributeCode, itemId, versionOf(ifMatch), body);
        return ResponseEntity.ok().eTag(etag(result.itemVersion())).body(result.item());
    }

    @Override
    public ResponseEntity<Void> deleteEmbeddedItem(String orgKey, String entityDefinitionCode, UUID id, String attributeCode, String itemId, String ifMatch) {
        var result = embeddedUseCase.remove(orgKey, entityDefinitionCode, id, attributeCode, itemId, versionOf(ifMatch));
        return ResponseEntity.noContent().build();
    }

    /** An item's ETag is the item's own version, which is what its If-Match names. */
    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    /** The version an {@code If-Match} header names — `"3"`, `W/"3"` or a bare `3` — or {@code null} when it names none. */
    private static Long versionOf(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || "*".equals(ifMatch.trim())) {
            return null;
        }
        String token = ifMatch.trim().replaceFirst("^W/", "").replace("\"", "");
        try {
            return Long.valueOf(token);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("If-Match must carry an object version, e.g. \"3\"");
        }
    }

    @Override
    public ResponseEntity<List<Map<String, Object>>> listNestedEmbeddedItems(String orgKey, String entityDefinitionCode, UUID id,
                                                                            String attributeCode, String itemId, String childAttributeCode) {
        var items = embeddedUseCase.list(orgKey, entityDefinitionCode, id, nested(attributeCode, itemId, childAttributeCode));
        return ResponseEntity.ok().eTag(etag(items.version())).body(items.items());
    }

    @Override
    public ResponseEntity<Map<String, Object>> appendNestedEmbeddedItem(String orgKey, String entityDefinitionCode, UUID id,
                                                                        String attributeCode, String itemId, String childAttributeCode,
                                                                        Map<String, Object> body) {
        var result = embeddedUseCase.append(orgKey, entityDefinitionCode, id, nested(attributeCode, itemId, childAttributeCode), body);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{childItemId}").buildAndExpand(result.item().get("id")).toUri();
        return ResponseEntity.created(location).eTag(etag(result.itemVersion())).body(result.item());
    }

    @Override
    public ResponseEntity<Map<String, Object>> patchNestedEmbeddedItem(String orgKey, String entityDefinitionCode, UUID id,
                                                                       String attributeCode, String itemId, String childAttributeCode,
                                                                       String childItemId, Map<String, Object> body, String ifMatch) {
        var result = embeddedUseCase.patch(orgKey, entityDefinitionCode, id, nested(attributeCode, itemId, childAttributeCode),
            childItemId, versionOf(ifMatch), body);
        return ResponseEntity.ok().eTag(etag(result.itemVersion())).body(result.item());
    }

    @Override
    public ResponseEntity<Void> deleteNestedEmbeddedItem(String orgKey, String entityDefinitionCode, UUID id, String attributeCode,
                                                         String itemId, String childAttributeCode, String childItemId, String ifMatch) {
        embeddedUseCase.remove(orgKey, entityDefinitionCode, id, nested(attributeCode, itemId, childAttributeCode), childItemId, versionOf(ifMatch));
        return ResponseEntity.noContent().build();
    }

    /** The collection of {@code childAttributeCode} inside the item {@code itemId} of {@code attributeCode}. */
    private static CollectionAddress nested(String attributeCode, String itemId, String childAttributeCode) {
        return CollectionAddress.of(attributeCode).within(itemId, childAttributeCode);
    }
}
