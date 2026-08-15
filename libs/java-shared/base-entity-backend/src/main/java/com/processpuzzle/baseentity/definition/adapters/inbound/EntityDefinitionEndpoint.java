package com.processpuzzle.baseentity.definition.adapters.inbound;

import com.processpuzzle.baseentity.definition.adapters.inbound.dto.BaseEntityAttributeDto;
import com.processpuzzle.baseentity.definition.adapters.inbound.dto.BaseEntityDefinitionDto;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionStatus;
import com.processpuzzle.baseentity.definition.usecases.inbound.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Inbound REST adapter for the definition module — see base-entity-api.yaml
 * /entity-definitions paths. Talks only to usecases + the mapper; no domain/repository access
 * here.
 */
@RestController
@RequestMapping("/api/base-entity/entity-definitions")
@RequiredArgsConstructor
public class EntityDefinitionEndpoint {

    private final CreateEntityDefinitionUseCase createUseCase;
    private final FindEntityDefinitionByCodeUseCase findByCodeUseCase;
    private final FindAllEntityDefinitionsUseCase findAllUseCase;
    private final ReplaceEntityDefinitionUseCase replaceUseCase;
    private final DeleteEntityDefinitionUseCase deleteUseCase;
    private final AddAttributeUseCase addAttributeUseCase;
    private final ReplaceAttributeUseCase replaceAttributeUseCase;
    private final DeleteAttributeUseCase deleteAttributeUseCase;
    private final EntityDefinitionMapper mapper;

    @GetMapping
    public Page<BaseEntityDefinitionDto> findAll(
        @RequestParam(required = false) EntityDefinitionStatus status,
        @RequestParam(required = false) Boolean isEmbedded,
        Pageable pageable
    ) {
        return findAllUseCase.findAll(status, isEmbedded, pageable).map(mapper::toDto);
    }

    @PostMapping
    public ResponseEntity<BaseEntityDefinitionDto> create(
        @Valid @RequestBody BaseEntityDefinitionDto input
    ) {
        BaseEntityDefinition created = createUseCase.create(mapper.fromDto(input));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{code}")
            .buildAndExpand(created.getCode())
            .toUri();
        return ResponseEntity.created(location).body(mapper.toDto(created));
    }

    @GetMapping("/{code}")
    public BaseEntityDefinitionDto get(@PathVariable String code) {
        return mapper.toDto(findByCodeUseCase.findByCode(code));
    }

    @PutMapping("/{code}")
    public BaseEntityDefinitionDto replace(@PathVariable String code, @Valid @RequestBody BaseEntityDefinitionDto input) {
        return mapper.toDto(replaceUseCase.replace(code, mapper.fromDto(input)));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        deleteUseCase.delete(code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{code}/attributes")
    public ResponseEntity<BaseEntityAttributeDto> addAttribute(
        @PathVariable String code,
        @Valid @RequestBody BaseEntityAttributeDto input
    ) {
        var created = addAttributeUseCase.addAttribute(code, mapper.fromDto(input));
        return ResponseEntity.status(201).body(mapper.toDto(created));
    }

    @PutMapping("/{code}/attributes/{attributeCode}")
    public BaseEntityAttributeDto replaceAttribute(
        @PathVariable String code,
        @PathVariable String attributeCode,
        @Valid @RequestBody BaseEntityAttributeDto input
    ) {
        return mapper.toDto(replaceAttributeUseCase.replaceAttribute(code, attributeCode, mapper.fromDto(input)));
    }

    @DeleteMapping("/{code}/attributes/{attributeCode}")
    public ResponseEntity<Void> deleteAttribute(@PathVariable String code, @PathVariable String attributeCode) {
        deleteAttributeUseCase.deleteAttribute(code, attributeCode);
        return ResponseEntity.noContent().build();
    }
}
