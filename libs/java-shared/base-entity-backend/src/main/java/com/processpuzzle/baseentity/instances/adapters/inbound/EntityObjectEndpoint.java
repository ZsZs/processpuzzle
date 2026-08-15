package com.processpuzzle.baseentity.instances.adapters.inbound;

import com.processpuzzle.baseentity.api.EntitiesApi;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.usecases.inbound.*;
import com.processpuzzle.baseentity.model.EntityObjectInput;
import com.processpuzzle.baseentity.model.EntityObjectUpdate;
import com.processpuzzle.baseentity.model.Page;
import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.core.rsql.SortParser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * Inbound REST adapter for the instances module, implementing the generated {@link EntitiesApi}.
 * Talks only to usecases + the mapper; no domain/repository access here.
 */
@RestController
@LogClass
@RequiredArgsConstructor
public class EntityObjectEndpoint implements EntitiesApi {

    private final CreateEntityInstanceUseCase createUseCase;
    private final FindEntityInstanceByIdUseCase findByIdUseCase;
    private final SearchEntityInstancesUseCase searchUseCase;
    private final UpdateEntityInstanceUseCase updateUseCase;
    private final DeleteEntityInstanceUseCase deleteUseCase;
    private final EntityObjectMapper mapper;

    @Override
    public ResponseEntity<Page> listEntities(
        String entityDefinitionCode,
        String rsql,
        String sort,
        Integer page,
        Integer size
    ) {
        Sort sortObj = SortParser.parse(sort);
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 20, sortObj);
        return ResponseEntity.ok(mapper.toPage(searchUseCase.search(entityDefinitionCode, rsql, pageable)));
    }

    @Override
    public ResponseEntity<com.processpuzzle.baseentity.model.EntityObject> createEntity(EntityObjectInput input) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) input.getPayload();
        EntityObject created = createUseCase.create(input.getEntityDefinitionCode(), payload);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
        return ResponseEntity.created(location).body(mapper.toModel(created));
    }

    @Override
    public ResponseEntity<com.processpuzzle.baseentity.model.EntityObject> getEntity(UUID id) {
        return ResponseEntity.ok(mapper.toModel(findByIdUseCase.findById(id)));
    }

    @Override
    public ResponseEntity<com.processpuzzle.baseentity.model.EntityObject> updateEntity(UUID id, EntityObjectUpdate request) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) request.getPayload();
        Long version = request.getVersion() != null ? request.getVersion().longValue() : null;
        return ResponseEntity.ok(mapper.toModel(updateUseCase.update(id, version, payload)));
    }

    @Override
    public ResponseEntity<Void> deleteEntity(UUID id, Boolean cascade) {
        deleteUseCase.delete(id, Boolean.TRUE.equals(cascade));
        return ResponseEntity.noContent().build();
    }
}
