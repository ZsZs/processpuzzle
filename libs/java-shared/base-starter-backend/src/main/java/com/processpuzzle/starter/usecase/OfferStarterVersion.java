package com.processpuzzle.starter.usecase;

import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.bundle.BundleRejectedException;
import com.processpuzzle.starter.bundle.StarterBundle;
import com.processpuzzle.starter.domain.OfferedVersion;
import com.processpuzzle.starter.domain.OfferedVersionRepository;
import com.processpuzzle.starter.registry.StarterVersions;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rolls out a version of a starter's customisable part: it becomes the <em>offered</em> version, which new sign-ups
 * install and which the organizations running an older one are told about. Server-side only — the platform's rollout
 * decides, and the application hosting this library exposes it to that rollout and to nobody else.
 *
 * <p>A version that names a common ({@code common.minVersion}) can be offered only once that common is at least
 * that version. A rollout never offers an older version than the one offered; withdrawing it does not undo what
 * organizations already applied.
 */
@Service
public class OfferStarterVersion {

    private final BrowseCatalog catalog;
    private final InstallStarter installer;
    private final BundleReader reader;
    private final CommonRules commonRules;
    private final OfferedVersionRepository offered;
    private final Clock clock;

    public OfferStarterVersion(BrowseCatalog catalog, InstallStarter installer, BundleReader reader, CommonRules commonRules,
                               OfferedVersionRepository offered, ObjectProvider<Clock> clock) {
        this.catalog = catalog;
        this.installer = installer;
        this.reader = reader;
        this.commonRules = commonRules;
        this.offered = offered;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    /**
     * @throws StarterNotFoundException if the catalog has no installable such version
     * @throws IllegalStateException    if the version is older than the offered one, or its common is not there yet
     */
    @Transactional
    public OfferedVersion offer(String starterId, String version, String by) {
        catalog.version(starterId, version);
        Optional<OfferedVersion> current = offered.findById(starterId);
        if (current.isPresent() && StarterVersions.compare(version, current.get().getVersion()) <= 0) {
            throw new IllegalStateException("Version " + current.get().getVersion() + " of '" + starterId + "' is offered already; "
                + "a rollout offers a newer one, not " + version + ".");
        }
        List<ImportReport.Error> problems = commonProblems(starterId, version);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(problems.getFirst().message());
        }
        OfferedVersion row = current.orElseGet(() -> new OfferedVersion(starterId));
        row.offer(version, clock.instant(), by);
        return offered.save(row);
    }

    /** Stops offering the starter's version; the organizations that applied it keep it. */
    @Transactional
    public void withdraw(String starterId) {
        offered.deleteById(starterId);
    }

    @Transactional(readOnly = true)
    public Optional<String> offeredVersion(String starterId) {
        return offered.findById(starterId).map(OfferedVersion::getVersion);
    }

    private List<ImportReport.Error> commonProblems(String starterId, String version) {
        try {
            StarterBundle bundle = reader.read(new ByteArrayInputStream(installer.download(starterId, version, false)));
            return commonRules.commonProblems(bundle.manifest());
        } catch (BundleRejectedException e) {
            return List.of(new ImportReport.Error(e.getFile(), e.getMessage()));
        }
    }
}
