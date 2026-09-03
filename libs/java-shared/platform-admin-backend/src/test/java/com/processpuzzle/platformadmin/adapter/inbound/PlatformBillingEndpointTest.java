package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.platformadmin.domain.BillingInterval;
import com.processpuzzle.platformadmin.domain.Plan;
import com.processpuzzle.platformadmin.usecase.FindAllInvoices;
import com.processpuzzle.platformadmin.usecase.FindAllPlans;
import com.processpuzzle.platformadmin.usecase.FindAllSubscriptions;
import com.processpuzzle.platformadmin.usecase.GetOrganizationBilling;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

import java.util.List;

import static com.processpuzzle.platformadmin.PlatformAdminTestFixtures.ORG_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformBillingEndpointTest {

    private final GetOrganizationBilling getOrganizationBilling = mock(GetOrganizationBilling.class);
    private final FindAllPlans findAllPlans = mock(FindAllPlans.class);
    private final FindAllSubscriptions findAllSubscriptions = mock(FindAllSubscriptions.class);
    private final FindAllInvoices findAllInvoices = mock(FindAllInvoices.class);
    private final PlatformBillingEndpoint endpoint = new PlatformBillingEndpoint(
            getOrganizationBilling, findAllPlans, findAllSubscriptions, findAllInvoices,
            new PlatformAdminMapper());

    @Test
    void aBillingPositionIsAnsweredEvenWhenTheTenantHasNoSubscription() {
        when(getOrganizationBilling.execute(ORG_KEY)).thenReturn(
                new GetOrganizationBilling.Result(ORG_KEY, null, null, List.of(), List.of()));

        var response = endpoint.getOrganizationBilling(ORG_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrgKey()).isEqualTo(ORG_KEY);
        assertThat(response.getBody().getSubscription()).isNull();
    }

    @Test
    void thePlanCatalogIsAnsweredUnpaged() {
        when(findAllPlans.execute(null, null)).thenReturn(List.of(
                new Plan("free", "Free", null, BillingInterval.MONTHLY, "EUR", 0L, List.of())));

        var body = endpoint.listPlans(null, null).getBody();

        assertThat(body).isNotNull().hasSize(1);
        assertThat(body.getFirst().getCode()).isEqualTo("free");
    }

    @Test
    void subscriptionsAndInvoicesAreAnsweredAsPages() {
        when(findAllSubscriptions.execute(null, null, null, null)).thenReturn(new PageImpl<>(List.of()));
        when(findAllInvoices.execute(null, null, null, null)).thenReturn(new PageImpl<>(List.of()));

        assertThat(endpoint.listSubscriptions(null, null, null, null).getBody()).isNotNull();
        assertThat(endpoint.listInvoices(null, null, null, null).getBody()).isNotNull();
    }
}
