package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.usecase.service.DefaultShell;
import com.processpuzzle.shared.event.OrganizationProvisionedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Creates the application a new tenant's designer opens first.
 *
 * <p>This was {@code ProvisionTenant}, a base-app use case that called platform-admin's
 * {@code ProvisionOrganization} and then saved the starter definition, with {@code @Transactional} on
 * the outer method so both writes committed together. It kept the invariant — no client can observe
 * an organization with no app to design — at the cost of base-app driving another feature's use case,
 * and of the {@code /organizations} endpoint living in base-app to call it. Both are gone; the
 * invariant is not.
 *
 * <p>{@link TransactionPhase#BEFORE_COMMIT} is what preserves it, and is not interchangeable with the
 * {@code AFTER_COMMIT} that {@code OrganizationRealmProvisioner} uses for the same event. Before
 * commit, this handler runs inside the provisioning transaction, so the starter app commits with the
 * organization row or not at all. An {@code AFTER_COMMIT} listener runs with that transaction already
 * completed and its writes are discarded without an error — the symptom would be a tenant with no
 * app and no failure anywhere, which is precisely what this must not produce. The realm provisioner
 * wants the opposite phase for the opposite reason: a realm is a network call that must not hold a
 * transaction open.
 *
 * <p>The mirror image of {@link TenantDataCleaner}, which removes these rows on deletion, and the
 * same pattern: platform-admin announces what happened to a tenant, and each feature decides for
 * itself what that means for its own data.
 *
 * <p>The definition is deliberately almost empty — a name and an empty graph — because choosing
 * theme, layout and navigation is the designer's first job, not something to guess here. With
 * {@code base-app.default-shell} on, which is the default, it is the platform's {@link DefaultShell} instead, published at once
 * and marked as untouched: the customer's own frame, to which a domain starter adds its modules (business-starters design §11.4).
 */
@Component
public class StarterAppCreator {

    /** Id of the app definition created alongside a new organization. */
    public static final String STARTER_APP_ID = "app";

    private static final Logger LOG = LoggerFactory.getLogger(StarterAppCreator.class);

    private final AppDefinitionRepository appDefinitionRepository;
    private final boolean defaultShell;

    /** An empty application: the form the tests and a host that wants no default shell use. */
    public StarterAppCreator(AppDefinitionRepository appDefinitionRepository) {
        this(appDefinitionRepository, false);
    }

    @Autowired
    public StarterAppCreator(AppDefinitionRepository appDefinitionRepository,
                             @Value("${base-app.default-shell:true}") boolean defaultShell) {
        this.appDefinitionRepository = appDefinitionRepository;
        this.defaultShell = defaultShell;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationProvisioned(OrganizationProvisionedEvent event) {
        LOG.info("Organization '{}' provisioned; creating its starter app definition '{}'.",
                event.orgKey(), STARTER_APP_ID);
        AppDefinition app = new AppDefinition(event.orgKey(), STARTER_APP_ID, event.organizationName(), null, null,
                defaultShell ? DefaultShell.graph() : AppGraph.empty());
        if (defaultShell) {
            app.markDefaultShell();
            app.publish();
        }
        appDefinitionRepository.save(app);
    }
}
