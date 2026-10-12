package com.processpuzzle.release.usecase;

import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.release.domain.OrgReleaseItem;
import com.processpuzzle.release.domain.OrgReleaseItemRepository;
import com.processpuzzle.release.domain.OrgReleaseRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** An organization's release history, newest first, each release with what it carried. */
@Service
@Transactional(readOnly = true)
public class ListReleases {

    private final OrganizationGuard guard;
    private final OrgReleaseRepository releaseRepository;
    private final OrgReleaseItemRepository itemRepository;

    ListReleases(OrganizationGuard guard, OrgReleaseRepository releaseRepository, OrgReleaseItemRepository itemRepository) {
        this.guard = guard;
        this.releaseRepository = releaseRepository;
        this.itemRepository = itemRepository;
    }

    public List<ReleaseRecord> execute(String orgKey) {
        guard.requireDesign(orgKey);
        Map<Long, List<OrgReleaseItem>> items = itemRepository.findByOrgKeyOrderByReleaseNumberDescSequenceNoAsc(orgKey).stream()
                .collect(Collectors.groupingBy(OrgReleaseItem::getReleaseNumber));
        return releaseRepository.findByOrgKeyOrderByReleaseNumberDesc(orgKey).stream()
                .map(release -> new ReleaseRecord(release, items.getOrDefault(release.getReleaseNumber(), List.of())))
                .toList();
    }
}
