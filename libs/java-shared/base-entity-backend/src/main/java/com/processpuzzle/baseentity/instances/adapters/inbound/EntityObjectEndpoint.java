package com.processpuzzle.baseentity.instances.adapters.inbound;

import com.processpuzzle.baseentity.instances.adapters.inbound.dto.EntityObjectDto;
import com.processpuzzle.baseentity.instances.usecases.inbound.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

/**
 * Inbound REST adapter for the instances module — see base-entity-api.yaml /entities paths.
 * Talks only to usecases + the mapper; no domain/repository access here.
 */
@RestController
@RequestMapping("/api/base-entity/entities")
@RequiredArgsConstructor
public class EntityObjectEndpoint {

    private final CreateEntityInstanceUseCase createUseCase;
    private final FindEntityInstanceByIdUseCase findByIdUseCase;
    private final SearchEntityInstancesUseCase searchUseCase;
    private final UpdateEntityInstanceUseCase updateUseCase;
    private final DeleteEntityInstanceUseCase deleteUseCase;
    private final EntityObjectMapper mapper;

    @GetMapping
    public Page<EntityObjectDto> search(
        @RequestParam String entityDefinitionCode,
        @RequestParam(required = false) String rsql,
        Pageable pageable
    ) {
        return searchUseCase.search(entityDefinitionCode, rsql, pageable).map(mapper::toDto);
    }

    @PostMapping
    public ResponseEntity<EntityObjectDto> create(
        @RequestBody EntityObjectDto input,
        UriComponentsBuilder uriBuilder
    ) {
        var created = createUseCase.create(input.getEntityDefinitionCode(), input.getPayload());
        return ResponseEntity
            .created(uriBuilder.path("/api/base-entity/entities/{id}").build(created.getId()))
            .body(mapper.toDto(created));
    }

    @GetMapping("/{id}")
    public EntityObjectDto get(@PathVariable UUID id) {
        return mapper.toDto(findByIdUseCase.findById(id));
    }

    @PutMapping("/{id}")
    public EntityObjectDto update(@PathVariable UUID id, @RequestBody EntityObjectUpdateRequest request) {
        return mapper.toDto(updateUseCase.update(id, request.version(), request.payload()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "false") boolean cascade
    ) {
        deleteUseCase.delete(id, cascade);
        return ResponseEntity.noContent().build();
    }

    public record EntityObjectUpdateRequest(Long version, Map<String, Object> payload) {
    }
}
