package com.processpuzzle.calculation.adapter.inbound;

import com.processpuzzle.calculation.port.EntityCatalogPort;
import com.processpuzzle.calculation.usecase.assist.CatalogDescription;
import com.processpuzzle.calculation.usecase.assist.SourceChecker;
import com.processpuzzle.core.assist.AssistRequest;
import com.processpuzzle.core.assist.AuthoringAssistParticipant;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Writes a calculation's YAML source from the user's description (assist task {@code calculation.source}). The
 * {@code text} is the description the user wrote — the source's own {@code description} key — and {@code current} is the
 * whole source as it stands, whose id and description the candidate has to keep. The model is told which entity types the
 * organization has; the candidate is checked by the calculation validator and against those entities. It takes no
 * parameter. Whoever may author metadata in the organization may use it.
 */
@Component
public class CalculationSourceAssist implements AuthoringAssistParticipant {

    public static final String TASK = "calculation.source";
    private static final String GUIDE = "assist/calculation/source-guide.md";

    private final ObjectProvider<OrganizationGuard> guard;
    private final ObjectProvider<EntityCatalogPort> catalog;
    private final SourceChecker checker;
    private volatile String guide;

    public CalculationSourceAssist(ObjectProvider<OrganizationGuard> guard, ObjectProvider<EntityCatalogPort> catalog,
                                   SourceChecker checker) {
        this.guard = guard;
        this.catalog = catalog;
        this.checker = checker;
    }

    @Override
    public String task() {
        return TASK;
    }

    @Override
    public String guide() {
        if (guide == null) {
            try (var in = new ClassPathResource(GUIDE).getInputStream()) {
                guide = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("The calculation source guide is missing from the classpath", e);
            }
        }
        return guide;
    }

    @Override
    public void checkAccess(String orgKey) {
        guard.getObject().requireDesign(orgKey);
    }

    /** @throws IllegalArgumentException for a parameter, which this task does not take */
    @Override
    public String context(String orgKey, AssistRequest request) {
        refuseParameters(request);
        return CatalogDescription.of(catalog().all(orgKey));
    }

    @Override
    public List<AuthoringIssue> validate(String orgKey, AssistRequest request, String candidate) {
        return checker.check(orgKey, catalog(), request.current(), candidate);
    }

    private EntityCatalogPort catalog() {
        return catalog.getIfAvailable(() -> EntityCatalogPort.UNKNOWN);
    }

    private static void refuseParameters(AssistRequest request) {
        request.parameters().keySet().stream().findFirst().ifPresent(key -> {
            throw new IllegalArgumentException("The task '%s' takes no parameter '%s'".formatted(TASK, key));
        });
    }
}
