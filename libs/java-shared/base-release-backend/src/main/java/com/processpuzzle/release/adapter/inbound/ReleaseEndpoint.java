package com.processpuzzle.release.adapter.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.release.api.BaseReleaseApi;
import com.processpuzzle.release.model.PendingRelease;
import com.processpuzzle.release.model.Release;
import com.processpuzzle.release.model.ReleaseRequest;
import com.processpuzzle.release.usecase.DescribePendingRelease;
import com.processpuzzle.release.usecase.ListReleases;
import com.processpuzzle.release.usecase.ReleaseOrganization;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
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
    private final ReleaseMapper mapper;
    private final HttpServletRequest request;

    public ReleaseEndpoint(DescribePendingRelease describePendingRelease, ReleaseOrganization releaseOrganization,
                           ListReleases listReleases, ReleaseMapper mapper, HttpServletRequest request) {
        this.describePendingRelease = describePendingRelease;
        this.releaseOrganization = releaseOrganization;
        this.listReleases = listReleases;
        this.mapper = mapper;
        this.request = request;
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

    private String principalName() {
        Principal principal = request.getUserPrincipal();
        return principal == null ? null : principal.getName();
    }
}
