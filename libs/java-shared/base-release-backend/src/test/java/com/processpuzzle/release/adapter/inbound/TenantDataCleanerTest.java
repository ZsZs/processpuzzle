package com.processpuzzle.release.adapter.inbound;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

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

    @Test
    void erasesTheDeletedTenantsReleaseHistory_contentAndItemsFirst() {
        new TenantDataCleaner(releaseRepository, itemRepository, contentRepository)
                .onOrganizationDeleted(new OrganizationDeletedEvent("gone"));

        InOrder order = inOrder(contentRepository, itemRepository, releaseRepository);
        order.verify(contentRepository).eraseByOrgKey("gone");
        order.verify(itemRepository).eraseByOrgKey("gone");
        order.verify(releaseRepository).eraseByOrgKey("gone");
    }
}
