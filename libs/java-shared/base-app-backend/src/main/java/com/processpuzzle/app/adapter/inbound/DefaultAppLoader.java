package com.processpuzzle.app.adapter.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.app.adapter.inbound.dto.DefaultAppsDocument;
import com.processpuzzle.app.model.AppDefinition;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.KeyAvailability;
import com.processpuzzle.app.model.ModuleDefinition;
import com.processpuzzle.app.model.ModuleDefinitionInput;
import com.processpuzzle.app.model.OrganizationInput;
import com.processpuzzle.app.model.ProvisioningResult;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.exception.AppDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.AppDefinitionInvalidException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionAlreadyExistsException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Seeds the bundled default app definitions on startup, so a fresh deployment serves a navigable
 * application instead of nothing. Gated behind {@code base-app.loadDefaultApps=true}.
 *
 * <p>App definitions are tenant-scoped, so every default has to belong to <em>some</em> organization.
 * The owning organization is part of the file name — {@code <orgKey>-apps.yaml} lands in
 * {@code orgKey}, so {@code processpuzzle-testbed-apps.yaml} is loaded into
 * {@code processpuzzle-testbed}. One deployment can therefore seed several organizations, and adding
 * one is adding a file, exactly as {@code SampleRuleLoader} treats {@code <orgKey>-rules.yaml}. The
 * pattern is {@code classpath*:} rather than {@code classpath:} so that a host application can
 * contribute its own {@code default-apps/} directory alongside this library's.
 *
 * <p>Everything goes through {@link AppEndpoint}, not through the use cases directly: the endpoint
 * already composes provisioning, validation, mapping and conflict detection, so the loader stays a
 * file walk. A default definition is consequently subject to the same structural validation and the
 * same {@code base-rule} governance as one a designer saves.
 *
 * <p>A file may declare {@code moduleDefinitions} beside its apps, and those are created first, so a
 * mount in the same file names a module that already exists. That order is a courtesy to the startup
 * log rather than a requirement: a mount naming an unknown module is a warning by design, which is what
 * keeps the two aggregates independently authorable.
 *
 * <p><strong>Existing data is never touched.</strong> An organization that already exists is loaded
 * into rather than re-provisioned, and an app or module definition whose key is already present is left
 * exactly as it is. Restarting against a persistent database therefore cannot overwrite a designer's
 * edits with the bundled defaults, which also makes the loader safe to leave enabled outside
 * development.
 *
 * <p>Nothing here can fail startup: every problem is logged and the next file or definition is
 * attempted. A convenience that refuses to boot would be worse than one that seeds nothing.
 */
