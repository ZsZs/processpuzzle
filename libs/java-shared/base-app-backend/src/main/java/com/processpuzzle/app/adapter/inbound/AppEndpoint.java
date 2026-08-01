package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.api.BaseAppApi;
import com.processpuzzle.app.model.AppDefinition;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.AppLayout;
import com.processpuzzle.app.model.KeyAvailability;
import com.processpuzzle.app.model.Organization;
import com.processpuzzle.app.model.OrganizationInput;
import com.processpuzzle.app.model.OrganizationUpdate;
import com.processpuzzle.app.model.PageDefinition;
import com.processpuzzle.app.model.PageOfAppDefinitionSummary;
import com.processpuzzle.app.model.ProvisioningResult;
import com.processpuzzle.app.model.ValidationResult;
import com.processpuzzle.app.usecase.CheckOrganizationKey;
import com.processpuzzle.app.usecase.CreateAppDefinition;
import com.processpuzzle.app.usecase.DeleteAppDefinition;
import com.processpuzzle.app.usecase.DeleteOrganization;
import com.processpuzzle.app.usecase.ExportAppDefinition;
import com.processpuzzle.app.usecase.FindAllAppDefinitions;
import com.processpuzzle.app.usecase.FindAppDefinition;
import com.processpuzzle.app.usecase.FindOrganization;
import com.processpuzzle.app.usecase.GetAppLayout;
import com.processpuzzle.app.usecase.GetPageDefinition;
import com.processpuzzle.app.usecase.ImportAppDefinitions;
import com.processpuzzle.app.usecase.ImportOutcome;
import com.processpuzzle.app.usecase.ProvisionOrganization;
import com.processpuzzle.app.usecase.PublishAppDefinition;
import com.processpuzzle.app.usecase.UpdateAppDefinition;
import com.processpuzzle.app.usecase.UpdateOrganization;
import com.processpuzzle.app.usecase.ValidateAppDefinition;
import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.shared.model.ImportResult;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * REST adapter for the base-app feature, implementing the generated {@link BaseAppApi}. Holds no
 * logic of its own: it delegates to a use case and maps the result.
 */
@RestController
@LogClass
public class AppEndpoint implements BaseAppApi {

    private final ProvisionOrganization provisionOrganization;
    private final CheckOrganizationKey checkOrganizationKey;
    private final FindOrganization findOrganization;
    private final UpdateOrganization updateOrganization;
    private final DeleteOrganization deleteOrganization;
    private final CreateAppDefinition createAppDefinition;
    private final FindAppDefinition findAppDefinition;
    private final FindAllAppDefinitions findAllAppDefinitions;
    private final UpdateAppDefinition updateAppDefinition;
    private final DeleteAppDefinition deleteAppDefinition;
    private final PublishAppDefinition publishAppDefinition;
    private final GetAppLayout getAppLayout;
    private final GetPageDefinition getPageDefinition;
    private final ValidateAppDefinition validateAppDefinition;
    private final ImportAppDefinitions importAppDefinitions;
    private final ExportAppDefinition exportAppDefinition;
    private final AppMapper mapper;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public AppEndpoint(ProvisionOrganization provisionOrganization,
                       CheckOrganizationKey checkOrganizationKey,
                       FindOrganization findOrganization,
                       UpdateOrganization updateOrganization,
                       DeleteOrganization deleteOrganization,
                       CreateAppDefinition createAppDefinition,
                       FindAppDefinition findAppDefinition,
                       FindAllAppDefinitions findAllAppDefinitions,
                       UpdateAppDefinition updateAppDefinition,
                       DeleteAppDefinition deleteAppDefinition,
                       PublishAppDefinition publishAppDefinition,
                       GetAppLayout getAppLayout,
                       GetPageDefinition getPageDefinition,
                       ValidateAppDefinition validateAppDefinition,
                       ImportAppDefinitions importAppDefinitions,
                       ExportAppDefinition exportAppDefinition,
                       AppMapper mapper) {
        this.provisionOrganization = provisionOrganization;
        this.checkOrganizationKey = checkOrganizationKey;
        this.findOrganization = findOrganization;
        this.updateOrganization = updateOrganization;
        this.deleteOrganization = deleteOrganization;
        this.createAppDefinition = createAppDefinition;
        this.findAppDefinition = findAppDefinition;
        this.findAllAppDefinitions = findAllAppDefinitions;
        this.updateAppDefinition = updateAppDefinition;
        this.deleteAppDefinition = deleteAppDefinition;
        this.publishAppDefinition = publishAppDefinition;
        this.getAppLayout = getAppLayout;
        this.getPageDefinition = getPageDefinition;
        this.validateAppDefinition = validateAppDefinition;
        this.importAppDefinitions = importAppDefinitions;
        this.exportAppDefinition = exportAppDefinition;
        this.mapper = mapper;
    }

