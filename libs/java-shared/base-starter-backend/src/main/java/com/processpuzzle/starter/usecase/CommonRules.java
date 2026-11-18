package com.processpuzzle.starter.usecase;

import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.bundle.CommonKeys;
import com.processpuzzle.starter.bundle.StarterManifest;
import com.processpuzzle.starter.domain.DataSpace;
import com.processpuzzle.starter.domain.DataSpaceRepository;
import com.processpuzzle.starter.domain.InstalledStarter;
import com.processpuzzle.starter.domain.InstalledStarterRepository;
import com.processpuzzle.starter.registry.StarterVersions;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Where a bundle may be installed, as far as commons are concerned. A <em>common</em> bundle goes into the
 * organization named by its own id, and nowhere else; a tenant bundle never goes into a common, and one that names a
 * common ({@code common:} block) needs that common to exist, at {@code minVersion} or newer. The first install of a
 * common bundle is what creates the common: it is registered as the data space of its starter.
 * See {@code docs/shared-data-commons-concept} §5.1 and §5.5.
 */
@Component
public class CommonRules {

    private final DataSpaceRepository dataSpaces;
    private final InstalledStarterRepository installed;
    private final RegisterDataSpace registerDataSpace;

    public CommonRules(DataSpaceRepository dataSpaces, InstalledStarterRepository installed, RegisterDataSpace registerDataSpace) {
        this.dataSpaces = dataSpaces;
        this.installed = installed;
        this.registerDataSpace = registerDataSpace;
    }

    /** What stops {@code manifest} from being installed into {@code orgKey}; empty when nothing does. */
    public List<ImportReport.Error> check(String orgKey, StarterManifest manifest) {
        if (manifest.isCommon()) {
            return orgKey.equals(manifest.id()) ? List.of() : List.of(error(
                    "The common bundle '" + manifest.id() + "' installs only into the organization '" + manifest.id() + "'."));
        }
        if (dataSpaces.existsById(orgKey)) {
            return List.of(error("'" + orgKey + "' is a common; a starter is not installed into it."));
        }
        return commonProblems(manifest);
    }

    /** What stops a tenant bundle that names a common from being used now: the common is missing or older than it needs. */
    public List<ImportReport.Error> commonProblems(StarterManifest manifest) {
        StarterManifest.Common needed = manifest.common();
        if (needed == null) {
            return List.of();
        }
        Optional<InstalledStarter> common = dataSpaces.findByStarterId(manifest.id())
                .map(DataSpace::getCommonKey)
                .flatMap(key -> installed.findByOrgKeyAndStarterId(key, needed.id()));
        if (common.isEmpty()) {
            return List.of(error("The common '" + needed.id() + "' of starter '" + manifest.id() + "' does not exist yet; "
                    + "it is created by rolling out its first version."));
        }
        String version = common.get().getVersion();
        if (StarterVersions.compare(version, needed.minVersion()) < 0) {
            return List.of(error("Starter '" + manifest.id() + "' " + manifest.version() + " needs '" + needed.id() + "' "
                    + needed.minVersion() + " or newer; the common is at " + version + "."));
        }
        return List.of();
    }

    /** Called once a common bundle has been installed for real: its organization becomes its starter's common. */
    public void commonInstalled(StarterManifest manifest) {
        if (manifest.isCommon()) {
            registerDataSpace.register(CommonKeys.starterOf(manifest.id()), manifest.id());
        }
    }

    private static ImportReport.Error error(String message) {
        return new ImportReport.Error(BundleReader.MANIFEST, message);
    }
}
