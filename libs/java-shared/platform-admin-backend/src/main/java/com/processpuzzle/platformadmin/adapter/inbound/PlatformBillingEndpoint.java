package com.processpuzzle.platformadmin.adapter.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.platformadmin.api.PlatformBillingApi;
import com.processpuzzle.platformadmin.model.OrganizationBilling;
import com.processpuzzle.platformadmin.model.PageOfInvoice;
import com.processpuzzle.platformadmin.model.PageOfSubscription;
import com.processpuzzle.platformadmin.model.Plan;
import com.processpuzzle.platformadmin.usecase.FindAllInvoices;
import com.processpuzzle.platformadmin.usecase.FindAllPlans;
import com.processpuzzle.platformadmin.usecase.FindAllSubscriptions;
import com.processpuzzle.platformadmin.usecase.GetOrganizationBilling;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST adapter for the billing half of the {@code /platform/**} staff surface.
 *
 * <p>Every operation is a read. That is the whole of billing in this platform: there is no payment
 * provider, so nothing here can charge anyone — see platform-admin-api.yaml's description. A write
 * verb appearing on this controller would be the signal that that decision has changed.
 */
@RestController
@LogClass
public class PlatformBillingEndpoint implements PlatformBillingApi {

    private final GetOrganizationBilling getOrganizationBilling;
    private final FindAllPlans findAllPlans;
    private final FindAllSubscriptions findAllSubscriptions;
    private final FindAllInvoices findAllInvoices;
    private final PlatformAdminMapper mapper;

    public PlatformBillingEndpoint(GetOrganizationBilling getOrganizationBilling,
                                   FindAllPlans findAllPlans,
                                   FindAllSubscriptions findAllSubscriptions,
                                   FindAllInvoices findAllInvoices,
                                   PlatformAdminMapper mapper) {
        this.getOrganizationBilling = getOrganizationBilling;
        this.findAllPlans = findAllPlans;
        this.findAllSubscriptions = findAllSubscriptions;
        this.findAllInvoices = findAllInvoices;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<OrganizationBilling> getOrganizationBilling(String orgKey) {
        return ResponseEntity.ok(mapper.toModel(getOrganizationBilling.execute(orgKey)));
    }

    @Override
    public ResponseEntity<List<Plan>> listPlans(String where, String order) {
        return ResponseEntity.ok(mapper.toPlanList(findAllPlans.execute(where, order)));
    }

    @Override
    public ResponseEntity<PageOfSubscription> listSubscriptions(String where, String order,
                                                                Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toSubscriptionPage(
                findAllSubscriptions.execute(where, order, page, size)));
    }

    @Override
    public ResponseEntity<PageOfInvoice> listInvoices(String where, String order,
                                                      Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toInvoicePage(
                findAllInvoices.execute(where, order, page, size)));
    }
}