    // --- organizations -------------------------------------------------------------------

    @Override
    public ResponseEntity<ProvisioningResult> provisionOrganization(OrganizationInput input) {
        ProvisionOrganization.Result result = provisionOrganization.execute(input);
        return new ResponseEntity<>(mapper.toModel(result.organization(), result.starterApp()),
                HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<KeyAvailability> checkOrganizationKey(String key) {
        return ResponseEntity.ok(mapper.toModel(checkOrganizationKey.execute(key)));
    }

    @Override
    public ResponseEntity<Organization> getOrganization(String orgKey) {
        return ResponseEntity.ok(mapper.toModel(findOrganization.execute(orgKey)));
    }

    @Override
    public ResponseEntity<Organization> updateOrganization(String orgKey, OrganizationUpdate input) {
        return ResponseEntity.ok(mapper.toModel(updateOrganization.execute(orgKey, input)));
    }

    @Override
    public ResponseEntity<Void> deleteOrganization(String orgKey) {
        deleteOrganization.execute(orgKey);
        return ResponseEntity.noContent().build();
    }

    // --- app definitions -----------------------------------------------------------------

    @Override
    public ResponseEntity<AppDefinition> createAppDefinition(String orgKey, AppDefinitionInput input) {
        return new ResponseEntity<>(mapper.toModel(createAppDefinition.execute(orgKey, input)),
                HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<AppDefinition> getAppDefinition(String orgKey, String appId) {
        return ResponseEntity.ok(mapper.toModel(findAppDefinition.execute(orgKey, appId)));
    }

    @Override
    public ResponseEntity<PageOfAppDefinitionSummary> listAppDefinitions(String orgKey, String where,
                                                                         String order, Integer page,
                                                                         Integer size) {
        return ResponseEntity.ok(mapper.toModel(
                findAllAppDefinitions.execute(orgKey, where, order, page, size)));
    }

    @Override
    public ResponseEntity<AppDefinition> updateAppDefinition(String orgKey, String appId,
                                                             AppDefinitionInput input) {
        return ResponseEntity.ok(mapper.toModel(updateAppDefinition.execute(orgKey, appId, input)));
    }

    @Override
    public ResponseEntity<Void> deleteAppDefinition(String orgKey, String appId) {
        deleteAppDefinition.execute(orgKey, appId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AppDefinition> publishAppDefinition(String orgKey, String appId) {
        return ResponseEntity.ok(mapper.toModel(publishAppDefinition.execute(orgKey, appId)));
    }

    // --- runtime -------------------------------------------------------------------------

    @Override
    public ResponseEntity<AppLayout> getAppLayout(String orgKey, String appId, Boolean draft) {
        GetAppLayout.Result result = getAppLayout.execute(orgKey, appId, Boolean.TRUE.equals(draft));
        return ResponseEntity.ok(mapper.toLayout(result.definition(), result.graph(), result.defaultLocale()));
    }

    @Override
    public ResponseEntity<PageDefinition> getPageDefinition(String orgKey, String appId, String pageId,
                                                            Boolean draft) {
        return ResponseEntity.ok(mapper.toModel(
                getPageDefinition.execute(orgKey, appId, pageId, Boolean.TRUE.equals(draft))));
    }

    // --- validation and transfer ---------------------------------------------------------

    @Override
    public ResponseEntity<ValidationResult> validateAppDefinition(String orgKey, AppDefinitionInput input) {
        return ResponseEntity.ok(mapper.toModel(validateAppDefinition.execute(orgKey, input)));
    }

    @Override
    public ResponseEntity<ImportResult> importAppDefinitions(String orgKey, MultipartFile file) {
        try {
            ImportOutcome outcome = importAppDefinitions.execute(orgKey, file.getInputStream());
            return ResponseEntity.ok(mapper.toModel(outcome));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ResponseEntity<Resource> exportAppDefinition(String orgKey, String appId) {
        try {
            byte[] yaml = exportAppDefinition.execute(orgKey, appId);
            // The generated operation also declares application/json, so the content type has to be
            // set explicitly or content negotiation picks JSON and serializes the byte array.
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + orgKey + "-" + appId + ".yaml\"")
                    .contentType(MediaType.parseMediaType("application/x-yaml"))
                    .body(new ByteArrayResource(yaml));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
