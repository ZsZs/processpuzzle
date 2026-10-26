package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The assets of a module in one channel, without their content. */
@Service
@Transactional(readOnly = true)
public class ListModuleAssets {

    private final ModuleDefinitionRepository modules;
    private final ModuleAssetRepository assets;
    private final OrganizationGuard guard;

    public ListModuleAssets(ModuleDefinitionRepository modules, ModuleAssetRepository assets, OrganizationGuard guard) {
        this.modules = modules;
        this.assets = assets;
        this.guard = guard;
    }

    public List<ModuleAssetSummary> execute(String orgKey, String moduleKey, Channel channel) {
        FindAllModuleDefinitions.requireReader(guard, orgKey, channel);
        if (!modules.existsByOrgKeyAndKeyAndChannel(orgKey, moduleKey, channel)) {
            throw new ModuleDefinitionNotFoundException(orgKey, moduleKey);
        }
        return assets.findByOrgKeyAndModuleKeyAndChannelOrderByPath(orgKey, moduleKey, channel).stream()
                .map(asset -> new ModuleAssetSummary(asset.getPath(), asset.getMimeType(), asset.getSize(), asset.getSha256()))
                .toList();
    }
}
