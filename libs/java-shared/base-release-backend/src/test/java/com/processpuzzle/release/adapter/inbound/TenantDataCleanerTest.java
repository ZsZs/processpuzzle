package com.processpuzzle.release.adapter.inbound;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.processpuzzle.release.domain.OrgMigrationStepRepository;
import com.processpuzzle.release.domain.OrgReleaseContentRepository;
import com.processpuzzle.release.domain.OrgReleaseItemRepository;
import com.processpuzzle.release.domain.OrgReleaseRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TenantDataCleanerTest {

    private final OrgReleaseRepository releaseRepository = mock(OrgReleaseRepository.class);
    private final OrgReleaseItemRepository itemRepository = mock(OrgReleaseItemRepository.class);
    private final OrgReleaseContentRepository contentRepository = mock(OrgReleaseContentRepository.class);
    private final OrgMigrationStepRepository stepRepository = mock(OrgMigrationStepRepository.class);

    @Test
    void erasesTheDeletedTenantsReleaseHistory_stepsContentAndItemsFirst() {
        new TenantDataCleaner(releaseRepository, itemRepository, contentRepository, stepRepository)
                .onOrganizationDeleted(new OrganizationDeletedEvent("gone"));

        InOrder order = inOrder(stepRepository, contentRepository, itemRepository, releaseRepository);
        order.verify(stepRepository).eraseByOrgKey("gone");
        order.verify(contentRepository).eraseByOrgKey("gone");
        order.verify(itemRepository).eraseByOrgKey("gone");
        order.verify(releaseRepository).eraseByOrgKey("gone");
    }
}
