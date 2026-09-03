package com.processpuzzle.app.domain;

import com.processpuzzle.app.domain.RouteTarget;
import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.adapter.inbound.AppMapper;
import com.processpuzzle.app.usecase.FindAllAppDefinitions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the mapping actually binds before anything is built on top of it: the composite
 * {@code @IdClass}, the {@code @Convert} + long-text JSON column, and Specification queries.
 * A converter composed with an explicit JDBC type has a history of breaking across Hibernate
 * releases, so this is deliberately the first test in the feature.
 *
 * <p>Note what this test cannot tell you, and did not: it runs on H2, and the column was
 * {@code @Lob} until the runtime datasource became PostgreSQL — where {@code @Lob} on a
 * {@code String} means an {@code oid} large-object reference rather than {@code text}, writes fine
 * and fails on read. H2 mapped the same annotation to a CLOB, so the round-trip below passed either
 * way. The columns are now {@code @JdbcTypeCode(SqlTypes.LONG32VARCHAR)}, checked against real
 * PostgreSQL; only a test against PostgreSQL could keep them that way.
 */
/*
 * Both scans used to name platformadmin.domain as well, because base-app read Organization directly:
 * GetAppLayout resolved a tenant's default locale from its repository and AppMapper rendered an
 * organization projection. It reads neither now — the locale arrives through the TenantDirectory
 * port and the projection is platform-admin's own — so this module's schema is its own again, which
 * is the point. An Organization round-trip belongs in platform-admin's OrganizationTest.
 */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.app.domain")
