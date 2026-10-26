package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.ModuleAsset;
import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.ModuleMount;
import com.processpuzzle.app.domain.ModuleTranslation;
import com.processpuzzle.app.domain.ModuleTranslationRepository;
import com.processpuzzle.app.domain.RouteTarget;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseProblem;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.app.domain")
@EnableJpaRepositories("com.processpuzzle.app.domain")
class AppReleaseParticipantsTest {

    private static final String ORG = "my-org";

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
    private EntityManager entityManager;

    private ModuleAssetSweeper sweeper;
    private ModuleReleaseParticipant modules;
    private AppReleaseParticipant apps;

    @BeforeEach
    void setUp() {
        sweeper = org.mockito.Mockito.mock(ModuleAssetSweeper.class);
        modules = new ModuleReleaseParticipant(moduleRepository, translationRepository, assetRepository, sweeper);
        apps = new AppReleaseParticipant(appRepository, AppTestFixtures.structuralValidator(), new AppMapper());
    }

    // --- modules -------------------------------------------------------------------------

    @Test
    void aNewModuleIsPendingAsAdded_andReleasingItCopiesTheDraft() {
        moduleRepository.save(module("claims", "Claims", Channel.DRAFT));

        assertThat(modules.pending(ORG)).extracting(PendingChange::key, PendingChange::change)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("claims", PendingChange.Change.ADDED));

        modules.release(ORG);
        flush();

