package com.processpuzzle.ai.usecase;

import com.processpuzzle.ai.domain.EnrollmentPhotoRepository;
import com.processpuzzle.ai.domain.MatchingSettings;
import com.processpuzzle.ai.domain.RecognitionProfile;
import com.processpuzzle.ai.domain.RecognitionProfileRepository;
import com.processpuzzle.ai.usecase.exception.AiRequestException;
import com.processpuzzle.ai.usecase.port.SubjectDirectory;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The recognition-profile use cases. Plain CRUD over one row with one validation routine, gathered in
 * one service for the reason base-widget gives for its {@code WidgetDefinitionCrud}.
 */
@Service
public class RecognitionProfiles {

    private static final String INVALID_PROFILE = "ai.profile.invalid";

    private final RecognitionProfileRepository profiles;
    private final EnrollmentPhotoRepository photos;
    private final SubjectDirectory subjects;
    private final OrganizationGuard guard;

    public RecognitionProfiles(RecognitionProfileRepository profiles, EnrollmentPhotoRepository photos,
                               ObjectProvider<SubjectDirectory> subjects, OrganizationGuard guard) {
        this.profiles = profiles;
        this.photos = photos;
        this.subjects = subjects.getIfUnique(() -> SubjectDirectory.PERMISSIVE);
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public Page<RecognitionProfile> findAll(String orgKey, Pageable pageable) {
        guard.requireAccess(orgKey);
        return profiles.findByOrgKey(orgKey, pageable);
    }

    @Transactional(readOnly = true)
    public RecognitionProfile find(String orgKey, String entityName) {
        guard.requireAccess(orgKey);
        return require(orgKey, entityName);
    }

    @Transactional
    public RecognitionProfile create(String orgKey, Draft draft) {
        guard.requireDesign(orgKey);
        validate(orgKey, draft.entityName(), draft);
        if (profiles.existsByOrgKeyAndEntityName(orgKey, draft.entityName())) {
            throw AiRequestException.conflict("ai.profile.already-exists",
                    "Entity type '" + draft.entityName() + "' already has a recognition profile.");
        }
        RecognitionProfile profile = new RecognitionProfile(orgKey, draft.entityName());
        apply(profile, draft);
        return profiles.save(profile);
    }

    /** Full replacement; the path's entityName is the source of truth, as the contract says. */
    @Transactional
    public RecognitionProfile update(String orgKey, String entityName, Draft draft) {
        guard.requireDesign(orgKey);
        RecognitionProfile profile = require(orgKey, entityName);
        validate(orgKey, entityName, draft);
        apply(profile, draft);
        return profiles.save(profile);
    }

    @Transactional
    public void delete(String orgKey, String entityName) {
        guard.requireDesign(orgKey);
        RecognitionProfile profile = require(orgKey, entityName);
        if (photos.existsByOrgKeyAndEntityName(orgKey, entityName)) {
            throw AiRequestException.conflict("ai.profile.has-enrollments",
                    "Subjects of '" + entityName + "' are still enrolled; delete their enrollments first.");
        }
        profiles.delete(profile);
    }

    /**
     * The profile, for another use case of this module; no access check of its own. Public, not
     * package-private: this bean is proxied for its transactions, and a method the proxy does not
     * override would run against the proxy's own empty fields.
     */
    public RecognitionProfile require(String orgKey, String entityName) {
        return profiles.findByOrgKeyAndEntityName(orgKey, entityName)
                .orElseThrow(() -> AiRequestException.notFound("ai.profile.not-found",
                        "Entity type '" + entityName + "' has no recognition profile."));
    }

    private void apply(RecognitionProfile profile, Draft draft) {
        profile.setName(draft.name());
        profile.setDescription(draft.description());
        profile.setDetectorClass(draft.detectorClass());
        profile.setIdentifierAttributeKey(blankToNull(draft.identifierAttributeKey()));
        profile.setIdentifierPattern(blankToNull(draft.identifierPattern()));
        profile.setMatching(draft.matching() == null ? MatchingSettings.defaults() : draft.matching());
    }

    private void validate(String orgKey, String entityName, Draft draft) {
        if (entityName == null || entityName.isBlank()) {
            throw AiRequestException.invalid(INVALID_PROFILE, "entityName is required.");
        }
        if (draft.name() == null || draft.name().isBlank()) {
            throw AiRequestException.invalid(INVALID_PROFILE, "name is required.");
        }
        if (draft.detectorClass() == null || draft.detectorClass().isBlank()) {
            throw AiRequestException.invalid(INVALID_PROFILE, "detectorClass is required.");
        }
        if (!subjects.entityTypeExists(orgKey, entityName)) {
            throw AiRequestException.notFound("ai.profile.entity-not-found",
                    "'" + entityName + "' is not an entity type of this organization.");
        }
        String attribute = blankToNull(draft.identifierAttributeKey());
        if (attribute != null && !subjects.isTextAttribute(orgKey, entityName, attribute)) {
            throw AiRequestException.invalid("ai.profile.identifier-attribute-invalid",
                    "'" + attribute + "' is not a text attribute of '" + entityName + "'.");
        }
        String pattern = blankToNull(draft.identifierPattern());
        if (pattern != null) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw AiRequestException.invalid("ai.profile.identifier-pattern-invalid",
                        "identifierPattern is not a valid regular expression: " + e.getDescription());
            }
        }
        validateMatching(draft.matching());
    }

    private static void validateMatching(MatchingSettings matching) {
        if (matching != null) {
            requireFraction("identifierWeight", matching.getIdentifierWeight());
            requireFraction("acceptScore", matching.getAcceptScore());
            requireFraction("acceptMargin", matching.getAcceptMargin());
            if (matching.getSampleFps() < 0.5 || matching.getSampleFps() > 30) {
                throw AiRequestException.invalid(INVALID_PROFILE, "sampleFps must be between 0.5 and 30.");
            }
        }
    }

    private static void requireFraction(String name, double value) {
        if (value < 0 || value > 1) {
            throw AiRequestException.invalid(INVALID_PROFILE, name + " must be between 0 and 1.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** The writable half of a profile, as the use cases receive it. */
    public record Draft(
            String entityName,
            String name,
            String description,
            String detectorClass,
            String identifierAttributeKey,
            String identifierPattern,
            MatchingSettings matching) {
    }
}