@Component
@ConditionalOnProperty(prefix = "base-app", name = "loadDefaultApps", havingValue = "true")
public class DefaultAppLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultAppLoader.class);
    private static final String APPS_FILE_SUFFIX = "-apps.yaml";
    private static final String DEFAULT_APPS_LOCATION = "classpath*:default-apps/*" + APPS_FILE_SUFFIX;

    /** The {@code errorId} {@code checkOrganizationKey} answers with when the tenant already exists. */
    private static final String KEY_TAKEN = "organization.key.taken";

    private final AppEndpoint endpoint;
    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public DefaultAppLoader(AppEndpoint endpoint, ResourcePatternResolver resourceResolver) {
        this.endpoint = endpoint;
        this.resourceResolver = resourceResolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(DEFAULT_APPS_LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default app files at {}", DEFAULT_APPS_LOCATION, e);
            return;
        }

        if (resources.length == 0) {
            LOG.info("No default app files found at {}", DEFAULT_APPS_LOCATION);
            return;
        }

        for (Resource resource : resources) {
            load(resource);
        }
    }

    private void load(Resource resource) {
        String fileName = resource.getFilename();
        String orgKey = orgKeyOf(fileName);
        if (orgKey == null) {
            LOG.warn("Skipping default app file '{}': the name does not follow the '<orgKey>{}' convention.",
                    fileName, APPS_FILE_SUFFIX);
            return;
        }

        DefaultAppsDocument document;
        try (InputStream input = resource.getInputStream()) {
            document = yamlMapper.readValue(input, DefaultAppsDocument.class);
        } catch (IOException e) {
            LOG.warn("Failed to read default app file {}", fileName, e);
            return;
        }

        if (!ensureOrganization(orgKey, document.organization(), fileName)) {
            return;
        }

        // Modules first, so that a mount declared in this same file names a module that already exists.
        if (!document.moduleDefinitions().isEmpty()) {
            Tally modules = new Tally();
            for (ModuleDefinitionInput module : document.moduleDefinitions()) {
                modules.add(createModule(orgKey, module, fileName));
            }
            LOG.info("Loaded default modules from {} into organization '{}': created={}, already present={}, rejected={}",
                    fileName, orgKey, modules.created, modules.skipped, modules.rejected);
        }

        Tally apps = new Tally();
        for (AppDefinitionInput definition : document.appDefinitions()) {
            apps.add(create(orgKey, definition, fileName));
        }
        LOG.info("Loaded default apps from {} into organization '{}': created={}, already present={}, rejected={}",
                fileName, orgKey, apps.created, apps.skipped, apps.rejected);
    }

    /**
     * Provisions {@code orgKey} when it is still free, so a default-apps file bootstraps its whole
     * tenant on an empty database.
     *
     * @return whether the organization is now available to load into
     */
    private boolean ensureOrganization(String orgKey, OrganizationInput declared, String fileName) {
        KeyAvailability availability;
        try {
            availability = endpoint.checkOrganizationKey(orgKey).getBody();
        } catch (RuntimeException e) {
            LOG.warn("Skipping {}: could not check organization key '{}'.", fileName, orgKey, e);
            return false;
        }
        if (availability == null) {
            LOG.warn("Skipping {}: no answer when checking organization key '{}'.", fileName, orgKey);
            return false;
        }

        if (!Boolean.TRUE.equals(availability.getAvailable())) {
            if (KEY_TAKEN.equals(availability.getErrorId())) {
                LOG.debug("Organization '{}' already exists; loading {} into it.", orgKey, fileName);
                return true;
            }
            // Reserved or malformed: the file name is not a claimable tenant slug, so there is nowhere
            // to put its definitions. Renaming the file is the fix.
            LOG.warn("Skipping {}: organization key '{}' cannot be claimed ({}).",
                    fileName, orgKey, availability.getErrorId());
            return false;
        }

        try {
            ProvisioningResult result = endpoint.provisionOrganization(organizationInput(orgKey, declared)).getBody();
            String starterAppId = result == null || result.getAppDefinition() == null
                    ? "none" : result.getAppDefinition().getId();
            LOG.info("Provisioned organization '{}' from {}; its starter app definition is '{}'.",
                    orgKey, fileName, starterAppId);
            return true;
        } catch (RuntimeException e) {
            LOG.warn("Skipping {}: could not provision organization '{}'.", fileName, orgKey, e);
            return false;
        }
    }

    /**
     * The provisioning payload for {@code orgKey}. The key comes from the file name rather than from
     * the document, so a file copied between deployments cannot seed the tenant it was copied from.
     * A file without an {@code organization} block still provisions, named after its key.
     */
    private static OrganizationInput organizationInput(String orgKey, OrganizationInput declared) {
        OrganizationInput input = new OrganizationInput(orgKey,
                declared == null || isBlank(declared.getName()) ? orgKey : declared.getName());
        if (declared != null) {
            input.setDescription(declared.getDescription());
            input.setContactEmail(declared.getContactEmail());
            input.setDefaultLocale(declared.getDefaultLocale());
        }
        return input;
    }

    private Outcome create(String orgKey, AppDefinitionInput definition, String fileName) {
        if (definition == null || isBlank(definition.getId())) {
            LOG.warn("Skipping an app definition in {}: the entry is null or has no id.", fileName);
            return Outcome.REJECTED;
        }

        try {
            AppDefinition created = endpoint.createAppDefinition(orgKey, definition).getBody();
            LOG.info("Created default app definition '{}' in organization '{}' as revision {}.",
                    definition.getId(), orgKey,
                    created == null || created.getVersion() == null ? "?" : created.getVersion());
            return Outcome.CREATED;
        } catch (AppDefinitionAlreadyExistsException e) {
            LOG.info("Default app definition '{}' already exists in organization '{}'; left untouched.",
                    definition.getId(), orgKey);
            return Outcome.SKIPPED;
        } catch (AppDefinitionInvalidException e) {
            // Logged problem by problem: the whole point of a validated default is that a broken one
            // says which part is broken.
            LOG.warn("Default app definition '{}' from {} was rejected by validation.",
                    definition.getId(), fileName);
            for (AppValidationProblem problem : e.getProblems()) {
                LOG.warn("  {} {}: {}", problem.path(), problem.errorId(), problem.errorText());
            }
            return Outcome.REJECTED;
        } catch (RuntimeException e) {
            LOG.warn("Failed to create default app definition '{}' from {} in organization '{}'.",
                    definition.getId(), fileName, orgKey, e);
            return Outcome.REJECTED;
        }
    }

    /**
     * The module counterpart of {@link #create}, and deliberately as forgiving: a rejected module leaves
     * the apps that mount it with a dangling {@code moduleKey}, which is a warning rather than an error,
     * so there is nothing here that should stop the rest of the file from loading.
     */
    private Outcome createModule(String orgKey, ModuleDefinitionInput module, String fileName) {
        if (module == null || isBlank(module.getKey())) {
            LOG.warn("Skipping a module definition in {}: the entry is null or has no key.", fileName);
            return Outcome.REJECTED;
        }

        try {
            ModuleDefinition created = endpoint.createModuleDefinition(orgKey, module).getBody();
            LOG.info("Created default module definition '{}' in organization '{}' as revision {}.",
                    module.getKey(), orgKey,
                    created == null || created.getVersion() == null ? "?" : created.getVersion());
            return Outcome.CREATED;
        } catch (ModuleDefinitionAlreadyExistsException e) {
            LOG.info("Default module definition '{}' already exists in organization '{}'; left untouched.",
                    module.getKey(), orgKey);
            return Outcome.SKIPPED;
        } catch (ModuleDefinitionInvalidException e) {
            LOG.warn("Default module definition '{}' from {} was rejected by validation.",
                    module.getKey(), fileName);
            for (AppValidationProblem problem : e.getProblems()) {
                LOG.warn("  {} {}: {}", problem.path(), problem.errorId(), problem.errorText());
            }
            return Outcome.REJECTED;
        } catch (RuntimeException e) {
            LOG.warn("Failed to create default module definition '{}' from {} in organization '{}'.",
                    module.getKey(), fileName, orgKey, e);
            return Outcome.REJECTED;
        }
    }

    /** The part of {@code <orgKey>-apps.yaml} before the suffix, or {@code null} if there is none. */
    private static String orgKeyOf(String fileName) {
        if (fileName == null || !fileName.endsWith(APPS_FILE_SUFFIX)) {
            return null;
        }
        String orgKey = fileName.substring(0, fileName.length() - APPS_FILE_SUFFIX.length());
        return orgKey.isBlank() ? null : orgKey;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** What became of one entry, for the per-file summary. */
    private enum Outcome {
        CREATED, SKIPPED, REJECTED
    }

    /** The per-file summary itself, counted the same way for both kinds of definition. */
    private static final class Tally {
        private int created;
        private int skipped;
        private int rejected;

        private void add(Outcome outcome) {
            switch (outcome) {
                case CREATED -> created++;
                case SKIPPED -> skipped++;
                case REJECTED -> rejected++;
            }
        }
    }
}