@EnableJpaRepositories("com.processpuzzle.app.domain")
class AppDefinitionPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private AppDefinitionRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
    }

    @Test
    void fullGraph_roundTripsThroughTheJsonColumn() {
        repository.saveAndFlush(new AppDefinition("my-org", "claims-app", "Claims Management",
                "claims.app.name", "Claims handling", fullGraph()));

        AppGraph reloaded = repository.findById(new AppDefinitionKey("my-org", "claims-app"))
                .orElseThrow()
                .getDraftGraph();

        assertThat(reloaded.theme().materialTheme()).isEqualTo("azure-blue");
        assertThat(reloaded.theme().tokenOverrides()).containsEntry("--pp-surface-sidenav", "#0d1b2a");
        assertThat(reloaded.layout().preset()).isEqualTo("sidenav-left");

        Region sidenav = reloaded.regions().getFirst();
        assertThat(sidenav.type()).isEqualTo("sidenav");
        NavNode group = sidenav.navItems().getFirst();
        assertThat(group.routePath()).isNull();
        assertThat(group.children()).hasSize(1);
        assertThat(group.children().getFirst().roles()).containsExactly("CLAIMS_ADJUSTER");

        AppRoute route = reloaded.findRoute("claims-list");
        assertThat(route).isNotNull();
        Widget container = route.target().widgets().getFirst();
        assertThat(container.props()).containsEntry("childIds", List.of("widget-claims-grid"));
        Widget grid = route.target().widgets().getLast();
        assertThat(grid.type()).isEqualTo("entity-grid");
        assertThat(grid.placement()).isEqualTo(WidgetPlacement.REFERENCED);
        assertThat(grid.props()).containsEntry("entityName", "Claim");
        assertThat(grid.props().get("columns")).isEqualTo(List.of("claimNumber", "claimant"));
    }

    @Test
    void sameAppId_coexistsInDifferentOrganizations() {
        repository.saveAndFlush(new AppDefinition("org-a", "claims-app", "A", null, null, AppGraph.empty()));
        repository.saveAndFlush(new AppDefinition("org-b", "claims-app", "B", null, null, AppGraph.empty()));

        assertThat(repository.findByOrgKeyAndId("org-a", "claims-app")).map(AppDefinition::getName).contains("A");
        assertThat(repository.findByOrgKeyAndId("org-b", "claims-app")).map(AppDefinition::getName).contains("B");
        assertThat(repository.existsByOrgKeyAndId("org-a", "claims-app")).isTrue();
        assertThat(repository.existsByOrgKeyAndId("org-c", "claims-app")).isFalse();
    }

    @Test
    void specificationQueriesResolveFlatIdFields() {
        repository.saveAndFlush(new AppDefinition("org-a", "claims-app", "Claims", null, null, AppGraph.empty()));
        repository.saveAndFlush(new AppDefinition("org-a", "billing-app", "Billing", null, null, AppGraph.empty()));
        repository.saveAndFlush(new AppDefinition("org-b", "claims-app", "Other", null, null, AppGraph.empty()));

        Specification<AppDefinition> byOrg = (root, query, cb) -> cb.equal(root.get("orgKey"), "org-a");
        assertThat(repository.findAll(byOrg)).extracting(AppDefinition::getId)
                .containsExactlyInAnyOrder("claims-app", "billing-app");

        Specification<AppDefinition> byId = (root, query, cb) -> cb.equal(root.get("id"), "claims-app");
        assertThat(repository.findAll(byOrg.and(byId))).hasSize(1);
    }

    @Test
    void nullPublishedGraph_survivesReload() {
        repository.saveAndFlush(new AppDefinition("my-org", "fresh-app", "Fresh", null, null, AppGraph.empty()));

        AppDefinition reloaded = repository.findByOrgKeyAndId("my-org", "fresh-app").orElseThrow();

        assertThat(reloaded.getPublishedGraph()).isNull();
        assertThat(reloaded.hasPublishedRevision()).isFalse();
        assertThat(reloaded.isPublished()).isFalse();
        assertThat(reloaded.getRevision()).isEqualTo(1L);
        assertThat(reloaded.getDraftGraph().regions()).isEmpty();
    }

    @Test
    void publishThenEdit_keepsBothSnapshotsDistinct() {
        repository.saveAndFlush(new AppDefinition("my-org", "claims-app", "Claims", null, null, fullGraph()));

        AppDefinition saved = repository.findByOrgKeyAndId("my-org", "claims-app").orElseThrow();
        saved.publish();
        repository.saveAndFlush(saved);
        saved.replaceDraft("Claims", null, null, AppGraph.empty());
        repository.saveAndFlush(saved);

        AppDefinition reloaded = repository.findByOrgKeyAndId("my-org", "claims-app").orElseThrow();
        assertThat(reloaded.getRevision()).isEqualTo(2L);
        assertThat(reloaded.getPublishedRevision()).isEqualTo(1L);
        assertThat(reloaded.isPublished()).isFalse();
        assertThat(reloaded.getDraftGraph().regions()).isEmpty();
        assertThat(reloaded.getPublishedGraph().regions()).hasSize(2);
        assertThat(reloaded.getPublishedGraph().findRoute("claims-list")).isNotNull();
    }

    @Test
    void deleteByOrgKey_removesOnlyThatOrganizationsDefinitions() {
        repository.saveAndFlush(new AppDefinition("org-a", "app-1", "A1", null, null, AppGraph.empty()));
        repository.saveAndFlush(new AppDefinition("org-a", "app-2", "A2", null, null, AppGraph.empty()));
        repository.saveAndFlush(new AppDefinition("org-b", "app-1", "B1", null, null, AppGraph.empty()));

        repository.deleteByOrgKey("org-a");
        repository.flush();

        assertThat(repository.findByOrgKey("org-a")).isEmpty();
        assertThat(repository.findByOrgKey("org-b")).hasSize(1);
    }

    /**
     * The tenant filter is a {@link Specification} lambda, so it is only really exercised against a
     * database — a mocked repository would accept one that filtered on the wrong attribute.
     */
    @Test
    void listingIsScopedToItsOrganizationByTheSpecificationItAlwaysApplies() {
        repository.saveAndFlush(new AppDefinition("org-a", "claims-app", "Claims", null, null, AppGraph.empty()));
        repository.saveAndFlush(new AppDefinition("org-a", "billing-app", "Billing", null, null, AppGraph.empty()));
        repository.saveAndFlush(new AppDefinition("org-b", "claims-app", "Other", null, null, AppGraph.empty()));
        FindAllAppDefinitions findAll =
                new FindAllAppDefinitions(repository, AppTestFixtures.permissiveGuard());

        assertThat(findAll.execute("org-a", null, "id,asc", null, null))
                .extracting(AppDefinition::getId).containsExactly("billing-app", "claims-app");
        assertThat(findAll.execute("org-a", "id==claims-app", null, null, null))
                .extracting(AppDefinition::getId).containsExactly("claims-app");
        assertThat(findAll.execute("org-c", null, null, null, null)).isEmpty();
    }

    /** The contract's timestamps are UTC offsets, and only a persisted entity has any to render. */
    @Test
    void persistedTimestampsMapOntoTheContractAsUtcOffsets() {
        repository.saveAndFlush(new AppDefinition("my-org", "claims-app", "Claims", null, null, fullGraph()));
        AppMapper mapper = new AppMapper();

        AppDefinition definition = repository.findByOrgKeyAndId("my-org", "claims-app").orElseThrow();
        com.processpuzzle.app.model.AppDefinition model = mapper.toModel(definition);

        assertThat(model.getCreatedAt()).isNotNull()
                .satisfies(stamp -> assertThat(stamp.getOffset()).isEqualTo(ZoneOffset.UTC));
        assertThat(model.getCreatedAt().toInstant()).isEqualTo(definition.getCreatedAt());
        assertThat(model.getUpdatedAt()).isNotNull();
    }

    private static AppGraph fullGraph() {
        Widget grid = new Widget("widget-claims-grid", "entity-grid",
                Map.of("entityName", "Claim", "columns", List.of("claimNumber", "claimant")),
                WidgetPlacement.REFERENCED);
        Widget tabs = new Widget("widget-tabs", "tab-group",
                Map.of("childIds", List.of("widget-claims-grid")), WidgetPlacement.STANDALONE);
        AppRoute route = new AppRoute("claims-list", "Claims", "claims.route.list.title", null, List.of(), RouteTarget.ofWidgets(List.of(tabs, grid)));

        NavNode leaf = new NavNode("nav-claims-new", "New Claim", null, "add_circle",
                "claims-list", List.of("CLAIMS_ADJUSTER"), List.of());
        NavNode group = new NavNode("nav-group", "Claims", null, "description", null, List.of(), List.of(leaf));
        Region sidenav = new Region("sidenav", List.of(group), List.of());
        Region content = new Region("content", List.of(), List.of());

        Theme theme = new Theme("azure-blue", "light",
                Map.of("--pp-surface-sidenav", "#0d1b2a"), "/logo.png", null);
        Layout layout = new Layout("sidenav-left", "side", Boolean.TRUE, Boolean.TRUE, "1280px");

        return new AppGraph(theme, layout, List.of(sidenav, content), List.of(route), List.of());
    }
}
