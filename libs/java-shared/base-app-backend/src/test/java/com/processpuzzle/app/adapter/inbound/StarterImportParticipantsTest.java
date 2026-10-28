package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppAssetRepository;
import com.processpuzzle.app.domain.AppDefinitionTranslationRepository;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.usecase.DeleteAppDefinition;
import com.processpuzzle.app.usecase.DeleteModuleDefinition;
import com.processpuzzle.app.usecase.ImportAppDefinitions;
import com.processpuzzle.app.usecase.ImportModuleDefinitions;
import com.processpuzzle.app.usecase.PublishAppDefinition;
import com.processpuzzle.app.usecase.service.AppAssetReleases;
import com.processpuzzle.app.usecase.service.AppTranslationReleases;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.ImportedItem;
import com.processpuzzle.core.definition.ParticipantResult;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The module and app participants over real repositories and the real use cases, so that what is asserted is
 * the outcome a starter install has: modules released, the app published, one application per organization.
 */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.app.domain")
@EnableJpaRepositories("com.processpuzzle.app.domain")
class StarterImportParticipantsTest {

    private static final String ORG = "my-org";
    private static final String PLACEHOLDER = StarterAppCreator.STARTER_APP_ID;

    private static final String MODULES = """
            moduleDefinitions:
              - key: plan-race
                name: Plan Race
                routes:
                  - path: overview
                    title: Overview
                    target:
                      kind: WIDGETS
                      widgets:
                        - id: w1
                          type: markdown-page
            """;

    private static final String APP = """
            appDefinitions:
              - id: %s
                name: Sail Race Control
                modules:
                  - moduleKey: plan-race
                    basePath: plan-race
                routes:
                  - path: home
                    title: Home
                    target:
                      kind: WIDGETS
                      widgets:
                        - id: cards
                          type: cards-grid
            """;

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private ModuleDefinitionRepository moduleRepository;
    @Autowired
    private ModuleTranslationRepository translationRepository;
    @Autowired
    private ModuleAssetRepository assetRepository;
    @Autowired
    private AppDefinitionRepository appRepository;
    @Autowired
    private AppAssetRepository appAssetRepository;
    @Autowired
    private AppDefinitionTranslationRepository appTranslationRepository;
    @Autowired
    private EntityManager entityManager;

    private ModuleImportParticipant modules;
    private AppImportParticipant apps;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ModuleAssetSweeper sweeper = mock(ModuleAssetSweeper.class);
        OrganizationGuard guard = AppTestFixtures.permissiveGuard();
        AppDefinitionValidator validator = AppTestFixtures.structuralValidator();
        AppMapper mapper = new AppMapper();
        ObjectProvider<com.processpuzzle.app.usecase.port.TenantDirectory> tenants = mock(ObjectProvider.class);
        when(tenants.getIfAvailable()).thenReturn(null);
        AppAssetReleases appAssets = new AppAssetReleases(appAssetRepository);
        AppTranslationReleases appTexts = new AppTranslationReleases(appTranslationRepository);

