package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Implements the instances module's EntityDefinitionLookupPort by reaching into the definition
 * module and mapping its JPA entities down to the read-only view records the port contract
 * defines.
 */
@Component
public class EntityDefinitionLookupAdapter implements EntityDefinitionLookupPort {

    private final EntityDefinitionRepository entityDefinitionRepository;
    private final ObjectProvider<DataSpaceDirectory> dataSpaces;

    public EntityDefinitionLookupAdapter(EntityDefinitionRepository entityDefinitionRepository,
                                         ObjectProvider<DataSpaceDirectory> dataSpaces) {
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.dataSpaces = dataSpaces;
    }

    /**
     * The organization's own definition, or else the shared type of its common. A member cannot define a type
     * under a shared type's code, so the two never compete; a member cannot draft a shared type either, so even
     * a draft lookup reads the common's released row.
     */
    @Override
    public Optional<EntityDefinitionView> findByCode(String orgKey, String code, Channel channel) {
        Optional<EntityDefinitionView> own = entityDefinitionRepository.findByOrgKeyAndCodeAndChannel(orgKey, code, channel).map(this::toView);
        if (own.isPresent()) {
            return own;
        }
        return sharedDefinition(orgKey, code).map(this::toView);
    }

    @Override
    public boolean isShared(String orgKey, String code) {
        return entityDefinitionRepository.findByOrgKeyAndCodeAndChannel(orgKey, code, Channel.RELEASED)
            .map(BaseEntityDefinition::isShared)
            .orElseGet(() -> sharedDefinition(orgKey, code).isPresent());
    }

    @Override
    public Optional<SharingView> sharingOf(String orgKey, String code) {
        Optional<BaseEntityDefinition> own = entityDefinitionRepository.findByOrgKeyAndCodeAndChannel(orgKey, code, Channel.RELEASED);
        Optional<BaseEntityDefinition> shared = own.isPresent() ? own : sharedDefinition(orgKey, code);
        return shared.filter(BaseEntityDefinition::isShared)
            .map(definition -> new SharingView(definition.getSharingWrite().name(), definition.getSharingVisibility().name(),
                definition.getSharingVisibleWhen(), definition.getSharingFrom(), followedType(definition)));
    }

    private static String followedType(BaseEntityDefinition definition) {
        return definition.getSharingFrom() == null ? null : definition.getAttributes().stream()
            .filter(attribute -> definition.getSharingFrom().equals(attribute.getCode()))
            .map(BaseEntityAttribute::getLinkedEntityType).findFirst().orElse(null);
    }

    @Override
    public Optional<String> sharedFrom(String orgKey, String code) {
        if (entityDefinitionRepository.existsByOrgKeyAndCodeAndChannel(orgKey, code, Channel.RELEASED)) {
            return Optional.empty();
        }
        return sharedDefinition(orgKey, code).map(BaseEntityDefinition::getOrgKey);
    }

    private Optional<BaseEntityDefinition> sharedDefinition(String orgKey, String code) {
        DataSpaceDirectory directory = dataSpaces.getIfAvailable();
        if (directory == null) {
            return Optional.empty();
        }
        return directory.commonOf(orgKey)
            .flatMap(common -> entityDefinitionRepository.findByOrgKeyAndCodeAndChannel(common, code, Channel.RELEASED))
            .filter(BaseEntityDefinition::isShared);
    }

    private EntityDefinitionView toView(BaseEntityDefinition definition) {
        return new EntityDefinitionView(
            definition.getCode(),
            definition.isEmbedded(),
            definition.getAttributes().stream().map(this::toView).toList(),
            definition.getAttributes().stream()
                .filter(BaseEntityAttribute::isLinkToDetails)
                .map(BaseEntityAttribute::getCode)
                .findFirst()
                .orElse(null),
            definition.getName(),
            definition.getIdentifiers()
        );
    }

    private EntityAttributeView toView(BaseEntityAttribute attribute) {
        return new EntityAttributeView(
            attribute.getCode(),
            ValueKindView.valueOf(attribute.getValueKind().name()),
            attribute.isMultiValued(),
            attribute.getFormControlType() == FormControlType.EMBEDDED_COMPONENTS,
            attribute.getLinkedEntityType(),
            attribute.isRequired(),
            attribute.getFormControlType() == FormControlType.COMMENTS,
            attribute.getFormControlType() == FormControlType.ADDRESS,
            attribute.getFormControlType() == FormControlType.GEO_LOCATION
        );
    }
}
