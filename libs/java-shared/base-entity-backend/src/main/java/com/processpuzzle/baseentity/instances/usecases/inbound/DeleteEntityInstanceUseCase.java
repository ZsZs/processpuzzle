package com.processpuzzle.baseentity.instances.usecases.inbound;

import com.processpuzzle.baseentity.common.ConflictException;
import com.processpuzzle.baseentity.common.NotFoundException;
import com.processpuzzle.baseentity.instances.domain.EntityObject;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteEntityInstanceUseCase {

    private final EntityObjectRepository repository;

    public void delete(UUID id, boolean cascade) {
        EntityObject entityObject = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("No entity instance with id '%s'".formatted(id)));

        if (!cascade && repository.existsAnyReferenceTo(id.toString())) {
            throw new ConflictException(
                "'%s' is still referenced by other entities — pass cascade=true to delete anyway".formatted(id));
        }
        repository.delete(entityObject);
    }
}
