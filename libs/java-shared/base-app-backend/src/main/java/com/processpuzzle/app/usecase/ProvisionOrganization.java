package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.Organization;
import com.processpuzzle.app.domain.OrganizationRepository;
import com.processpuzzle.app.domain.OrganizationStatus;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.model.OrganizationInput;
import com.processpuzzle.app.usecase.exception.OrganizationAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.OrganizationKeyInvalidException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Creates a tenant together with the starter application its designer opens first.
 *
 * <p>Both are written in one transaction, so a client never observes an organization without an app
 * to design. The starter definition is deliberately almost empty — a name and a single
 * {@code content} region — because choosing theme, layout and navigation is the designer's first
 * job, not something to guess here.
 *
 * <p>The organization is created {@code ACTIVE} rather than {@code PROVISIONING}: there is no
 * out-of-band step yet (no Keycloak realm to create), so a state entered and left inside one
 * transaction would never be observable.
 */
@Service
public class ProvisionOrganization {

    /** Id of the app definition created alongside a new organization. */
    public static final String STARTER_APP_ID = "app";

    private final OrganizationRepository organizationRepository;
    private final AppDefinitionRepository appDefinitionRepository;
    private final CheckOrganizationKey checkOrganizationKey;

    public ProvisionOrganization(OrganizationRepository organizationRepository,
                                 AppDefinitionRepository appDefinitionRepository,
                                 CheckOrganizationKey checkOrganizationKey) {
        this.organizationRepository = organizationRepository;
        this.appDefinitionRepository = appDefinitionRepository;
        this.checkOrganizationKey = checkOrganizationKey;
    }

    @Transactional
    public Result execute(OrganizationInput input) {
        String orgKey = input.getKey() == null ? "" : input.getKey().trim().toLowerCase(Locale.ROOT);
        KeyCheckOutcome keyCheck = checkOrganizationKey.execute(orgKey);
        if (!keyCheck.available()) {
            if ("organization.key.taken".equals(keyCheck.errorId())) {
                throw new OrganizationAlreadyExistsException(orgKey);
            }
            throw new OrganizationKeyInvalidException(keyCheck.errorId(),
                    "Organization key cannot be claimed: '" + orgKey + "' (" + keyCheck.errorId() + ").");
        }

        Organization organization = organizationRepository.save(new Organization(
                orgKey,
                input.getName(),
                input.getDescription(),
                input.getContactEmail(),
                input.getDefaultLocale(),
                OrganizationStatus.ACTIVE));

        AppDefinition starterApp = appDefinitionRepository.save(new AppDefinition(
                orgKey,
                STARTER_APP_ID,
                input.getName(),
                null,
                null,
                starterGraph()));

        return new Result(organization, starterApp);
    }

    /**
     * The graph of a freshly provisioned app: one content region so the shell has somewhere to
     * render, and nothing else. No theme means the frontend defaults apply until the designer picks
     * one.
     */
    private static AppGraph starterGraph() {
        return new AppGraph(null, null, List.of(new Region("content", List.of(), List.of())), List.of());
    }

    /** An organization and the starter app definition created with it. */
    public record Result(Organization organization, AppDefinition starterApp) {
    }
}
