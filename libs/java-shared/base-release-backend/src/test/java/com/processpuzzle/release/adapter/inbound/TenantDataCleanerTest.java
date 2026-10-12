package com.processpuzzle.release.adapter.inbound;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.processpuzzle.release.domain.OrgReleaseItemRepository;
import com.processpuzzle.release.domain.OrgReleaseRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TenantDataCleanerTest {

    private final OrgReleaseRepository releaseRepository = mock(OrgReleaseRepository.class);
    private final OrgReleaseItemRepository itemRepository = mock(OrgReleaseItemRepository.class);

    @Test
    void erasesTheDeletedTenantsReleaseHistory_itemsFirst() {
        new TenantDataCleaner(releaseRepository, itemRepository).onOrganizationDeleted(new OrganizationDeletedEvent("gone"));

        InOrder order = inOrder(itemRepository, releaseRepository);
        order.verify(itemRepository).eraseByOrgKey("gone");
        order.verify(releaseRepository).eraseByOrgKey("gone");
    }
}
