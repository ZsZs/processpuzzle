package com.processpuzzle.release.adapter.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.release.api.BaseReleaseApi;
import com.processpuzzle.release.model.MigrationStep;
import com.processpuzzle.release.model.PendingRelease;
import com.processpuzzle.release.model.Release;
import com.processpuzzle.release.model.ReleaseRequest;
import com.processpuzzle.release.usecase.DescribePendingRelease;
import com.processpuzzle.release.usecase.ListReleases;
import com.processpuzzle.release.usecase.ManagePendingSteps;
import com.processpuzzle.release.usecase.ReleaseOrganization;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Inbound REST adapter of base-release-api.yaml. Thin: the work is in the use cases. */
@RestController
@LogClass
public class ReleaseEndpoint implements BaseReleaseApi {

    private final DescribePendingRelease describePendingRelease;
    private final ReleaseOrganization releaseOrganization;
    private final ListReleases listReleases;
    private final ManagePendingSteps managePendingSteps;
    private final ReleaseMapper mapper;
    private final CurrentPrincipal currentPrincipal;

    public ReleaseEndpoint(DescribePendingRelease describePendingRelease, ReleaseOrganization releaseOrganization,
                           ListReleases listReleases, ManagePendingSteps managePendingSteps, ReleaseMapper mapper,
                           CurrentPrincipal currentPrincipal) {
        this.describePendingRelease = describePendingRelease;
        this.releaseOrganization = releaseOrganization;
        this.listReleases = listReleases;
        this.managePendingSteps = managePendingSteps;
        this.mapper = mapper;
        this.currentPrincipal = currentPrincipal;
    }

    @Override
    public ResponseEntity<PendingRelease> getPendingRelease(String orgKey) {
        return ResponseEntity.ok(mapper.toModel(describePendingRelease.execute(orgKey)));
    }

    @Override
    public ResponseEntity<Release> releaseOrganization(String orgKey, ReleaseRequest releaseRequest) {
        boolean acknowledgeWarnings = releaseRequest != null && Boolean.TRUE.equals(releaseRequest.getAcknowledgeWarnings());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toModel(releaseOrganization.execute(orgKey, acknowledgeWarnings, principalName())));
    }

    @Override
    public ResponseEntity<List<Release>> listReleases(String orgKey) {
        return ResponseEntity.ok(listReleases.execute(orgKey).stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<List<MigrationStep>> listPendingSteps(String orgKey) {
        return ResponseEntity.ok(managePendingSteps.list(orgKey).stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<List<MigrationStep>> replacePendingSteps(String orgKey, List<MigrationStep> steps) {
        return ResponseEntity.ok(managePendingSteps.replace(orgKey, mapper.fromModel(steps)).stream()
                .map(mapper::toModel).toList());
    }

    /** Who released, as a person reads it — not the token's subject, which is a UUID. */
    private String principalName() {
        return currentPrincipal.displayName().orElse(null);
    }
}
