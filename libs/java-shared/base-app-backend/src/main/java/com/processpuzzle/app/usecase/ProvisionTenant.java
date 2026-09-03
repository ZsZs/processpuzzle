package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.usecase.OrganizationDetails;
import com.processpuzzle.platformadmin.usecase.ProvisionOrganization;
import com.processpuzzle.app.model.OrganizationInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a tenant together with the starter application its designer opens first.
 *
 * <p>This class exists to keep one invariant across a module boundary. Provisioning used to be a
 * single use case in this package that wrote the {@code Organization} and its starter
 * {@code AppDefinition} in one transaction, so a client could never observe an organization with no
 * app to design. The organization then moved to {@code platform-admin}, which must not know what an
 * {@code AppDefinition} is — so the transaction moved here instead. {@code @Transactional} on this
 * method is what makes {@link ProvisionOrganization}'s own {@code @Transactional} join it rather
 * than open its own, which is why both writes still commit or roll back together.
 *
 * <p>The starter definition is deliberately almost empty — just a name and an empty graph — because
 * choosing theme, layout and navigation is the designer's first job, not something to guess here.
 *
 * <p>Note what did <em>not</em> come along: the organization is now committed
 * {@code PROVISIONING}, and its realm is created after this transaction commits. The starter app
 * therefore exists before the tenant can be logged into, which is the right way round — the
 * designer's first request finds an app waiting.
 */
@Service
public class ProvisionTenant {

    /** Id of the app definition created alongside a new organization. */
    public static final String STARTER_APP_ID = "app";

    private final ProvisionOrganization provisionOrganization;
    private final AppDefinitionRepository appDefinitionRepository;

    public ProvisionTenant(ProvisionOrganization provisionOrganization,
                           AppDefinitionRepository appDefinitionRepository) {
        this.provisionOrganization = provisionOrganization;
        this.appDefinitionRepository = appDefinitionRepository;
    }

    @Transactional
    public Result execute(OrganizationInput input) {
        Organization organization = provisionOrganization.execute(input.getKey(), new OrganizationDetails(
                input.getName(),
                input.getDescription(),
                input.getContactEmail(),
                input.getDefaultLocale()));

        AppDefinition starterApp = appDefinitionRepository.save(new AppDefinition(
                organization.getKey(),
                STARTER_APP_ID,
                input.getName(),
                null,
                null,
                AppGraph.empty()));

        return new Result(organization, starterApp);
    }

    /** An organization and the starter app definition created with it. */
    public record Result(Organization organization, AppDefinition starterApp) {
    }
}
