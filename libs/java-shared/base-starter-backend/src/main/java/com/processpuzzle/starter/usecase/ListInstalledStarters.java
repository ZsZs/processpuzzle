package com.processpuzzle.starter.usecase;

import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.starter.domain.DefinitionProvenance;
import com.processpuzzle.starter.domain.DefinitionProvenanceRepository;
import com.processpuzzle.starter.domain.InstalledStarter;
import com.processpuzzle.starter.domain.InstalledStarterRepository;
import com.processpuzzle.starter.domain.OfferedVersion;
import com.processpuzzle.starter.domain.OfferedVersionRepository;
import com.processpuzzle.starter.registry.StarterVersions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The starters installed in an organization, each with how many of its definitions were changed since
 * they were imported.
 *
 * <p>"Changed" is decided by the owning participant's current fingerprint no longer hashing to the
 * value stored at import, so it needs no column in any feature table. A definition that was deleted
 * counts as changed too. A kind whose participant the deployment no longer hosts cannot be checked
 * and is not counted.
 */
@Service
public class ListInstalledStarters {

    private final OrganizationGuard guard;
    private final InstalledStarterRepository installedStarterRepository;
    private final DefinitionProvenanceRepository provenanceRepository;
    private final ImportParticipants participants;
    private final OfferedVersionRepository offered;

    public ListInstalledStarters(OrganizationGuard guard,
                                 InstalledStarterRepository installedStarterRepository,
                                 DefinitionProvenanceRepository provenanceRepository,
                                 ImportParticipants participants,
                                 OfferedVersionRepository offered) {
        this.offered = offered;
        this.guard = guard;
        this.installedStarterRepository = installedStarterRepository;
        this.provenanceRepository = provenanceRepository;
        this.participants = participants;
    }

    @Transactional(readOnly = true)
    public List<View> execute(String orgKey) {
        guard.requireAccess(orgKey);
        return installedStarterRepository.findByOrgKeyOrderByStarterId(orgKey).stream()
                .map(installed -> view(orgKey, installed))
                .toList();
    }

    private View view(String orgKey, InstalledStarter installed) {
        String offeredVersion = offered.findById(installed.getStarterId()).map(OfferedVersion::getVersion).orElse(null);
        boolean upgradeAvailable = offeredVersion != null && StarterVersions.compare(offeredVersion, installed.getVersion()) > 0;
        return new View(installed.getStarterId(), installed.getVersion(), installed.getInstalledAt(), installed.getInstalledBy(),
                customized(orgKey, installed), upgradeAvailable ? offeredVersion : null, upgradeAvailable);
    }

    private int customized(String orgKey, InstalledStarter installed) {
        Map<String, List<DefinitionProvenance>> byKind = provenanceRepository
                .findByOrgKeyAndStarterId(orgKey, installed.getStarterId()).stream()
                .collect(Collectors.groupingBy(DefinitionProvenance::getKind));

        int customized = 0;
        for (Map.Entry<String, List<DefinitionProvenance>> kind : byKind.entrySet()) {
            Optional<DefinitionImportParticipant> participant = participants.forKind(kind.getKey());
            if (participant.isEmpty()) {
                continue;
            }
            Set<String> keys = kind.getValue().stream().map(DefinitionProvenance::getKey).collect(Collectors.toSet());
            Map<String, String> current = participant.get().fingerprints(orgKey, keys);
            for (DefinitionProvenance provenance : kind.getValue()) {
                String fingerprint = current.get(provenance.getKey());
                if (fingerprint == null || !ImportBundle.hash(fingerprint).equals(provenance.getContentHash())) {
                    customized++;
                }
            }
        }
        return customized;
    }

    /**
     * @param offeredVersion   the version the platform offers when it is newer than the installed one; otherwise null
     * @param upgradeAvailable whether an upgrade is on offer
     */
    public record View(String starterId, String version, Instant installedAt, String installedBy,
                       int customizedDefinitions, String offeredVersion, boolean upgradeAvailable) {
    }
}
