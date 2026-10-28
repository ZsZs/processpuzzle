package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.ModuleMount;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.service.AppAssetReleases;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.definition.CanonicalJson;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseCandidate;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Releases applications: publishes each app whose draft graph differs from its published one.
 *
 * <p>An app keeps its draft and published graphs side by side on one row (see {@link AppDefinition}),
 * so it has no channel of its own; releasing it is {@link AppDefinition#publish()}. The draft must pass
 * the same validation a save applies, and only its errors refuse the release, as they refuse a save.
 * Deleting an app deletes both graphs, so there is never a removal to release.
 *
 * <p>The app's assets are released with it and are part of what is pending: an app whose graph is unchanged but
 * whose files are not still has something to release. Only the records are copied, never the files.
 */
@Component
public class AppReleaseParticipant implements ReleaseParticipant {

    private final AppDefinitionRepository repository;
    private final AppDefinitionValidator validator;
    private final AppMapper mapper;
    private final AppAssetReleases assets;
    private final ModuleAssetSweeper sweeper;

    public AppReleaseParticipant(AppDefinitionRepository repository, AppDefinitionValidator validator, AppMapper mapper,
                                 AppAssetReleases assets, ModuleAssetSweeper sweeper) {
        this.repository = repository;
        this.validator = validator;
        this.mapper = mapper;
        this.assets = assets;
        this.sweeper = sweeper;
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
    public Set<String> drafts(String orgKey) {
        return apps(orgKey).stream().map(AppDefinition::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public List<PendingChange> pending(String orgKey) {
        Map<String, Map<String, String>> draftAssets = assets.byApp(orgKey, Channel.DRAFT);
        Map<String, Map<String, String>> releasedAssets = assets.byApp(orgKey, Channel.RELEASED);
        return apps(orgKey).stream()
                .map(app -> PendingChange.between(kind(), app.getId(),
                        fingerprint(app.getDraftGraph(), draftAssets.get(app.getId())),
                        fingerprint(app.getPublishedGraph(), releasedAssets.get(app.getId()))))
                .filter(Objects::nonNull)
                .toList();
    }

    /** The draft's routes, and its module mounts — optional, since a mount of a missing module only shows nothing. */
    @Override
    public List<DefinitionReference> references(String orgKey) {
        List<DefinitionReference> references = new ArrayList<>();
        for (AppDefinition app : apps(orgKey)) {
            AppGraph draft = app.getDraftGraph();
            references.addAll(RouteReferences.of(kind(), app.getId(), draft.routes()));
            draft.modules().stream()
                    .map(ModuleMount::moduleKey)
                    .filter(key -> key != null && !key.isBlank())
                    .distinct()
                    .forEach(key -> references.add(DefinitionReference.optional(kind(), app.getId(), DefinitionKinds.MODULE, key)));
        }
        return references;
    }

    /** Only a changed app is validated: an unchanged one is live already, and refusing it now would block every release. */
    @Override
    public List<ReleaseProblem> check(String orgKey, ReleaseCandidate candidate) {
        List<ReleaseProblem> problems = new ArrayList<>();
        for (AppDefinition app : changedApps(orgKey)) {
            for (AppValidationProblem problem : AppValidationProblem.blocking(validator.validateStored(orgKey, mapper.toModel(app)))) {
                problems.add(ReleaseProblem.error(kind(), app.getId(), problem.errorId(),
                        problem.errorText() + (problem.path() == null ? "" : " (" + problem.path() + ")")));
            }
        }
        return problems;
    }

    @Override
    public void release(String orgKey) {
        Set<String> dropped = new TreeSet<>();
        for (AppDefinition app : changedApps(orgKey)) {
            app.publish();
            assets.release(orgKey, app.getId(), dropped);
        }
        sweeper.sweep(orgKey, dropped);
    }

    private List<AppDefinition> changedApps(String orgKey) {
        Map<String, Map<String, String>> draftAssets = assets.byApp(orgKey, Channel.DRAFT);
        Map<String, Map<String, String>> releasedAssets = assets.byApp(orgKey, Channel.RELEASED);
        return apps(orgKey).stream()
                .filter(app -> !Objects.equals(fingerprint(app.getDraftGraph(), draftAssets.get(app.getId())),
                        fingerprint(app.getPublishedGraph(), releasedAssets.get(app.getId()))))
                .toList();
    }

    private List<AppDefinition> apps(String orgKey) {
        return repository.findByOrgKey(orgKey).stream().sorted(Comparator.comparing(AppDefinition::getId)).toList();
    }

    /** The graph alone when the app has no assets, so that an app that never had any keeps the fingerprint it had before they existed. */
    static String fingerprint(AppGraph graph, Map<String, String> assetHashes) {
        if (graph == null) {
            return null;
        }
        if (assetHashes == null || assetHashes.isEmpty()) {
            return CanonicalJson.of(graph, Set.of());
        }
        Map<String, Object> content = new TreeMap<>();
        content.put("graph", graph);
        content.put("assets", assetHashes);
        return CanonicalJson.of(content, Set.of());
    }
}
