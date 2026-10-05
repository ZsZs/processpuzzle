package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FindEntityInstanceByIdUseCase {

    private final EntityObjectRepository repository;

    @Transactional(readOnly = true)
    public EntityObject findById(String orgKey, UUID id) {
        return repository.findByIdAndOrgKey(id, orgKey)
            .orElseThrow(() -> new NotFoundException("No entity instance with id '%s'".formatted(id)));
    }
}
