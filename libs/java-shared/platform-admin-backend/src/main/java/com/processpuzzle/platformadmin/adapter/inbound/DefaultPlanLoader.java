package com.processpuzzle.platformadmin.adapter.inbound;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.platformadmin.adapter.inbound.dto.DefaultPlansDocument;
import com.processpuzzle.platformadmin.domain.BillingInterval;
import com.processpuzzle.platformadmin.domain.Plan;
import com.processpuzzle.platformadmin.domain.PlanLimit;
import com.processpuzzle.platformadmin.domain.PlanRepository;
import com.processpuzzle.platformadmin.domain.UsageMetric;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Seeds the bundled plan catalog on startup, so a fresh deployment has something for a subscription
 * to name instead of nothing. Gated behind {@code platform-admin.loadDefaultPlans=true}, following
 * the {@code Default*Loader} idiom the other feature libraries use.
 *
 * <p>The location is {@code classpath*:default-plans/*-plans.yaml}. The per-feature directory is not
 * decoration: {@code classpath*:} scans every jar on the classpath, and identically named seed files
 * in two libraries leak into each other's loaders — the directory is the only thing discriminating
 * them. {@code classpath*:} rather than {@code classpath:} so a host application can contribute its
 * own catalog alongside this library's.
 *
 * <p><strong>Create-only. Existing plans are never touched.</strong> A restart against a persistent
 * database must not reprice a plan customers are already subscribed to, and must not silently undo a
 * price an operator changed by hand. That matches base-entity's seed importer, and the consequence is
 * the same: editing a price in the YAML has no effect on an already-seeded database.
 *
 * <p>Plans are the one seeded thing here that is <em>not</em> tenant-scoped — there is no
 * {@code orgKey} in the file name, unlike {@code <orgKey>-widgets.yaml} and friends, because a plan
 * belongs to the platform's catalog rather than to a customer.
 *
 * <p>Nothing here can fail startup: every problem is logged and the next file or plan is attempted.
 */
@Component
@ConditionalOnProperty(prefix = "platform-admin", name = "loadDefaultPlans", havingValue = "true")
public class DefaultPlanLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultPlanLoader.class);
    private static final String PLANS_LOCATION = "classpath*:default-plans/*-plans.yaml";

    private final PlanRepository repository;
    private final ResourcePatternResolver resourceResolver;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public DefaultPlanLoader(PlanRepository repository, ResourcePatternResolver resourceResolver) {
        this.repository = repository;
        this.resourceResolver = resourceResolver;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadDefaults() {
        Resource[] resources;
        try {
            resources = resourceResolver.getResources(PLANS_LOCATION);
        } catch (IOException e) {
            LOG.warn("Unable to scan for default plan files at {}", PLANS_LOCATION, e);
            return;
        }
        if (resources.length == 0) {
            LOG.info("No default plan files found at {}", PLANS_LOCATION);
            return;
        }
        for (Resource resource : resources) {
            load(resource);
        }
    }

    private void load(Resource resource) {
        String fileName = resource.getFilename();
        DefaultPlansDocument document;
        try (InputStream in = resource.getInputStream()) {
            document = yamlMapper.readValue(in, DefaultPlansDocument.class);
        } catch (IOException e) {
            LOG.warn("Skipping {}: could not be read as a default plans document.", fileName, e);
            return;
        }
        if (document == null || document.plans() == null || document.plans().isEmpty()) {
            LOG.warn("Skipping {}: declares no plans.", fileName);
            return;
        }

        int created = 0;
        int alreadyPresent = 0;
        int rejected = 0;
        for (DefaultPlansDocument.PlanEntry entry : document.plans()) {
            switch (seed(entry, fileName)) {
                case CREATED -> created++;
                case ALREADY_PRESENT -> alreadyPresent++;
                case REJECTED -> rejected++;
            }
        }
        LOG.info("Loaded default plans from {}: created={}, already present={}, rejected={}",
                fileName, created, alreadyPresent, rejected);
    }

    private Outcome seed(DefaultPlansDocument.PlanEntry entry, String fileName) {
        if (entry.code() == null || entry.code().isBlank() || entry.name() == null) {
            LOG.warn("Skipping a plan in {}: code and name are both required.", fileName);
            return Outcome.REJECTED;
        }
        if (repository.existsById(entry.code())) {
            LOG.debug("Plan '{}' already exists; left untouched.", entry.code());
            return Outcome.ALREADY_PRESENT;
        }
        try {
            repository.save(new Plan(
                    entry.code(),
                    entry.name(),
                    entry.description(),
                    parseInterval(entry.interval(), entry.code()),
                    entry.currency(),
                    entry.amountMinor() == null ? 0L : entry.amountMinor(),
                    parseLimits(entry.limits(), entry.code())));
            LOG.info("Created default plan '{}'.", entry.code());
            return Outcome.CREATED;
        } catch (RuntimeException e) {
            LOG.warn("Failed to create default plan '{}' from {}.", entry.code(), fileName, e);
            return Outcome.REJECTED;
        }
    }

    private static BillingInterval parseInterval(String raw, String planCode) {
        if (raw == null || raw.isBlank()) {
            return BillingInterval.MONTHLY;
        }
        try {
            return BillingInterval.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOG.warn("Plan '{}' declares interval '{}', which is not one of {}; using MONTHLY.",
                    planCode, raw, List.of(BillingInterval.values()));
            return BillingInterval.MONTHLY;
        }
    }

    /**
     * An unrecognised metric name is dropped with a warning rather than failing the plan. A plan is
     * still usable — and still sells — with one limit missing, whereas rejecting it outright would
     * leave a deployment with no catalog at all because of one typo.
     */
    private static List<PlanLimit> parseLimits(Map<String, Long> raw, String planCode) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<PlanLimit> limits = new ArrayList<>(raw.size());
        raw.forEach((metricName, maxQuantity) -> {
            if (metricName == null || maxQuantity == null) {
                return;
            }
            try {
                limits.add(new PlanLimit(
                        UsageMetric.valueOf(metricName.trim().toUpperCase(Locale.ROOT)), maxQuantity));
            } catch (IllegalArgumentException e) {
                LOG.warn("Plan '{}' declares a limit on unknown metric '{}'; ignored.",
                        planCode, metricName);
            }
        });
        return limits;
    }

    private enum Outcome {
        CREATED,
        ALREADY_PRESENT,
        REJECTED
    }
}
