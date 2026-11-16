package com.processpuzzle.baseentity.definition.domain;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The rules of sharing that need to know which organization a definition is in, kept apart from
 * {@link EntityDefinitionValidator}, which looks at one definition alone: a type is shared only in a common, and
 * a member of a common defines nothing under the code of a type the common shares.
 */
@Component
public class EntitySharingValidator {

    private final EntityDefinitionRepository repository;
    private final ObjectProvider<DataSpaceDirectory> dataSpaces;

    public EntitySharingValidator(EntityDefinitionRepository repository, ObjectProvider<DataSpaceDirectory> dataSpaces) {
        this.repository = repository;
        this.dataSpaces = dataSpaces;
    }

    /** @throws ValidationException when {@code definition}, about to be stored in {@code orgKey}, breaks a rule of sharing */
    public void validate(String orgKey, BaseEntityDefinition definition) {
        DataSpaceDirectory directory = dataSpaces.getIfAvailable();
        if (definition.isShared() || definition.getSharingVisibility() != null) {
            validateShared(orgKey, definition, directory);
        } else if (directory != null) {
            validateNotReserved(orgKey, definition, directory);
        }
    }

    private void validateShared(String orgKey, BaseEntityDefinition definition, DataSpaceDirectory directory) {
        String code = definition.getCode();
        if (definition.getSharingWrite() == null || definition.getSharingVisibility() == null) {
            throw violation("'%s': sharing needs both write and visibility.".formatted(code));
        }
        if (directory == null || !directory.isCommon(orgKey)) {
            throw violation("'%s' is shared, but '%s' is not a common; only a common shares entity types.".formatted(code, orgKey));
        }
        if (definition.isEmbedded()) {
            throw violation("'%s' is shared but embedded; an embedded component lives in its parent's record.".formatted(code));
        }
        if (definition.getSharingWrite() != SharingWrite.COMMON_ADMIN || definition.getSharingVisibility() != SharingVisibility.PUBLIC) {
            throw violation("'%s': only sharing with write COMMON_ADMIN and visibility PUBLIC is supported yet.".formatted(code));
        }
    }

    private void validateNotReserved(String orgKey, BaseEntityDefinition definition, DataSpaceDirectory directory) {
        Optional<String> common = directory.commonOf(orgKey);
        if (common.isPresent() && sharedByCommon(common.get(), definition.getCode())) {
            throw violation("'%s' is an entity type shared by '%s'; a member defines its own types under other codes."
                .formatted(definition.getCode(), common.get()));
        }
    }

    private boolean sharedByCommon(String common, String code) {
        return repository.findByOrgKeyAndCodeAndChannel(common, code, Channel.RELEASED).filter(BaseEntityDefinition::isShared).isPresent()
            || repository.findByOrgKeyAndCodeAndChannel(common, code, Channel.DRAFT).filter(BaseEntityDefinition::isShared).isPresent();
    }

    private static ValidationException violation(String message) {
        return new ValidationException(List.of(new Violation(null, message)));
    }
}
