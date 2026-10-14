package com.processpuzzle.release.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.release.domain.OrgMigrationStep;
import com.processpuzzle.release.domain.OrgMigrationStepRepository;
import com.processpuzzle.release.domain.OrgReleaseItem;
import com.processpuzzle.release.domain.OrgReleaseItemRepository;
import com.processpuzzle.release.domain.OrgReleaseRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** An organization's release history, newest first, each release with what it carried and the steps it ran. */
@Service
@Transactional(readOnly = true)
public class ListReleases {

    private final OrganizationGuard guard;
    private final OrgReleaseRepository releaseRepository;
    private final OrgReleaseItemRepository itemRepository;
    private final OrgMigrationStepRepository stepRepository;

    ListReleases(OrganizationGuard guard, OrgReleaseRepository releaseRepository, OrgReleaseItemRepository itemRepository,
                 OrgMigrationStepRepository stepRepository) {
        this.guard = guard;
        this.releaseRepository = releaseRepository;
        this.itemRepository = itemRepository;
        this.stepRepository = stepRepository;
    }

    public List<ReleaseRecord> execute(String orgKey) {
        guard.requireDesign(orgKey);
        Map<Long, List<OrgReleaseItem>> items = itemRepository.findByOrgKeyOrderByReleaseNumberDescSequenceNoAsc(orgKey).stream()
                .collect(Collectors.groupingBy(OrgReleaseItem::getReleaseNumber));
        Map<Long, List<OrgMigrationStep>> steps = stepRepository
                .findByOrgKeyAndReleaseNumberIsNotNullOrderByReleaseNumberDescSequenceNoAsc(orgKey).stream()
                .collect(Collectors.groupingBy(OrgMigrationStep::getReleaseNumber));
        return releaseRepository.findByOrgKeyOrderByReleaseNumberDesc(orgKey).stream()
                .map(release -> new ReleaseRecord(release, items.getOrDefault(release.getReleaseNumber(), List.of()),
                        steps.getOrDefault(release.getReleaseNumber(), List.of())))
                .toList();
    }
}
