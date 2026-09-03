package com.processpuzzle.app.usecase;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.Layout;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.domain.Theme;
import com.processpuzzle.app.domain.Widget;
import com.processpuzzle.app.domain.WidgetPlacement;
import com.processpuzzle.app.domain.RouteTarget;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationAccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.processpuzzle.app.AppTestFixtures.APP_ID;
import static com.processpuzzle.app.AppTestFixtures.ORG_KEY;
import static com.processpuzzle.app.AppTestFixtures.ROUTE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Export's contract is that its output is importable — into this tenant or any other. The
 * round-trip test is therefore the primary one: it fails both when export starts emitting
 * server-assigned fields and when it stops emitting something import needs.
 */
class ExportAppDefinitionTest {

    private AppDefinitionRepository repository;
    private ExportAppDefinition exportAppDefinition;

    @BeforeEach
    void setUp() {
        repository = mock(AppDefinitionRepository.class);
        exportAppDefinition = new ExportAppDefinition(repository, AppTestFixtures.permissiveGuard(),
                new AppMapper());
    }

    @Test
    void writesTheDraftRevisionAsAYamlDocument() throws IOException {
        when(repository.findByOrgKeyAndId(ORG_KEY, APP_ID)).thenReturn(Optional.of(richDefinition()));

        String yaml = new String(exportAppDefinition.execute(ORG_KEY, APP_ID), StandardCharsets.UTF_8);

        assertThat(yaml).contains("appDefinitions", APP_ID, "Claims Management", "rose-red",
                "sidenav-left", "sidenav", ROUTE_PATH, "entityName", "Claim");
    }

    /** Server-assigned fields would either be ignored on import or, worse, seed the wrong tenant. */
    @Test
    void omitsEverythingTheServerAssigns() throws IOException {
        AppDefinition definition = richDefinition();
        definition.publish();
        when(repository.findByOrgKeyAndId(ORG_KEY, APP_ID)).thenReturn(Optional.of(definition));

        String yaml = new String(exportAppDefinition.execute(ORG_KEY, APP_ID), StandardCharsets.UTF_8);

        assertThat(yaml).doesNotContain("orgKey", "status", "version", "publishedVersion",
                "createdAt", "updatedAt");
    }

    @Test
    void theExportedFileImportsBackUnchanged() throws IOException {
        when(repository.findByOrgKeyAndId(ORG_KEY, APP_ID)).thenReturn(Optional.of(richDefinition()));
        byte[] exported = exportAppDefinition.execute(ORG_KEY, APP_ID);

        AppDefinitionRepository target = mock(AppDefinitionRepository.class);
        when(target.save(any(AppDefinition.class))).thenAnswer(call -> call.getArgument(0));
        when(target.findByOrgKeyAndId(anyString(), anyString())).thenReturn(Optional.empty());
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        when(organizations.existsById(anyString())).thenReturn(true);
        ImportAppDefinitions into = new ImportAppDefinitions(target, organizations,
                AppTestFixtures.structuralValidator(), AppTestFixtures.permissiveGuard(), new AppMapper());

        ImportOutcome outcome = into.execute("another-org", new ByteArrayInputStream(exported));

        assertThat(outcome.errors()).isEmpty();
        assertThat(outcome.created()).isEqualTo(1);

        ArgumentCaptor<AppDefinition> imported = ArgumentCaptor.forClass(AppDefinition.class);
        verify(target).save(imported.capture());
        assertThat(imported.getValue().getOrgKey()).isEqualTo("another-org");
        assertThat(imported.getValue().getDraftGraph()).isEqualTo(richDefinition().getDraftGraph());
    }

    @Test
    void unknownDefinition_is404() {
        when(repository.findByOrgKeyAndId(ORG_KEY, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exportAppDefinition.execute(ORG_KEY, "nope"))
                .isInstanceOf(AppDefinitionNotFoundException.class)
                .hasMessageContaining(ORG_KEY + "/nope");
    }

    @Test
    void aPrincipalWithoutDesignRights_isRejectedBeforeAnythingIsRead() {
        ExportAppDefinition guarded = new ExportAppDefinition(repository, AppTestFixtures.denyingGuard(),
                new AppMapper());

        assertThatThrownBy(() -> guarded.execute(ORG_KEY, APP_ID))
                .isInstanceOf(OrganizationAccessDeniedException.class);

        verifyNoInteractions(repository);
    }

    /** Every optional part of the graph populated, so the round-trip has something to lose. */
    private static AppDefinition richDefinition() {
        Widget grid = new Widget("widget-grid", "entity-grid", Map.of("entityName", "Claim"), WidgetPlacement.STANDALONE);
        AppRoute route = new AppRoute(ROUTE_PATH, "Claims", "claims.route.list", null, List.of(), RouteTarget.ofWidgets(List.of(grid)));
        NavNode nav = new NavNode(AppTestFixtures.NAV_ID, "Claims", "claims.nav", "list_alt", ROUTE_PATH,
                List.of("CLAIMS_ADJUSTER"), List.of());
        AppGraph graph = new AppGraph(
                new Theme("rose-red", "dark", Map.of("--pp-surface-sidenav", "#0d1b2a"), null, null),
                new Layout("sidenav-left", "side", true, true, "1280px"),
                List.of(new Region("sidenav", List.of(nav), List.of())),
                List.of(route), List.of());
        return new AppDefinition(ORG_KEY, APP_ID, "Claims Management", "claims.app.name", "Handles claims.",
                graph);
    }
}
