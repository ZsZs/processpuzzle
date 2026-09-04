package com.processpuzzle.platformadmin.adapter.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.platformadmin.adapter.inbound.dto.DefaultOrganizationDocument;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.usecase.OrganizationDetails;
import com.processpuzzle.platformadmin.usecase.ProvisionOrganization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Provisions the tenants a fresh deployment starts with, from bundled
 * {@code default-organizations/<orgKey>-organization.yaml} files.
 *
 * <p><strong>Ordered ahead of every feature's seed loader</strong> ({@code @Order(0)}; base-entity's
 * is 10, base-state's 20, and the rest are unordered and therefore later). That is load-bearing:
 * each of those files is named after the tenant it seeds and, from now on, skips a tenant that does
 * not exist rather than creating one. Something has to go first, and it has to be the module that
 * owns the aggregate.
 *
 * <p><strong>Create-only.</strong> An existing tenant is left exactly as it is. A restart against a
 * persistent database must not overwrite a name or locale an operator changed by hand. Same rule as
 * {@link DefaultPlanLoader} and base-entity's importer, with the same consequence: editing this YAML
 * has no effect on an already-seeded database.
 *
 * <p>Nothing here can fail startup: every problem is logged and the next file is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "platform-admin", name = "loadDefaultOrganizations", havingValue = "true")
public class DefaultOrganizationLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultOrganizationLoader.class);
    private static final String FILE_SUFFIX = "-organization.yaml";
    private static final String LOCATION = "classpath*:default-organizations/*" + FILE_SUFFIX;

    private final ProvisionOrganization provisionOrganization;
    private final OrganizationRepository repository;
    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public DefaultOrganizationLoader(ProvisionOrganization provisionOrganization,
                                     OrganizationRepository repository,
                                     ResourcePatternResolver resourceResolver) {
        this.provisionOrganization = provisionOrganization;
        this.repository = repository;
        this.resourceResolver = resourceResolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    public void loadDefaults() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default organization files at {}", LOCATION, e);
            return;
        }
        if (resources.length == 0) {
            LOG.info("No default organization files found at {}", LOCATION);
            return;
        }
        for (Resource resource : resources) {
            load(resource);
        }
    }

    private void load(Resource resource) {
        String fileName = resource.getFilename();
        if (fileName == null || !fileName.endsWith(FILE_SUFFIX)) {
            return;
        }
        String orgKey = fileName.substring(0, fileName.length() - FILE_SUFFIX.length());
        if (repository.existsById(orgKey)) {
            LOG.debug("Organization '{}' already exists; leaving {} alone.", orgKey, fileName);
            return;
        }

        DefaultOrganizationDocument document;
        try (InputStream in = resource.getInputStream()) {
            document = yamlMapper.readValue(in, DefaultOrganizationDocument.class);
        } catch (IOException e) {
            LOG.warn("Skipping {}: could not be read as a default organization file.", fileName, e);
            return;
        }
        if (document == null) {
            LOG.warn("Skipping {}: empty document.", fileName);
            return;
        }

        try {
            provisionOrganization.execute(orgKey, new OrganizationDetails(
                    isBlank(document.name()) ? orgKey : document.name(),
                    document.description(),
                    document.contactEmail(),
                    document.defaultLocale()));
            LOG.info("Provisioned organization '{}' from {}.", orgKey, fileName);
        } catch (RuntimeException e) {
            LOG.warn("Skipping {}: could not provision organization '{}'.", fileName, orgKey, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