        ModuleReleaseParticipant release = new ModuleReleaseParticipant(moduleRepository, translationRepository, assetRepository, sweeper);
        modules = new ModuleImportParticipant(new ImportModuleDefinitions(moduleRepository, tenants, validator, guard, mapper),
                new DeleteModuleDefinition(moduleRepository, translationRepository, assetRepository, sweeper, guard),
                moduleRepository, release);
        apps = new AppImportParticipant(new ImportAppDefinitions(appRepository, tenants, validator, guard, mapper),
                new PublishAppDefinition(appRepository, validator, appAssets, appTexts, sweeper, guard, mapper),
                new DeleteAppDefinition(appRepository, appAssets, appTexts, sweeper, guard), appRepository);
    }

    @Test
    void runInTheOrderOfTheirKinds() {
        assertThat(modules.kind()).isEqualTo(DefinitionKinds.MODULE);
        assertThat(modules.order()).isEqualTo(DefinitionKinds.MODULE_ORDER);
        assertThat(apps.kind()).isEqualTo(DefinitionKinds.APP);
        assertThat(apps.order()).isEqualTo(DefinitionKinds.APP_ORDER);
        assertThat(modules.order()).isLessThan(apps.order());
    }

    @Test
    void importedModulesAreReleasedAtOnce() {
        ParticipantResult result = modules.apply(ORG, "modules/plan.yaml", bytes(MODULES));

        assertThat(result.isRejected()).isFalse();
        assertThat(result.items()).containsExactly(new ImportedItem("plan-race", ImportedItem.Action.CREATE));
        assertThat(moduleRepository.findByOrgKeyAndKeyAndChannel(ORG, "plan-race", Channel.DRAFT)).isPresent();
        assertThat(moduleRepository.findByOrgKeyAndKeyAndChannel(ORG, "plan-race", Channel.RELEASED)).isPresent();
    }

    @Test
    void aModuleAlreadyThereIsAnUpdateAndItsReleasedRowFollows() {
        modules.apply(ORG, "modules/plan.yaml", bytes(MODULES));
        flushAndClear();

        ParticipantResult result = modules.apply(ORG, "modules/plan.yaml", bytes(MODULES.replace("Plan Race", "Plan a Race")));
        flushAndClear();

        assertThat(result.items()).containsExactly(new ImportedItem("plan-race", ImportedItem.Action.UPDATE));
        assertThat(moduleRepository.findByOrgKeyAndKeyAndChannel(ORG, "plan-race", Channel.RELEASED))
                .get().extracting(ModuleDefinition::getName).isEqualTo("Plan a Race");
    }

    @Test
    void anInvalidModuleIsRefusedWithTheFileNamedAndNothingIsWritten() {
        ParticipantResult result = modules.apply(ORG, "modules/bad.yaml", bytes("""
                moduleDefinitions:
                  - key: bad
                """));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.errors()).isNotEmpty().allMatch(error -> error.startsWith("modules/bad.yaml: "));
        assertThat(moduleRepository.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).isEmpty();
    }

    @Test
    void anUnreadableFileIsRefused() {
        assertThat(modules.apply(ORG, "modules/x.yaml", bytes("moduleDefinitions: [")).isRejected()).isTrue();
        assertThat(apps.apply(ORG, "apps/x.yaml", bytes("appDefinitions: [")).isRejected()).isTrue();
    }

    @Test
    void removingAllModulesRemovesTheReleasedRowsToo() {
        modules.apply(ORG, "modules/plan.yaml", bytes(MODULES));
        flushAndClear();

        List<String> removed = modules.removeAll(ORG);
        flushAndClear();

        assertThat(removed).containsExactly("plan-race");
        assertThat(moduleRepository.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).isEmpty();
        assertThat(moduleRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).isEmpty();
    }

    @Test
    void aModuleCanBeImportedAgainAfterRemovingAll() {
        modules.apply(ORG, "modules/plan.yaml", bytes(MODULES));
        flushAndClear();

        modules.removeAll(ORG);
        ParticipantResult result = modules.apply(ORG, "modules/plan.yaml", bytes(MODULES));
        flushAndClear();

        assertThat(result.isRejected()).isFalse();
        assertThat(moduleRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).hasSize(1);
    }

    @Test
    void anAppWithThePlaceholdersIdReplacesItAndIsPublished() {
        placeholder();
        modules.apply(ORG, "modules/plan.yaml", bytes(MODULES));

        ParticipantResult result = apps.apply(ORG, "apps/home.yaml", bytes(APP.formatted(PLACEHOLDER)));
        flushAndClear();

        assertThat(result.isRejected()).isFalse();
        assertThat(result.items()).containsExactly(new ImportedItem(PLACEHOLDER, ImportedItem.Action.UPDATE));
        AppDefinition app = appRepository.findByOrgKeyAndId(ORG, PLACEHOLDER).orElseThrow();
        assertThat(app.getName()).isEqualTo("Sail Race Control");
        assertThat(app.isPublished()).isTrue();
        assertThat(app.getPublishedGraph().routes()).extracting(route -> route.path()).containsExactly("home");
        assertThat(appRepository.findByOrgKey(ORG)).hasSize(1);
    }

    @Test
    void anAppWithAnotherIdRemovesTheUntouchedPlaceholder() {
        placeholder();

        ParticipantResult result = apps.apply(ORG, "apps/home.yaml", bytes(APP.formatted("sail-rc")));
        flushAndClear();

        assertThat(result.items()).containsExactly(new ImportedItem("sail-rc", ImportedItem.Action.CREATE));
        assertThat(appRepository.findByOrgKey(ORG)).extracting(AppDefinition::getId).containsExactly("sail-rc");
        assertThat(appRepository.findByOrgKeyAndId(ORG, "sail-rc").orElseThrow().isPublished()).isTrue();
    }

    @Test
    void aPlaceholderADesignerHasEditedStays() {
        AppDefinition edited = placeholder();
        edited.replaceDraft("Mine", null, null, AppGraph.empty());
        appRepository.save(edited);

        apps.apply(ORG, "apps/home.yaml", bytes(APP.formatted("sail-rc")));
        flushAndClear();

        assertThat(appRepository.findByOrgKey(ORG)).extracting(AppDefinition::getId).containsExactlyInAnyOrder(PLACEHOLDER, "sail-rc");
    }

    @Test
    void anInvalidAppIsRefusedWithTheProblemNamed() {
        placeholder();

        ParticipantResult result = apps.apply(ORG, "apps/bad.yaml", bytes("""
                appDefinitions:
                  - id: bad
                """));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.errors()).isNotEmpty().allMatch(error -> error.startsWith("apps/bad.yaml: "));
    }

    @Test
    void removingAllAppsLeavesAFreshEmptyPlaceholderSoTheOrganizationKeepsOne() {
        placeholder();
        apps.apply(ORG, "apps/home.yaml", bytes(APP.formatted("sail-rc")));
        flushAndClear();

        List<String> removed = apps.removeAll(ORG);
        flushAndClear();

        assertThat(removed).containsExactly("sail-rc");
        AppDefinition app = appRepository.findByOrgKeyAndId(ORG, PLACEHOLDER).orElseThrow();
        assertThat(appRepository.findByOrgKey(ORG)).hasSize(1);
        assertThat(app.getDraftGraph()).isEqualTo(AppGraph.empty());
        assertThat(app.hasPublishedRevision()).isFalse();
    }

    @Test
    void anUntouchedPlaceholderIsNotReportedAsRemovedAndAnOrganizationWithoutAppsGetsNone() {
        placeholder();
        flushAndClear();

        assertThat(apps.removeAll(ORG)).isEmpty();
        flushAndClear();
        assertThat(appRepository.findByOrgKey(ORG)).hasSize(1);

        appRepository.deleteByOrgKey(ORG);
        flushAndClear();
        assertThat(apps.removeAll(ORG)).isEmpty();
        assertThat(appRepository.findByOrgKey(ORG)).isEmpty();
    }

    @Test
    void anAppCanBeImportedAgainAfterRemovingAll() {
        placeholder();
        apps.apply(ORG, "apps/home.yaml", bytes(APP.formatted(PLACEHOLDER)));
        flushAndClear();

        apps.removeAll(ORG);
        ParticipantResult result = apps.apply(ORG, "apps/home.yaml", bytes(APP.formatted(PLACEHOLDER)));
        flushAndClear();

        assertThat(result.isRejected()).isFalse();
        assertThat(appRepository.findByOrgKeyAndId(ORG, PLACEHOLDER).orElseThrow().isPublished()).isTrue();
    }

    @Test
    void fingerprintsAreStableAndOnlyCoverTheAskedKeys() {
        placeholder();
        modules.apply(ORG, "modules/plan.yaml", bytes(MODULES));
        apps.apply(ORG, "apps/home.yaml", bytes(APP.formatted(PLACEHOLDER)));
        flushAndClear();

        Map<String, String> module = modules.fingerprints(ORG, Set.of("plan-race", "missing"));
        Map<String, String> app = apps.fingerprints(ORG, Set.of(PLACEHOLDER));

        assertThat(module).containsOnlyKeys("plan-race");
        assertThat(app).containsOnlyKeys(PLACEHOLDER);
        assertThat(modules.fingerprints(ORG, Set.of("plan-race"))).isEqualTo(module);
        assertThat(apps.fingerprints(ORG, Set.of(PLACEHOLDER))).isEqualTo(app);

        apps.apply(ORG, "apps/home.yaml", bytes(APP.formatted(PLACEHOLDER).replace("Sail Race Control", "Other")));
        flushAndClear();
        assertThat(apps.fingerprints(ORG, Set.of(PLACEHOLDER))).isNotEqualTo(app);
    }

    private AppDefinition placeholder() {
        return appRepository.saveAndFlush(new AppDefinition(ORG, PLACEHOLDER, "My Org", null, null, AppGraph.empty()));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static byte[] bytes(String yaml) {
        return yaml.getBytes(StandardCharsets.UTF_8);
    }
}
