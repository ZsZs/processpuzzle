package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppAssetRepository;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The assets of an app in one channel, without their content. */
@Service
@Transactional(readOnly = true)
public class ListAppAssets {

    private final AppDefinitionRepository apps;
    private final AppAssetRepository assets;
    private final OrganizationGuard guard;

    public ListAppAssets(AppDefinitionRepository apps, AppAssetRepository assets, OrganizationGuard guard) {
        this.apps = apps;
        this.assets = assets;
        this.guard = guard;
    }

    public List<ModuleAssetSummary> execute(String orgKey, String appId, Channel channel) {
        FindAllModuleDefinitions.requireReader(guard, orgKey, channel);
        if (!apps.existsByOrgKeyAndId(orgKey, appId)) {
            throw new AppDefinitionNotFoundException(orgKey, appId);
        }
        return assets.findByOrgKeyAndAppIdAndChannelOrderByPath(orgKey, appId, channel).stream()
                .map(asset -> new ModuleAssetSummary(asset.getPath(), asset.getMimeType(), asset.getSize(), asset.getSha256()))
                .toList();
    }
}
