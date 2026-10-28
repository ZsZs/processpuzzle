package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppAssetRepository;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleTranslation;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.usecase.DeleteAppDefinition;
import com.processpuzzle.app.usecase.DeleteModuleDefinition;
import com.processpuzzle.app.usecase.ImportAppDefinitions;
import com.processpuzzle.app.usecase.ImportModuleDefinitions;
import com.processpuzzle.app.usecase.PublishAppDefinition;
import com.processpuzzle.app.usecase.UploadAppAsset;
import com.processpuzzle.app.usecase.UploadModuleAsset;
import com.processpuzzle.app.usecase.UploadModuleTranslations;
import com.processpuzzle.app.usecase.port.ModuleAssetStore;
import com.processpuzzle.app.usecase.port.SupportedLanguages;
import com.processpuzzle.app.usecase.port.TenantDirectory;
import com.processpuzzle.app.usecase.service.AppAssetReleases;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.app.usecase.service.ModuleTranslationLanguages;
import com.processpuzzle.core.definition.Channel;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The asset and translation participants over real repositories and the real use cases, after the module and app
 * participants that define their owners, so that what is asserted is the outcome a starter install has: files stored,
 * released at once, refused when their owner is not in the bundle, and gone again after a removal.
 */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.app.domain")
@EnableJpaRepositories("com.processpuzzle.app.domain")
class StarterBundleExtrasImportTest {

    private static final String ORG = "my-org";
    private static final String APP_ID = StarterAppCreator.STARTER_APP_ID;
    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>";

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
              - id: app
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

    /** Keeps what it is given, keyed the way the real adapter names objects. */
    private static final class MemoryStore implements ModuleAssetStore {
        final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public void put(String orgKey, String sha256, byte[] content, String mimeType) {
            objects.put(orgKey + "/" + sha256, content);
        }

        @Override
        public Optional<byte[]> get(String orgKey, String sha256) {
            return Optional.ofNullable(objects.get(orgKey + "/" + sha256));
        }

        @Override
        public void delete(String orgKey, String sha256) {
            objects.remove(orgKey + "/" + sha256);
        }

        @Override
        public void deleteAll(String orgKey) {
            objects.keySet().removeIf(name -> name.startsWith(orgKey + "/"));
        }
    }

    @Autowired
    private ModuleDefinitionRepository moduleRepository;
    @Autowired
    private ModuleTranslationRepository translationRepository;
    @Autowired
    private ModuleAssetRepository moduleAssetRepository;
    @Autowired
    private AppDefinitionRepository appRepository;
    @Autowired
    private AppAssetRepository appAssetRepository;
    @Autowired
    private EntityManager entityManager;

    private final MemoryStore store = new MemoryStore();
    private final ModuleAssetSweeper sweeper = mock(ModuleAssetSweeper.class);
    private ModuleImportParticipant modules;
    private AppImportParticipant apps;
    private AssetImportParticipant assets;
    private TranslationImportParticipant translations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        OrganizationGuard guard = AppTestFixtures.permissiveGuard();
        AppDefinitionValidator validator = AppTestFixtures.structuralValidator();
        AppMapper mapper = new AppMapper();
        ObjectProvider<TenantDirectory> tenants = mock(ObjectProvider.class);
        when(tenants.getIfAvailable()).thenReturn(null);
        ObjectProvider<SupportedLanguages> languages = mock(ObjectProvider.class);
        when(languages.getIfAvailable()).thenReturn(null);
        ObjectProvider<ModuleAssetStore> objects = mock(ObjectProvider.class);
        when(objects.getIfAvailable()).thenReturn(store);
        AppAssetReleases appRelease = new AppAssetReleases(appAssetRepository);

        ModuleReleaseParticipant moduleRelease = new ModuleReleaseParticipant(moduleRepository, translationRepository, moduleAssetRepository, sweeper);
        modules = new ModuleImportParticipant(new ImportModuleDefinitions(moduleRepository, tenants, validator, guard, mapper),
                new DeleteModuleDefinition(moduleRepository, translationRepository, moduleAssetRepository, sweeper, guard),
                moduleRepository, moduleRelease);
        apps = new AppImportParticipant(new ImportAppDefinitions(appRepository, tenants, validator, guard, mapper),
                new PublishAppDefinition(appRepository, validator, appRelease, sweeper, guard, mapper),
                new DeleteAppDefinition(appRepository, appRelease, sweeper, guard), appRepository);
        assets = new AssetImportParticipant(new UploadModuleAsset(moduleRepository, moduleAssetRepository, objects, sweeper, guard),
                new UploadAppAsset(appRepository, appAssetRepository, objects, sweeper, guard),
                moduleAssetRepository, appAssetRepository, moduleRepository, appRepository, moduleRelease, appRelease, sweeper);
        translations = new TranslationImportParticipant(
                new UploadModuleTranslations(moduleRepository, translationRepository, new ModuleTranslationLanguages(languages, tenants), guard),
                translationRepository, moduleRepository, moduleRelease);

