package com.processpuzzle.platformadmin.usecase;

import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.event.OrganizationDeletedEvent;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a tenant and everything scoped by it.
 *
 * <p>The cascade is by event, not by JPA and not by reaching into other features' repositories. It
 * used to call {@code AppDefinitionRepository.deleteByOrgKey} and its module equivalent directly,
 * which was fine while base-app owned the aggregate and impossible once it did not — this module
 * cannot see base-app's repositories, and giving it sight of them would be a dependency cycle.
 *
 * <p>So it publishes {@link OrganizationDeletedEvent} and each feature deletes its own rows in a
 * {@code @TransactionalEventListener(phase = BEFORE_COMMIT)} handler, joining this transaction. That
 * is what the previous implementation's own Javadoc recommended as the better arrangement, and it
 * re-opens the gap that Javadoc documented: entity descriptors, rules, state and workflow
 * definitions are organization-scoped by contract and still are not cleaned up, but now each of
 * those features can subscribe without this class changing.
 *
 * <p>The tenant's identity realm is deleted too, and cannot be part of this transaction for the same
 * reason its creation is not — {@code OrganizationRealmProvisioner} handles it after commit.
 */
@Service
@Transactional
public class DeleteOrganization {

    private final OrganizationRepository organizationRepository;
    private final OrganizationGuard guard;
    private final ApplicationEventPublisher events;

    public DeleteOrganization(OrganizationRepository organizationRepository,
                              OrganizationGuard guard,
                              ApplicationEventPublisher events) {
        this.organizationRepository = organizationRepository;
        this.guard = guard;
        this.events = events;
    }

    public void execute(String orgKey) {
        guard.requireDesign(orgKey);
        delete(orgKey);
    }

    /** As {@link #execute}, for the {@code /platform/**} caller already gated on staff authority. */
    public void executeAsPlatformAdmin(String orgKey) {
        guard.requirePlatformAdmin();
        delete(orgKey);
    }

    private void delete(String orgKey) {
        if (!organizationRepository.existsById(orgKey)) {
            throw new OrganizationNotFoundException(orgKey);
        }
        // Published before the row goes, so a listener may still read the organization it is
        // cleaning up after.
        events.publishEvent(new OrganizationDeletedEvent(orgKey));
        organizationRepository.deleteById(orgKey);
    }
}