        assertThat(moduleRepository.findByOrgKeyAndKeyAndChannel(ORG, "claims", Channel.RELEASED))
                .hasValueSatisfying(released -> assertThat(released.getRoutes()).extracting(AppRoute::path).containsExactly("list"));
        assertThat(modules.pending(ORG)).isEmpty();
    }

    @Test
    void anEditedDraftIsPendingAsChanged_andTheReleasedRowFollowsOnlyOnRelease() {
        moduleRepository.save(module("claims", "Claims", Channel.DRAFT));
        moduleRepository.save(module("claims", "Claims", Channel.RELEASED));
        flush();
        ModuleDefinition draft = moduleRepository.findByOrgKeyAndKeyAndChannel(ORG, "claims", Channel.DRAFT).orElseThrow();
        draft.setName("Claims handling");
        flush();

        assertThat(modules.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.CHANGED);
        assertThat(moduleRepository.findByOrgKeyAndKeyAndChannel(ORG, "claims", Channel.RELEASED).orElseThrow().getName())
                .isEqualTo("Claims");

        modules.release(ORG);
        flush();

        assertThat(moduleRepository.findByOrgKeyAndKeyAndChannel(ORG, "claims", Channel.RELEASED).orElseThrow().getName())
                .isEqualTo("Claims handling");
    }

    @Test
    void aDeletedDraftIsPendingAsRemoved_andReleasingDeletesTheReleasedRow() {
        moduleRepository.save(module("claims", "Claims", Channel.RELEASED));
        flush();

        assertThat(modules.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.REMOVED);

        modules.release(ORG);
        flush();

        assertThat(moduleRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).isEmpty();
    }

    @Test
    void anUploadedTranslationIsPendingOnAReleasedModule_andReleaseCopiesItAcrossLanguageByLanguage() {
        moduleRepository.save(module("claims", "Claims", Channel.DRAFT));
        moduleRepository.save(module("claims", "Claims", Channel.RELEASED));
        translationRepository.save(translation(Channel.RELEASED, "de", Map.of("module.name", "Alt")));
        translationRepository.save(translation(Channel.RELEASED, "fr", Map.of("module.name", "Ancien")));
        translationRepository.save(translation(Channel.DRAFT, "de", Map.of("module.name", "Schaeden")));
        flush();

        assertThat(modules.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.CHANGED);

        modules.release(ORG);
        flush();

        assertThat(translationRepository.findByOrgKeyAndModuleKeyAndChannel(ORG, "claims", Channel.RELEASED))
                .extracting(ModuleTranslation::getLocale, ModuleTranslation::getMessages)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("de", Map.of("module.name", "Schaeden")));
        assertThat(modules.pending(ORG)).isEmpty();
    }

    @Test
    void aModuleWithoutTranslationsKeepsTheFingerprintItHadBeforeTheyExisted() {
        ModuleDefinition module = module("claims", "Claims", Channel.DRAFT);

        assertThat(ModuleReleaseParticipant.fingerprint(module, Map.of(), Map.of()))
                .isEqualTo(ModuleReleaseParticipant.fingerprint(module, null, null))
                .doesNotContain("translations");
    }

    @Test
    void anUploadedAssetIsPendingAndReleaseCopiesTheRecordNotTheContent() {
        moduleRepository.save(module("claims", "Claims", Channel.DRAFT));
        moduleRepository.save(module("claims", "Claims", Channel.RELEASED));
        assetRepository.save(asset(Channel.DRAFT, "docs/a.md", "h1"));
        assetRepository.save(asset(Channel.DRAFT, "docs/b.md", "h2"));
        assetRepository.save(asset(Channel.RELEASED, "docs/a.md", "old"));
        assetRepository.save(asset(Channel.RELEASED, "docs/gone.md", "h3"));
        flush();

        assertThat(modules.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.CHANGED);

        modules.release(ORG);
        flush();

        assertThat(assetRepository.findByOrgKeyAndModuleKeyAndChannelOrderByPath(ORG, "claims", Channel.RELEASED))
                .extracting(ModuleAsset::getPath, ModuleAsset::getSha256)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("docs/a.md", "h1"), org.assertj.core.groups.Tuple.tuple("docs/b.md", "h2"));
        // What no released record names any more is for the sweeper to remove, once nothing else names it either.
        org.mockito.Mockito.verify(sweeper).sweep(org.mockito.ArgumentMatchers.eq(ORG),
                org.mockito.ArgumentMatchers.argThat(hashes -> hashes.containsAll(List.of("old", "h3")) && hashes.size() == 2));
        assertThat(modules.pending(ORG)).isEmpty();
    }

    @Test
    void releasingARemovedModuleDeletesItsReleasedAssetRecordsAndHandsTheirContentToTheSweeper() {
        moduleRepository.save(module("claims", "Claims", Channel.RELEASED));
        assetRepository.save(asset(Channel.RELEASED, "docs/a.md", "h1"));
        flush();

        modules.release(ORG);
        flush();

        assertThat(assetRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).isEmpty();
        org.mockito.Mockito.verify(sweeper).sweep(org.mockito.ArgumentMatchers.eq(ORG),
                org.mockito.ArgumentMatchers.argThat(hashes -> hashes.contains("h1")));
    }

    @Test
    void releasingARemovedModuleDeletesItsReleasedTranslations() {
        moduleRepository.save(module("claims", "Claims", Channel.RELEASED));
        translationRepository.save(translation(Channel.RELEASED, "de", Map.of("module.name", "Schaeden")));
        flush();

        modules.release(ORG);
        flush();

        assertThat(translationRepository.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).isEmpty();
    }

    @Test
    void aModuleRefersToTheEntitiesItsRoutesRender() {
        ModuleDefinition draft = module("claims", "Claims", Channel.DRAFT);
        draft.setRoutes(List.of(new AppRoute("list", "List", null, null, List.of(),
                new RouteTarget(RouteTarget.Kind.ENTITY, List.of(), null, "Claim", RouteTarget.EntityMode.LIST, null))));
        moduleRepository.save(draft);

        assertThat(modules.references(ORG)).containsExactly(
                DefinitionReference.required(DefinitionKinds.MODULE, "claims", DefinitionKinds.ENTITY, "Claim"));
    }

    // --- apps ----------------------------------------------------------------------------

    @Test
    void anUnpublishedAppIsPendingAsAdded_andReleasingPublishesIt() {
        appRepository.save(new AppDefinition(ORG, "app", "App", null, null, AppTestFixtures.validGraph()));

        assertThat(apps.pending(ORG)).extracting(PendingChange::change).containsExactly(PendingChange.Change.ADDED);
        assertThat(apps.check(ORG, null)).isEmpty();

        apps.release(ORG);
        flush();

        AppDefinition released = appRepository.findByOrgKeyAndId(ORG, "app").orElseThrow();
        assertThat(released.isPublished()).isTrue();
        assertThat(apps.pending(ORG)).isEmpty();
    }

    @Test
    void anAppReleasedUnchangedIsNotPending() {
        AppDefinition app = new AppDefinition(ORG, "app", "App", null, null, AppTestFixtures.validGraph());
        app.publish();
        app.replaceDraft("App", null, null, AppTestFixtures.validGraph());
        appRepository.save(app);

        assertThat(apps.pending(ORG)).isEmpty();
    }

    @Test
    void anAppMountsItsModulesOptionally() {
        AppGraph graph = AppTestFixtures.validGraph();
        appRepository.save(new AppDefinition(ORG, "app", "App", null, null,
                new AppGraph(graph.theme(), graph.layout(), graph.regions(), graph.routes(), List.of(new ModuleMount("claims", "claims")))));

        assertThat(apps.references(ORG)).contains(
                DefinitionReference.optional(DefinitionKinds.APP, "app", DefinitionKinds.MODULE, "claims"));
    }

    @Test
    void anInvalidDraftRefusesTheRelease() {
        AppRoute route = AppTestFixtures.appRoute("dup", "Dup");
        appRepository.save(new AppDefinition(ORG, "app", "App", null, null,
                new AppGraph(null, null, List.of(), List.of(route, route), List.of())));

        assertThat(apps.check(ORG, null)).isNotEmpty().allMatch(ReleaseProblem::isError);
    }

    private static ModuleDefinition module(String key, String name, Channel channel) {
        ModuleDefinition module = new ModuleDefinition(ORG, key, name, channel);
        module.setRoutes(List.of(AppTestFixtures.appRoute("list", "List")));
        return module;
    }

    private static ModuleAsset asset(Channel channel, String path, String sha256) {
        return new ModuleAsset(ORG, "claims", channel, path, sha256, "text/markdown", 10);
    }

    private static ModuleTranslation translation(Channel channel, String locale, Map<String, String> messages) {
        return new ModuleTranslation(ORG, "claims", channel, locale, messages);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }
}
