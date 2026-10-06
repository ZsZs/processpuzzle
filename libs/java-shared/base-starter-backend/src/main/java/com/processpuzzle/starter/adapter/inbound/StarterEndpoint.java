package com.processpuzzle.starter.adapter.inbound;

import com.processpuzzle.core.logging.LogClass;
import com.processpuzzle.starter.api.BaseStarterApi;
import com.processpuzzle.starter.model.CatalogStarter;
import com.processpuzzle.starter.model.ImportReport;
import com.processpuzzle.starter.model.InstallStarterRequest;
import com.processpuzzle.starter.model.InstalledStarter;
import com.processpuzzle.starter.usecase.BrowseCatalog;
import com.processpuzzle.starter.usecase.ImportBundle;
import com.processpuzzle.starter.usecase.InstallStarter;
import com.processpuzzle.starter.usecase.ListInstalledStarters;
import com.processpuzzle.starter.usecase.StarterNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Inbound REST adapter of base-starter-api.yaml. Thin: the work is in the use cases. */
@RestController
@LogClass
public class StarterEndpoint implements BaseStarterApi {

    private final BrowseCatalog browseCatalog;
    private final InstallStarter installStarter;
    private final ImportBundle importBundle;
    private final ListInstalledStarters listInstalledStarters;
    private final StarterMapper mapper;
    private final HttpServletRequest request;

    public StarterEndpoint(BrowseCatalog browseCatalog, InstallStarter installStarter, ImportBundle importBundle,
                           ListInstalledStarters listInstalledStarters, StarterMapper mapper, HttpServletRequest request) {
        this.browseCatalog = browseCatalog;
        this.installStarter = installStarter;
        this.importBundle = importBundle;
        this.listInstalledStarters = listInstalledStarters;
        this.mapper = mapper;
        this.request = request;
    }

    @Override
    public ResponseEntity<List<CatalogStarter>> listStarters() {
        return ResponseEntity.ok(browseCatalog.list().stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<CatalogStarter> getStarter(String starterId) {
        return ResponseEntity.ok(mapper.toModel(browseCatalog.get(starterId)
                .orElseThrow(() -> new StarterNotFoundException("The catalog has no starter '" + starterId + "'."))));
    }

    @Override
    public ResponseEntity<ImportReport> installStarter(String orgKey, InstallStarterRequest installStarterRequest) {
        return ResponseEntity.ok(mapper.toModel(installStarter.execute(orgKey, installStarterRequest.getStarterId(),
                installStarterRequest.getVersion(), Boolean.TRUE.equals(installStarterRequest.getDryRun()), principalName())));
    }

    @Override
    public ResponseEntity<ImportReport> importBundle(String orgKey, MultipartFile bundle, Boolean dryRun) {
        try (InputStream input = bundle.getInputStream()) {
            return ResponseEntity.ok(mapper.toModel(
                    importBundle.execute(orgKey, input, Boolean.TRUE.equals(dryRun), principalName())));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read the uploaded bundle", e);
        }
    }

    @Override
    public ResponseEntity<List<InstalledStarter>> listInstalledStarters(String orgKey) {
        return ResponseEntity.ok(listInstalledStarters.execute(orgKey).stream().map(mapper::toModel).toList());
    }

    private String principalName() {
        Principal principal = request.getUserPrincipal();
        return principal == null ? null : principal.getName();
    }
}
