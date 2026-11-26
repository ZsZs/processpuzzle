package com.processpuzzle.baseentity.instances.adapters.outbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.baseentity.definition.domain.SharedTypes;
import com.processpuzzle.baseentity.definition.domain.BaseEntityAttribute;
import com.processpuzzle.baseentity.definition.domain.BaseEntityDefinition;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionLookupPort.SnapshotView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView;
import com.processpuzzle.core.quantity.UnitCatalog;
import com.processpuzzle.core.quantity.UnitCatalogProvider;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final UnitCatalogProvider catalogs;

    /** For an application, or a test, that has only the standard units. */
    public EntityDefinitionLookupAdapter(EntityDefinitionRepository entityDefinitionRepository,
                                         ObjectProvider<DataSpaceDirectory> dataSpaces) {
        this(entityDefinitionRepository, dataSpaces, UnitCatalogProvider.standardOnly());
    }

    @Autowired
    public EntityDefinitionLookupAdapter(EntityDefinitionRepository entityDefinitionRepository,
                                         ObjectProvider<DataSpaceDirectory> dataSpaces, UnitCatalogProvider catalogs) {
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.dataSpaces = dataSpaces;
        this.catalogs = catalogs;
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
    public List<EntityDefinitionView> findAll(String orgKey, Channel channel) {
        return entityDefinitionRepository.findAllByOrgKeyAndChannel(orgKey, channel).stream().map(this::toView).toList();
    }

    @Override
    public Optional<EntityDefinitionView> findByRuleContext(String orgKey, String ruleContext, Channel channel) {
        return entityDefinitionRepository.findAllByOrgKeyAndChannel(orgKey, channel).stream()
            .map(this::toView)
            .filter(view -> view.ruleContext().equals(ruleContext))
            .findFirst();
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

    @Override
    public Map<String, SnapshotView> snapshotsOf(String orgKey, String code) {
        Optional<BaseEntityDefinition> own = entityDefinitionRepository.findByOrgKeyAndCodeAndChannel(orgKey, code, Channel.RELEASED);
        Optional<BaseEntityDefinition> definition = own.isPresent() ? own : sharedDefinition(orgKey, code);
        Map<String, SnapshotView> snapshots = new LinkedHashMap<>();
        definition.ifPresent(type -> type.getAttributes().stream()
            .filter(attribute -> attribute.getSnapshotFields() != null && !attribute.getSnapshotFields().isEmpty())
            .forEach(attribute -> snapshots.put(attribute.getCode(),
                new SnapshotView(attribute.getLinkedEntityType(), attribute.getSnapshotFields()))));
        return snapshots;
    }

    private Optional<BaseEntityDefinition> sharedDefinition(String orgKey, String code) {
        DataSpaceDirectory directory = dataSpaces.getIfAvailable();
        if (directory == null) {
            return Optional.empty();
        }
        return SharedTypes.sharedIn(entityDefinitionRepository, directory.commonsOf(orgKey), code);
    }

    private EntityDefinitionView toView(BaseEntityDefinition definition) {
        // The catalog of the organization that owns the definition: the units its attributes name are that organization's, also
        // when the definition is a common's, shared with a member.
        UnitCatalog catalog = catalogs.forOrganization(definition.getOrgKey());
        return new EntityDefinitionView(
            definition.getCode(),
            definition.isEmbedded(),
            definition.getAttributes().stream().map(attribute -> toView(attribute, catalog)).toList(),
            definition.getAttributes().stream()
                .filter(BaseEntityAttribute::isLinkToDetails)
                .map(BaseEntityAttribute::getCode)
                .findFirst()
                .orElse(null),
            definition.getName(),
            definition.getIdentifiers()
        );
    }

    private EntityAttributeView toView(BaseEntityAttribute attribute, UnitCatalog catalog) {
        return new EntityAttributeView(
            attribute.getCode(),
            ValueKindView.valueOf(attribute.getValueKind().name()),
            attribute.isMultiValued(),
            attribute.getFormControlType() == FormControlType.EMBEDDED_COMPONENTS,
            attribute.getLinkedEntityType(),
            attribute.isRequired(),
            attribute.getFormControlType() == FormControlType.COMMENTS,
            attribute.getFormControlType() == FormControlType.ADDRESS,
            attribute.getFormControlType() == FormControlType.GEO_LOCATION,
            measureOf(attribute, catalog)
        );
    }

    /** What an instance write needs of a QUANTITY, MONEY or DIMENSIONS attribute; null for any other control. */
    private MeasureView measureOf(BaseEntityAttribute attribute, UnitCatalog catalog) {
        MeasureView.Kind kind = switch (attribute.getFormControlType()) {
            case QUANTITY -> MeasureView.Kind.QUANTITY;
            case MONEY -> MeasureView.Kind.MONEY;
            case DIMENSIONS -> MeasureView.Kind.DIMENSIONS;
            default -> null;
        };
        return kind == null ? null : new MeasureView(kind, attribute.getQuantityKind(), attribute.getAllowedUnits(), attribute.getScale(),
            attribute.getAllowedCurrencies(), attribute.getDimensionCount(), attribute.getDefaultUnit(), attribute.getDefaultCurrency(), catalog);
    }
}
