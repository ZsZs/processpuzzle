package com.processpuzzle.app.adapter.inbound;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.app.adapter.inbound.dto.AppYamlDocument;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.DeleteAppDefinition;
import com.processpuzzle.app.usecase.ImportAppDefinitions;
import com.processpuzzle.app.usecase.ImportOutcome;
import com.processpuzzle.app.usecase.PublishAppDefinition;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Imports a Business Starter's app files, whose root is {@code appDefinitions:} as in the bundled default-apps
 * files, through {@link ImportAppDefinitions}, and publishes them through {@link PublishAppDefinition}: a starter's
 * application is what the runtime host serves the moment it is installed, not a draft waiting for a designer.
 * The key is the app id.
 *
 * <p>An organization has one application, and sign-up creates it empty, as
 * {@link StarterAppCreator#STARTER_APP_ID}. So the importer never leaves that placeholder next to the starter's app:
 * <ul>
 *   <li>{@link #removeAll} deletes every app and puts a fresh empty placeholder back, so an organization whose
 *       next starter carries no app still has one to design;</li>
 *   <li>a starter whose app has the placeholder's id replaces it in place;</li>
 *   <li>a starter whose app has another id removes the placeholder, as long as it is still untouched.</li>
 * </ul>
 *
 * <p>Modules the app mounts come from the same bundle, imported and released first ({@link ModuleImportParticipant}),
 * so the published layout carries them. Apps hold no instance data, so there is nothing to probe.
 */
@Component
public class AppImportParticipant implements DefinitionImportParticipant {

    private final ImportAppDefinitions importApps;
    private final PublishAppDefinition publishApp;
    private final DeleteAppDefinition deleteApp;
    private final AppDefinitionRepository repository;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public AppImportParticipant(ImportAppDefinitions importApps, PublishAppDefinition publishApp,
                                DeleteAppDefinition deleteApp, AppDefinitionRepository repository) {
        this.importApps = importApps;
        this.publishApp = publishApp;
        this.deleteApp = deleteApp;
        this.repository = repository;
    }

    @Override
    public String kind() {
        return DefinitionKinds.APP;
    }

    @Override
    public int order() {
        return DefinitionKinds.APP_ORDER;
    }

    @Override
    public ParticipantResult apply(String orgKey, String fileName, byte[] yaml) {
        List<String> ids;
        try {
            ids = ids(yaml);
        } catch (IOException e) {
            return ParticipantResult.rejected(List.of(fileName + ": not a readable app file: " + e.getMessage()));
        }
        List<String> existing = ids.stream().filter(id -> repository.existsByOrgKeyAndId(orgKey, id)).toList();
        try {
            ImportOutcome outcome = importApps.execute(orgKey, new ByteArrayInputStream(yaml));
            if (!outcome.errors().isEmpty()) {
                return ParticipantResult.rejected(outcome.errors().stream().map(error -> fileName + ": " + error).toList());
            }
            ids.forEach(id -> publishApp.execute(orgKey, id));
        } catch (AppDefinitionInvalidException e) {
            List<String> errors = new ArrayList<>();
            for (AppValidationProblem problem : e.getProblems()) {
                errors.add(fileName + ": " + problem.path() + ": " + problem.errorText());
            }
            return ParticipantResult.rejected(errors.isEmpty() ? List.of(fileName + ": " + e.getMessage()) : errors);
        } catch (IOException | RuntimeException e) {
            return ParticipantResult.rejected(List.of(fileName + ": " + e.getMessage()));
        }
        if (!ids.isEmpty() && !ids.contains(StarterAppCreator.STARTER_APP_ID)) {
            untouchedPlaceholder(orgKey).ifPresent(placeholder -> deleteApp.execute(orgKey, placeholder.getId()));
        }

        List<ImportedItem> items = ids.stream()
                .map(id -> new ImportedItem(id, existing.contains(id) ? ImportedItem.Action.UPDATE : ImportedItem.Action.CREATE))
                .toList();
        return new ParticipantResult(items, List.of());
    }

    /**
     * Deletes every app, then saves the empty placeholder again, so that the organization is never left without
     * one. An untouched placeholder is not reported: it is back as it was. Flushed before the save, because
     * Hibernate runs inserts before deletes and the placeholder reuses a key.
     */
    @Override
    public List<String> removeAll(String orgKey) {
        List<AppDefinition> apps = repository.findByOrgKey(orgKey);
        if (apps.isEmpty()) {
            return List.of();
        }
        String placeholderName = apps.stream()
                .filter(app -> StarterAppCreator.STARTER_APP_ID.equals(app.getId()))
                .map(AppDefinition::getName)
                .findFirst()
                .orElse(orgKey);
        List<String> removed = new ArrayList<>();
        for (AppDefinition app : apps) {
            if (!isUntouchedPlaceholder(app)) {
                removed.add(app.getId());
            }
            deleteApp.execute(orgKey, app.getId());
        }
        repository.flush();
        repository.save(new AppDefinition(orgKey, StarterAppCreator.STARTER_APP_ID, placeholderName, null, null, AppGraph.empty()));
        return removed;
    }

    @Override
    public Map<String, String> fingerprints(String orgKey, Set<String> keys) {
        Map<String, String> fingerprints = new LinkedHashMap<>();
        for (AppDefinition app : repository.findByOrgKey(orgKey)) {
            if (keys.contains(app.getId())) {
                fingerprints.put(app.getId(), fingerprint(app));
            }
        }
        return fingerprints;
    }

    /** The authored content only: neither the revisions nor the timestamps every save moves. */
    private static String fingerprint(AppDefinition app) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", app.getName());
        content.put("translocoId", app.getTranslocoId());
        content.put("description", app.getDescription());
        content.put("graph", app.getDraftGraph());
        return CanonicalJson.of(content, Set.of());
    }

    private Optional<AppDefinition> untouchedPlaceholder(String orgKey) {
        return repository.findByOrgKeyAndId(orgKey, StarterAppCreator.STARTER_APP_ID)
                .filter(AppImportParticipant::isUntouchedPlaceholder);
    }

    /** What sign-up created and nobody has edited, saved or published since: the placeholder's id, an empty graph, the first revision. */
    private static boolean isUntouchedPlaceholder(AppDefinition app) {
        return StarterAppCreator.STARTER_APP_ID.equals(app.getId())
                && !app.hasPublishedRevision()
                && app.getRevision() != null && app.getRevision() == 1L
                && AppGraph.empty().equals(app.getDraftGraph());
    }

    private List<String> ids(byte[] yaml) throws IOException {
        AppYamlDocument document = yamlMapper.readValue(yaml, AppYamlDocument.class);
        return document == null ? List.of() : document.appDefinitions().stream()
                .filter(Objects::nonNull)
                .map(AppDefinitionInput::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
