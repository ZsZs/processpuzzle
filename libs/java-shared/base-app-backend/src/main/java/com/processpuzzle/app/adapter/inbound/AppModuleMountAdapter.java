package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.usecase.service.ModuleMountMerger;
import com.processpuzzle.app.usecase.service.ModuleMountMerger.ModuleInfo;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.ModuleMountPort;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Mounts the modules of an installed domain starter into the organization's application (business-starters design
 * §11.3 and decision 11a), for {@code base-starter-backend}, which reaches it only through {@link ModuleMountPort}.
 *
 * <p>The mounts are written to the application's <em>draft</em>, in the install's own transaction. The application is
 * released as well only when it is still the untouched default shell — nobody has saved it since it was seeded — because
 * then there is no draft of the customer's that the release would carry along; every other application keeps the mounts as
 * draft changes for its owner to release. The modules themselves are released as they are imported, as before.
 *
 * <p>The organization's application is {@link StarterAppCreator#STARTER_APP_ID}, or the only one it has. An organization
 * with neither gets no mounts, which is reported and not an error.
 */
@Component
public class AppModuleMountAdapter implements ModuleMountPort {

    private final AppDefinitionRepository apps;
    private final ModuleDefinitionRepository modules;

    public AppModuleMountAdapter(AppDefinitionRepository apps, ModuleDefinitionRepository modules) {
        this.apps = apps;
        this.modules = modules;
    }

    @Override
    public MountOutcome mount(String orgKey, List<MountHint> hints) {
        if (hints.isEmpty()) {
            return MountOutcome.nothing();
        }
        Optional<AppDefinition> app = applicationOf(orgKey);
        if (app.isEmpty()) {
            return noApplication(hints.stream().map(MountHint::moduleKey).toList());
        }
        ModuleMountMerger.Result result = ModuleMountMerger.mount(app.get().getDraftGraph(), hints, key -> moduleInfo(orgKey, key));
        return apply(app.get(), result);
    }

    @Override
    public MountOutcome unmount(String orgKey, Collection<String> moduleKeys) {
        Optional<AppDefinition> app = applicationOf(orgKey);
        if (moduleKeys.isEmpty() || app.isEmpty()) {
            return MountOutcome.nothing();
        }
        return apply(app.get(), ModuleMountMerger.unmount(app.get().getDraftGraph(), moduleKeys));
    }

    private MountOutcome apply(AppDefinition app, ModuleMountMerger.Result result) {
        boolean release = false;
        if (!result.done().isEmpty()) {
            release = app.isUntouchedShell();
            app.mergeDraft(result.graph());
            if (release) {
                app.publish();
            }
            apps.save(app);
        }
        return new MountOutcome(result.done(), result.skipped(), release);
    }

    private Optional<AppDefinition> applicationOf(String orgKey) {
        Optional<AppDefinition> starterApp = apps.findByOrgKeyAndId(orgKey, StarterAppCreator.STARTER_APP_ID);
        if (starterApp.isPresent()) {
            return starterApp;
        }
        List<AppDefinition> all = apps.findByOrgKey(orgKey);
        return all.size() == 1 ? Optional.of(all.getFirst()) : Optional.empty();
    }

    /** The module as the draft holds it: the install has just written it there. */
    private ModuleInfo moduleInfo(String orgKey, String moduleKey) {
        return modules.findByOrgKeyAndKeyAndChannel(orgKey, moduleKey, Channel.DRAFT)
                .map(module -> ModuleMountMerger.infoOf(module.getName(), module.getRoutes()))
                .orElse(null);
    }

    private static MountOutcome noApplication(List<String> moduleKeys) {
        Map<String, String> skipped = new LinkedHashMap<>();
        moduleKeys.forEach(key -> skipped.put(key, "the organization has no application"));
        return new MountOutcome(List.of(), skipped, false);
    }
}
