package com.processpuzzle.starter.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.processpuzzle.starter.model.InstallStarterRequest;
import com.processpuzzle.starter.registry.StarterCatalog;
import com.processpuzzle.starter.usecase.BrowseCatalog;
import com.processpuzzle.starter.usecase.ImportBundle;
import com.processpuzzle.starter.usecase.ImportReport;
import com.processpuzzle.starter.usecase.InstallStarter;
import com.processpuzzle.starter.usecase.ListInstalledStarters;
import com.processpuzzle.starter.usecase.StarterNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

class StarterEndpointTest {

    private final BrowseCatalog catalog = mock(BrowseCatalog.class);
    private final InstallStarter installer = mock(InstallStarter.class);
    private final ImportBundle importer = mock(ImportBundle.class);
    private final ListInstalledStarters installed = mock(ListInstalledStarters.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final StarterMapper mapper = new StarterMapper();
    private final com.processpuzzle.starter.usecase.UpgradeStarter upgrader = mock(com.processpuzzle.starter.usecase.UpgradeStarter.class);
    private final StarterEndpoint endpoint = new StarterEndpoint(catalog, installer, importer, installed, upgrader, mapper, request);

    @Test
    void listsAndFindsCatalogStarters() {
        StarterCatalog.Starter starter = new StarterCatalog.Starter("inventory", "Inventory", null, null, null,
                null, null, null, null);
        when(catalog.list()).thenReturn(List.of(starter));
        when(catalog.get("inventory")).thenReturn(Optional.of(starter));

        var list = endpoint.listStarters();
        var detail = endpoint.getStarter("inventory");

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).containsExactly(mapper.toModel(starter));
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).isEqualTo(mapper.toModel(starter));
    }

    @Test
    void anUnknownStarterIsNotFound() {
        when(catalog.get("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> endpoint.getStarter("missing"))
                .isInstanceOf(StarterNotFoundException.class).hasMessageContaining("no starter 'missing'");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {true, false})
    void installsTheRequestedVersionWithThePrincipalAndDryRunFlag(Boolean dryRun) {
        when(request.getUserPrincipal()).thenReturn(() -> "alice");
        boolean expectedDryRun = Boolean.TRUE.equals(dryRun);
        ImportReport report = report(expectedDryRun);
        when(installer.execute("acme", "inventory", "1.0.0", expectedDryRun, "alice")).thenReturn(report);
        InstallStarterRequest command = new InstallStarterRequest().starterId("inventory").version("1.0.0").dryRun(dryRun);

        var response = endpoint.installStarter("acme", command);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mapper.toModel(report));
        verify(installer).execute("acme", "inventory", "1.0.0", expectedDryRun, "alice");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {true, false})
    void importsAnUploadWithoutAPrincipalAndClosesItsStream(Boolean dryRun) throws IOException {
        MultipartFile upload = mock(MultipartFile.class);
        InputStream input = mock(InputStream.class);
        when(upload.getInputStream()).thenReturn(input);
        boolean expectedDryRun = Boolean.TRUE.equals(dryRun);
        ImportReport report = report(expectedDryRun);
        when(importer.execute("acme", input, expectedDryRun, null)).thenReturn(report);

        var response = endpoint.importBundle("acme", upload, dryRun);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mapper.toModel(report));
        verify(importer).execute("acme", input, expectedDryRun, null);
        verify(input).close();
    }

    @Test
    void anUnreadableUploadFailsExplicitly() throws IOException {
        MultipartFile upload = mock(MultipartFile.class);
        IOException failure = new IOException("Upload unavailable");
        when(upload.getInputStream()).thenThrow(failure);

        assertThatThrownBy(() -> endpoint.importBundle("acme", upload, false))
                .isInstanceOf(UncheckedIOException.class).hasCause(failure)
                .hasMessageContaining("Unable to read the uploaded bundle");
        verifyNoInteractions(importer);
    }

    @Test
    void closesTheUploadWhenTheImporterRefusesIt() throws IOException {
        MultipartFile upload = mock(MultipartFile.class);
        InputStream input = mock(InputStream.class);
        when(upload.getInputStream()).thenReturn(input);
        IllegalStateException failure = new IllegalStateException("Import failed");
        when(importer.execute("acme", input, false, null)).thenThrow(failure);

        assertThatThrownBy(() -> endpoint.importBundle("acme", upload, false)).isSameAs(failure);
        verify(input).close();
    }

    @Test
    void listsTheOrganizationsInstalledStarters() {
        ListInstalledStarters.View view = new ListInstalledStarters.View("inventory", "1.0.0",
                Instant.parse("2026-10-06T10:00:00Z"), "alice", 2, null, false);
        when(installed.execute("acme")).thenReturn(List.of(view));

        var response = endpoint.listInstalledStarters("acme");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(mapper.toModel(view));
        verify(installed).execute("acme");
    }

    private static ImportReport report(boolean dryRun) {
        return new ImportReport(dryRun, dryRun ? ImportReport.Status.WOULD_APPLY : ImportReport.Status.APPLIED,
                "inventory", "1.0.0", List.of(), List.of());
    }

    @Test
    void anAppliedUpgradeIs200_andARefusedOneIs422WithTheReport() {
        var applied = new com.processpuzzle.starter.usecase.UpgradeReport(false, com.processpuzzle.starter.usecase.UpgradeReport.Status.APPLIED,
                "inventory", "1.0.0", "2.0.0", List.of(new com.processpuzzle.starter.usecase.UpgradeReport.Item("entity", "item",
                com.processpuzzle.starter.usecase.UpgradeReport.Action.KEEP_CUSTOMIZED, "changed")), List.of("rule"), List.of());
        when(upgrader.execute("acme", "inventory", "2.0.0", false, null)).thenReturn(applied);
        when(upgrader.execute("acme", "inventory", null, true, null)).thenReturn(com.processpuzzle.starter.usecase.UpgradeReport.rejected(
                true, "inventory", "1.0.0", null, List.of(new com.processpuzzle.starter.usecase.ImportReport.Error(null, "no newer version"))));

        var ok = endpoint.upgradeStarter("acme", "inventory", new com.processpuzzle.starter.model.UpgradeRequest().version("2.0.0"));
        var refused = endpoint.upgradeStarter("acme", "inventory", new com.processpuzzle.starter.model.UpgradeRequest().dryRun(true));

        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getAction()).isEqualTo(com.processpuzzle.starter.model.UpgradeReportItem.ActionEnum.KEEP_CUSTOMIZED);
            assertThat(item.getNote()).isEqualTo("changed");
        });
        assertThat(ok.getBody().getSkippedKinds()).containsExactly("rule");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody().getErrors()).singleElement().satisfies(error -> assertThat(error.getMessage()).isEqualTo("no newer version"));
    }

    @Test
    void anUpgradeWithoutABodyAsksForTheNextOfferedVersion() {
        when(upgrader.execute("acme", "inventory", null, false, null)).thenReturn(com.processpuzzle.starter.usecase.UpgradeReport.rejected(
                false, "inventory", "1.0.0", null, List.of()));

        endpoint.upgradeStarter("acme", "inventory", null);

        org.mockito.Mockito.verify(upgrader).execute("acme", "inventory", null, false, null);
    }
}
