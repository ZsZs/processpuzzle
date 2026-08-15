package com.processpuzzle.baseentity.definition.adapters.inbound;

import com.processpuzzle.baseentity.api.EntityDefinitionsApi;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.usecases.inbound.*;
import com.processpuzzle.baseentity.model.BaseEntityAttributeInput;
import com.processpuzzle.baseentity.model.BaseEntityDefinitionInput;
import com.processpuzzle.baseentity.model.EntityDefinitionStatus;
import com.processpuzzle.baseentity.model.Page;
import com.processpuzzle.core.logging.LogClass;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Inbound REST adapter for the definition module, implementing the generated {@link EntityDefinitionsApi}.
 * Talks only to usecases + the mapper; no domain/repository access here.
 */
@RestController
@LogClass
@RequiredArgsConstructor
public class EntityDefinitionEndpoint implements EntityDefinitionsApi {

    private final CreateEntityDefinitionUseCase createUseCase;
    private final FindEntityDefinitionByCodeUseCase findByCodeUseCase;
    private final FindAllEntityDefinitionsUseCase findAllUseCase;
    private final ReplaceEntityDefinitionUseCase replaceUseCase;
    private final DeleteEntityDefinitionUseCase deleteUseCase;
    private final AddAttributeUseCase addAttributeUseCase;
    private final ReplaceAttributeUseCase replaceAttributeUseCase;
    private final DeleteAttributeUseCase deleteAttributeUseCase;
    private final EntityDefinitionMapper mapper;

    @Override
    public ResponseEntity<Page> listEntityDefinitions(EntityDefinitionStatus status, Boolean isEmbedded, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page != null ? page : 0, size != null ? size : 20);
        var domainStatus = mapper.toDomainStatus(status);
        return ResponseEntity.ok(mapper.toPage(findAllUseCase.findAll(domainStatus, isEmbedded, pageable)));
    }

    @Override
    public ResponseEntity<com.processpuzzle.baseentity.model.BaseEntityDefinition> createEntityDefinition(BaseEntityDefinitionInput input) {
        BaseEntityDefinition created = createUseCase.create(mapper.toDomain(input));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{code}")
            .buildAndExpand(created.getCode())
            .toUri();
        return ResponseEntity.created(location).body(mapper.toModel(created));
    }

    @Override
    public ResponseEntity<com.processpuzzle.baseentity.model.BaseEntityDefinition> getEntityDefinition(String code) {
        return ResponseEntity.ok(mapper.toModel(findByCodeUseCase.findByCode(code)));
    }

    @Override
    public ResponseEntity<com.processpuzzle.baseentity.model.BaseEntityDefinition> replaceEntityDefinition(String code, BaseEntityDefinitionInput input) {
        return ResponseEntity.ok(mapper.toModel(replaceUseCase.replace(code, mapper.toDomain(input))));
    }

    @Override
    public ResponseEntity<Void> deleteEntityDefinition(String code) {
        deleteUseCase.delete(code);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<com.processpuzzle.baseentity.model.BaseEntityAttribute> addAttribute(String code, BaseEntityAttributeInput input) {
        BaseEntityAttribute created = addAttributeUseCase.addAttribute(code, mapper.toDomain(input));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toModel(created));
    }

    @Override
    public ResponseEntity<com.processpuzzle.baseentity.model.BaseEntityAttribute> replaceAttribute(String code, String attributeCode, BaseEntityAttributeInput input) {
        return ResponseEntity.ok(mapper.toModel(replaceAttributeUseCase.replaceAttribute(code, attributeCode, mapper.toDomain(input))));
    }

    @Override
    public ResponseEntity<Void> deleteAttribute(String code, String attributeCode) {
        deleteAttributeUseCase.deleteAttribute(code, attributeCode);
        return ResponseEntity.noContent().build();
    }
}
