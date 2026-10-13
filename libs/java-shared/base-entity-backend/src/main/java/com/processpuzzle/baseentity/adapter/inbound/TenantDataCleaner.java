package com.processpuzzle.baseentity.adapter.inbound;

import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository;
import com.processpuzzle.baseentity.definition.domain.EntityDefinitionRepository.AttributeCodes;
import com.processpuzzle.baseentity.definition.domain.FormControlType;
import com.processpuzzle.baseentity.i18n.EntityTranslationRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository;
import com.processpuzzle.baseentity.instances.domain.EntityObjectRepository.PayloadView;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import com.processpuzzle.shared.event.StoredObjectsReleasedEvent;
import com.processpuzzle.shared.event.StoredObjectsReleasedEvent.StoredObjectRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes this feature's rows of an erased tenant — its definitions and their attributes, its objects,
 * its translations — and releases the files its objects' ARTIFACT values referred to.
 *
 * <p><b>{@code BEFORE_COMMIT}</b>, so the deletes join the erasing transaction and a tenant is either
 * erased in every feature or in none. An {@code AFTER_COMMIT} listener's writes would be discarded
 * without an error.
 *
 * <p><b>Bulk statements, child table first.</b> A JPQL {@code delete} is one statement, but it does
 * not cascade, so {@code base_entity_attribute} goes before the definitions it belongs to. Objects
 * name their definition by code, not by foreign key, so their order does not matter.
 *
 * <p><b>Files: collected before, deleted after commit — by the store.</b> An ARTIFACT value is a
 * {@code {bucket, objectId, name, mimeType}} map, or a list of them, in the object's payload; once the
 * rows are gone nothing knows which files they were. So the references are read first, and handed on
 * in a {@link StoredObjectsReleasedEvent}, published in this transaction; processpuzzle-store deletes
 * the files after commit, so a rolled-back erasure releases nothing, and this feature does not depend
 * on the store. Only objects of definitions that have an ARTIFACT attribute are read, and only their
 * payloads. A value that is not such a map is skipped, not an error: it refers to no file this could
 * release, and failing would keep the tenant from being erased at all.
 *
 * <p>Named, because other features declare a {@code TenantDataCleaner} too, and an application that
 * assembles several of them would otherwise have two beans of one name.
 */
@Component("entityTenantDataCleaner")
public class TenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDataCleaner.class);

    private final EntityDefinitionRepository definitions;
    private final EntityObjectRepository objects;
    private final EntityTranslationRepository translations;
    private final ApplicationEventPublisher eventPublisher;

    public TenantDataCleaner(EntityDefinitionRepository definitions, EntityObjectRepository objects,
                             EntityTranslationRepository translations, ApplicationEventPublisher eventPublisher) {
        this.definitions = definitions;
        this.objects = objects;
        this.translations = translations;
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        String orgKey = event.orgKey();
        List<StoredObjectRef> releasedFiles = referencedFiles(orgKey);

        definitions.eraseAttributesByOrgKey(orgKey);
        int erasedDefinitions = definitions.eraseByOrgKey(orgKey);
        int erasedObjects = objects.eraseByOrgKey(orgKey);
        int erasedTranslations = translations.eraseByOrgKey(orgKey);

        if (!releasedFiles.isEmpty()) {
            eventPublisher.publishEvent(new StoredObjectsReleasedEvent(orgKey, releasedFiles));
        }
        LOG.info("Organization '{}' erased: {} entity definition(s), {} object(s) and {} translation bundle(s) "
                + "deleted; {} stored file(s) released.", orgKey, erasedDefinitions, erasedObjects,
                erasedTranslations, releasedFiles.size());
    }

    /** Every file the tenant's objects refer to through an ARTIFACT or AVATAR attribute, each once. */
    private List<StoredObjectRef> referencedFiles(String orgKey) {
        Map<String, Set<String>> artifactAttributesByDefinition = definitions
                .findAttributeCodesByOrgKeyAndFormControlTypes(orgKey, Set.of(FormControlType.ARTIFACT, FormControlType.AVATAR)).stream()
                .collect(Collectors.groupingBy(AttributeCodes::getDefinitionCode,
                        Collectors.mapping(AttributeCodes::getAttributeCode, Collectors.toSet())));
        if (artifactAttributesByDefinition.isEmpty()) {
            return List.of();
        }

        Set<StoredObjectRef> files = new LinkedHashSet<>();
        for (PayloadView object : objects.findPayloadsByOrgKeyAndEntityDefinitionCodeIn(orgKey,
                artifactAttributesByDefinition.keySet())) {
            Map<String, Object> payload = object.getPayload();
            if (payload == null) {
                continue;
            }
            for (String attributeCode : artifactAttributesByDefinition.get(object.getEntityDefinitionCode())) {
                collectFiles(payload.get(attributeCode), files);
            }
        }
        return new ArrayList<>(files);
    }

    /** A single ARTIFACT value or a list of them (multiplicity {@code 0..n}); anything else adds nothing. */
    private static void collectFiles(Object value, Set<StoredObjectRef> files) {
        if (value instanceof Collection<?> values) {
            values.forEach(element -> collectFile(element, files));
        } else {
            collectFile(value, files);
        }
    }

    private static void collectFile(Object value, Set<StoredObjectRef> files) {
        if (value instanceof Map<?, ?> artifact
                && artifact.get("bucket") instanceof String bucket && !bucket.isBlank()
                && artifact.get("objectId") instanceof String objectId && !objectId.isBlank()) {
            files.add(new StoredObjectRef(bucket, objectId));
        }
    }
}