        appRepository.saveAndFlush(new AppDefinition(ORG, APP_ID, "My Org", null, null, AppGraph.empty()));
        modules.apply(ORG, "modules/plan.yaml", bytes(MODULES));
        apps.apply(ORG, "apps/home.yaml", bytes(APP));
        flushAndClear();
    }

    @Test
    void runAfterTheModulesAndTheAppsTheyBelongTo() {
        assertThat(assets.kind()).isEqualTo(DefinitionKinds.ASSET);
        assertThat(translations.kind()).isEqualTo(DefinitionKinds.TRANSLATION);
        assertThat(translations.order()).isGreaterThan(modules.order()).isLessThan(apps.order());
        assertThat(assets.order()).isGreaterThan(apps.order());
    }

    // --- assets --------------------------------------------------------------------------

    @Test
    void anAppAssetIsStoredAndReleasedAtOnce() {
        ParticipantResult result = assets.apply(ORG, "assets/apps/app/cards/plan-race.svg", bytes(SVG));
        flushAndClear();

        assertThat(result.isRejected()).isFalse();
        assertThat(result.items()).containsExactly(new ImportedItem("apps/app/cards/plan-race.svg", ImportedItem.Action.CREATE));
        var draft = appAssetRepository.findByOrgKeyAndAppIdAndChannelAndPath(ORG, APP_ID, Channel.DRAFT, "cards/plan-race.svg").orElseThrow();
        var released = appAssetRepository.findByOrgKeyAndAppIdAndChannelAndPath(ORG, APP_ID, Channel.RELEASED, "cards/plan-race.svg").orElseThrow();
        assertThat(released.getSha256()).isEqualTo(draft.getSha256());
        assertThat(draft.getMimeType()).isEqualTo("image/svg+xml");
        assertThat(store.get(ORG, draft.getSha256())).isPresent();
    }

    @Test
    void aModuleAssetIsStoredAndReleasedAtOnce() {
        ParticipantResult result = assets.apply(ORG, "assets/modules/plan-race/docs/guide.md", bytes("# Guide"));
        flushAndClear();

        assertThat(result.items()).containsExactly(new ImportedItem("modules/plan-race/docs/guide.md", ImportedItem.Action.CREATE));
        assertThat(moduleAssetRepository.findByOrgKeyAndModuleKeyAndChannelAndPath(ORG, "plan-race", Channel.DRAFT, "docs/guide.md")).isPresent();
        assertThat(moduleAssetRepository.findByOrgKeyAndModuleKeyAndChannelAndPath(ORG, "plan-race", Channel.RELEASED, "docs/guide.md")).isPresent();
    }

    @Test
    void anAssetAlreadyThereIsAnUpdateAndTheReleasedRecordFollows() {
        assets.apply(ORG, "assets/apps/app/cards/plan-race.svg", bytes(SVG));
        flushAndClear();

        ParticipantResult result = assets.apply(ORG, "assets/apps/app/cards/plan-race.svg", bytes(SVG.replace("svg", "svg ") + "<!-- 2 -->"));
        flushAndClear();

        assertThat(result.items()).extracting(ImportedItem::action).containsExactly(ImportedItem.Action.UPDATE);
        var draft = appAssetRepository.findByOrgKeyAndAppIdAndChannelAndPath(ORG, APP_ID, Channel.DRAFT, "cards/plan-race.svg").orElseThrow();
        var released = appAssetRepository.findByOrgKeyAndAppIdAndChannelAndPath(ORG, APP_ID, Channel.RELEASED, "cards/plan-race.svg").orElseThrow();
        assertThat(released.getSha256()).isEqualTo(draft.getSha256());
    }

    @Test
    void anAssetWhoseOwnerTheBundleDoesNotDefineIsRefused() {
        ParticipantResult noModule = assets.apply(ORG, "assets/modules/ghost/a.png", bytes("x"));
        ParticipantResult noApp = assets.apply(ORG, "assets/apps/ghost/a.png", bytes("x"));

        assertThat(noModule.isRejected()).isTrue();
        assertThat(noModule.errors()).singleElement().asString().startsWith("assets/modules/ghost/a.png: ").contains("no module 'ghost'");
        assertThat(noApp.isRejected()).isTrue();
        assertThat(noApp.errors()).singleElement().asString().contains("no app 'ghost'");
    }

    @Test
    void anAssetOutsideTheLayoutOrWithABadPathIsRefused() {
        assertThat(assets.apply(ORG, "assets/plan-race.svg", bytes("x")).isRejected()).isTrue();
        assertThat(assets.apply(ORG, "assets/widgets/w/a.png", bytes("x")).isRejected()).isTrue();
        assertThat(assets.apply(ORG, "assets/modules/plan-race", bytes("x")).isRejected()).isTrue();
        ParticipantResult badPath = assets.apply(ORG, "assets/modules/plan-race/a b.png", bytes("x"));
        assertThat(badPath.isRejected()).isTrue();
        assertThat(badPath.errors()).singleElement().asString().startsWith("assets/modules/plan-race/a b.png: ");
    }

    @Test
    void anEmptyAssetIsRefused() {
        assertThat(assets.apply(ORG, "assets/apps/app/empty.png", new byte[0]).isRejected()).isTrue();
    }

    @Test
    void theFingerprintOfAnAssetChangesWithItsContentAndCoversOnlyTheAskedKeys() {
        assets.apply(ORG, "assets/apps/app/cards/plan-race.svg", bytes(SVG));
        assets.apply(ORG, "assets/modules/plan-race/docs/guide.md", bytes("# Guide"));
        flushAndClear();

        Map<String, String> first = assets.fingerprints(ORG, Set.of("apps/app/cards/plan-race.svg", "missing"));
        assertThat(first).containsOnlyKeys("apps/app/cards/plan-race.svg");
        assertThat(assets.fingerprints(ORG, Set.of("apps/app/cards/plan-race.svg"))).isEqualTo(first);
        assertThat(assets.fingerprints(ORG, Set.of("modules/plan-race/docs/guide.md"))).containsOnlyKeys("modules/plan-race/docs/guide.md");

        assets.apply(ORG, "assets/apps/app/cards/plan-race.svg", bytes(SVG + "<!-- changed -->"));
        flushAndClear();
        assertThat(assets.fingerprints(ORG, Set.of("apps/app/cards/plan-race.svg"))).isNotEqualTo(first);
    }

    @Test
    void removingAllAssetsDeletesBothChannelsAndSweepsTheirContent() {
        assets.apply(ORG, "assets/apps/app/cards/plan-race.svg", bytes(SVG));
        assets.apply(ORG, "assets/modules/plan-race/docs/guide.md", bytes("# Guide"));
        flushAndClear();
        String svgHash = appAssetRepository.findByOrgKeyAndChannel(ORG, Channel.DRAFT).getFirst().getSha256();
        String guideHash = moduleAssetRepository.findByOrgKeyAndChannel(ORG, Channel.DRAFT).getFirst().getSha256();

        List<String> removed = assets.removeAll(ORG);
        flushAndClear();

        assertThat(removed).containsExactlyInAnyOrder("apps/app/cards/plan-race.svg", "modules/plan-race/docs/guide.md");
        assertThat(appAssetRepository.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).isEmpty();
        assertThat(appAssetRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).isEmpty();
        assertThat(moduleAssetRepository.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).isEmpty();
        assertThat(moduleAssetRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).isEmpty();
        verify(sweeper, atLeastOnce()).sweep(eq(ORG), argThat(hashes -> hashes.contains(svgHash) && hashes.contains(guideHash)));
    }

    @Test
    void anAssetCanBeImportedAgainAfterRemovingAll() {
        assets.apply(ORG, "assets/apps/app/cards/plan-race.svg", bytes(SVG));
        flushAndClear();

        assets.removeAll(ORG);
        ParticipantResult result = assets.apply(ORG, "assets/apps/app/cards/plan-race.svg", bytes(SVG));
        flushAndClear();

        assertThat(result.items()).extracting(ImportedItem::action).containsExactly(ImportedItem.Action.CREATE);
        assertThat(appAssetRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).hasSize(1);
    }

    // --- translations --------------------------------------------------------------------

    @Test
    void aTranslationIsStoredAndReleasedAtOnce() {
        ParticipantResult result = translations.apply(ORG, "translations/modules/plan-race/de.json", bytes("""
                { "module": { "name": "Rennen planen" }, "route.overview": "Übersicht" }
                """));
        flushAndClear();

        assertThat(result.isRejected()).isFalse();
        assertThat(result.items()).containsExactly(new ImportedItem("modules/plan-race/de", ImportedItem.Action.CREATE));
        for (Channel channel : Channel.values()) {
            assertThat(translationRepository.findByOrgKeyAndModuleKeyAndChannelAndLocale(ORG, "plan-race", channel, "de"))
                    .get().extracting(ModuleTranslation::getMessages)
                    .isEqualTo(Map.of("module.name", "Rennen planen", "route.overview", "Übersicht"));
        }
    }

    @Test
    void aTranslationAlreadyThereIsAnUpdate() {
        translations.apply(ORG, "translations/modules/plan-race/de.json", bytes("{\"module.name\": \"A\"}"));
        flushAndClear();

        ParticipantResult result = translations.apply(ORG, "translations/modules/plan-race/de.json", bytes("{\"module.name\": \"B\"}"));
        flushAndClear();

        assertThat(result.items()).extracting(ImportedItem::action).containsExactly(ImportedItem.Action.UPDATE);
        assertThat(translationRepository.findByOrgKeyAndModuleKeyAndChannelAndLocale(ORG, "plan-race", Channel.RELEASED, "de"))
                .get().extracting(ModuleTranslation::getMessages).isEqualTo(Map.of("module.name", "B"));
    }

    @Test
    void aTextTheModuleDoesNotAuthorIsRefusedRatherThanDropped() {
        ParticipantResult result = translations.apply(ORG, "translations/modules/plan-race/de.json",
                bytes("{\"module.name\": \"A\", \"card.title\": \"B\"}"));

        assertThat(result.isRejected()).isTrue();
        assertThat(result.errors()).singleElement().asString()
                .startsWith("translations/modules/plan-race/de.json: ").contains("'card.title'");
    }

    @Test
    void aTranslationOfAnAppOrOfAModuleTheBundleDoesNotDefineOrOfABadNameIsRefused() {
        assertThat(translations.apply(ORG, "translations/apps/app/de.json", bytes("{}")).errors())
                .singleElement().asString().contains("only a module's texts");
        assertThat(translations.apply(ORG, "translations/modules/ghost/de.json", bytes("{}")).errors())
                .singleElement().asString().contains("no module 'ghost'");
        assertThat(translations.apply(ORG, "translations/modules/plan-race/de.txt", bytes("{}")).isRejected()).isTrue();
        assertThat(translations.apply(ORG, "translations/de.json", bytes("{}")).isRejected()).isTrue();
        assertThat(translations.apply(ORG, "translations/modules/plan-race/de.json", bytes("[1]")).isRejected()).isTrue();
        assertThat(translations.apply(ORG, "translations/modules/plan-race/not a locale.json", bytes("{}")).isRejected()).isTrue();
    }

    @Test
    void theFingerprintOfATranslationChangesWithItsTexts() {
        translations.apply(ORG, "translations/modules/plan-race/de.json", bytes("{\"module.name\": \"A\"}"));
        flushAndClear();

        Map<String, String> first = translations.fingerprints(ORG, Set.of("modules/plan-race/de", "missing"));
        assertThat(first).containsOnlyKeys("modules/plan-race/de");
        assertThat(translations.fingerprints(ORG, Set.of("modules/plan-race/de"))).isEqualTo(first);

        translations.apply(ORG, "translations/modules/plan-race/de.json", bytes("{\"module.name\": \"B\"}"));
        flushAndClear();
        assertThat(translations.fingerprints(ORG, Set.of("modules/plan-race/de"))).isNotEqualTo(first);
    }

    @Test
    void removingAllTranslationsDeletesBothChannelsAndTheyCanComeBack() {
        translations.apply(ORG, "translations/modules/plan-race/de.json", bytes("{\"module.name\": \"A\"}"));
        translations.apply(ORG, "translations/modules/plan-race/en.json", bytes("{\"module.name\": \"Plan Race\"}"));
        flushAndClear();

        List<String> removed = translations.removeAll(ORG);
        flushAndClear();

        assertThat(removed).containsExactlyInAnyOrder("modules/plan-race/de", "modules/plan-race/en");
        assertThat(translationRepository.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).isEmpty();
        assertThat(translationRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).isEmpty();

        ParticipantResult again = translations.apply(ORG, "translations/modules/plan-race/de.json", bytes("{\"module.name\": \"A\"}"));
        flushAndClear();
        assertThat(again.items()).extracting(ImportedItem::action).containsExactly(ImportedItem.Action.CREATE);
        assertThat(translationRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).hasSize(1);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
