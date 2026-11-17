package com.processpuzzle.baseentity.definition.domain;

import com.processpuzzle.baseentity.common.ValidationException;
import com.processpuzzle.baseentity.common.ValidationException.Violation;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
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
        validateFrom(orgKey, definition);
        validateVisibleWhen(definition);
        if (definition.getSharingWrite() == SharingWrite.SELF) {
            throw violation("'%s': write SELF is reserved; records owned by a person need their own consent design.".formatted(code));
        }
    }

    /**
     * A type that follows another record names a single-valued REFERENCE attribute of its own whose target is a shared
     * type of the same common that follows nothing: one level, never a chain. A target that is not defined yet is
     * not checked here — a bundle may define the booking before the event.
     */
    private void validateFrom(String orgKey, BaseEntityDefinition definition) {
        String from = definition.getSharingFrom();
        if (from == null) {
            return;
        }
        String code = definition.getCode();
        BaseEntityAttribute attribute = definition.getAttributes().stream().filter(each -> from.equals(each.getCode())).findFirst()
            .orElseThrow(() -> violation("'%s': from names '%s', which is not an attribute of the type.".formatted(code, from)));
        if (attribute.getValueKind() != ValueKind.REFERENCE || attribute.isMultiValued() || attribute.getLinkedEntityType() == null) {
            throw violation("'%s': from must name a single-valued REFERENCE attribute, '%s' is not.".formatted(code, from));
        }
        if (definition.getSharingWrite() != SharingWrite.OWNER_ORG || definition.getSharingVisibility() != SharingVisibility.PUBLIC
            || !definition.getSharingVisibleWhen().isEmpty()) {
            throw violation("'%s': a type that follows a record takes its owner and visibility from it; leave write, visibility and visibleWhen out."
                .formatted(code));
        }
        String target = attribute.getLinkedEntityType();
        Optional<BaseEntityDefinition> followed = Stream.of(Channel.DRAFT, Channel.RELEASED)
            .map(channel -> repository.findByOrgKeyAndCodeAndChannel(orgKey, target, channel)).flatMap(Optional::stream).findFirst();
        if (followed.isPresent() && (!followed.get().isShared() || followed.get().getSharingFrom() != null)) {
            throw violation("'%s': '%s' must be a shared type that does not itself follow a record (one level only)."
                .formatted(code, target));
        }
    }

    private void validateVisibleWhen(BaseEntityDefinition definition) {
        List<String> states = definition.getSharingVisibleWhen() == null ? List.of() : definition.getSharingVisibleWhen();
        if (states.isEmpty()) {
            return;
        }
        if (definition.getSharingVisibility() != SharingVisibility.PUBLIC) {
            throw violation("'%s': visibleWhen limits a PUBLIC type; an OWNER_ONLY one is not visible to other members at all."
                .formatted(definition.getCode()));
        }
        if (states.stream().anyMatch(state -> state == null || state.isBlank())) {
            throw violation("'%s': visibleWhen names a blank state.".formatted(definition.getCode()));
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
