package com.processpuzzle.app.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppAsset;
import com.processpuzzle.app.domain.AppAssetRepository;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.usecase.exception.AppAssetNotFoundException;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.ModuleAssetRejectedException;
import com.processpuzzle.app.usecase.exception.ModuleAssetStoreUnavailableException;
import com.processpuzzle.app.usecase.port.ModuleAssetStore;
import com.processpuzzle.app.usecase.service.ModuleAssetSweeper;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

/** The app asset use cases against a real repository and an in-memory object store. */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.app.domain")
@EnableJpaRepositories("com.processpuzzle.app.domain")
class AppAssetUseCasesTest {

    private static final String ORG = "my-org";
    private static final String APP = "app";

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
    private AppDefinitionRepository apps;
    @Autowired
    private AppAssetRepository assets;
    @Autowired
    private EntityManager entityManager;

    private final MemoryStore memory = new MemoryStore();
    private final ModuleAssetSweeper sweeper = mock(ModuleAssetSweeper.class);
    private UploadAppAsset upload;
    private ListAppAssets list;
    private GetAppAssetContent content;
    private DeleteAppAsset delete;

    @BeforeEach
    void setUp() {
        wire(memory, AppTestFixtures.permissiveGuard());
        apps.save(new AppDefinition(ORG, APP, "App", null, null, AppTestFixtures.validGraph()));
        flush();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ModuleAssetStore> providerOf(ModuleAssetStore store) {
        ObjectProvider<ModuleAssetStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        return provider;
    }

    private void wire(ModuleAssetStore store, OrganizationGuard guard) {
        var provider = providerOf(store);
        upload = new UploadAppAsset(apps, assets, provider, sweeper, guard);
        list = new ListAppAssets(apps, assets, guard);
        content = new GetAppAssetContent(apps, assets, provider, guard);
        delete = new DeleteAppAsset(assets, sweeper, guard);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private ModuleAssetSummary put(String path, String text) {
        var summary = upload.execute(ORG, APP, path, "ignored.bin", "image/svg+xml; charset=UTF-8", text.getBytes(StandardCharsets.UTF_8));
        flush();
        return summary;
    }

    // --- upload --------------------------------------------------------------------------

    @Test
    void storesTheContentUnderItsHashAndRecordsThePathInTheDraftChannel() {
        var summary = put("cards/plan-race.svg", "<svg/>");

        assertThat(summary.path()).isEqualTo("cards/plan-race.svg");
        assertThat(summary.mimeType()).isEqualTo("image/svg+xml");
        assertThat(summary.size()).isEqualTo(6);
        assertThat(summary.sha256()).hasSize(64);
        assertThat(memory.objects).containsOnlyKeys(ORG + "/" + summary.sha256());
        assertThat(assets.findByOrgKeyAndAppIdAndChannelAndPath(ORG, APP, Channel.DRAFT, "cards/plan-race.svg")).isPresent();
        assertThat(assets.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).isEmpty();
    }

    @Test
    void anUploadWithoutAPathTakesTheFilesOwnName() {
        var summary = upload.execute(ORG, APP, null, "C:\\img\\logo.png", "image/png", new byte[] {1});

        assertThat(summary.path()).isEqualTo("logo.png");
    }

    @Test
    void replacingAPathWithOtherContentHandsTheOldContentToTheSweeper() {
        var first = put("a.svg", "one");
        var second = put("a.svg", "two");

        assertThat(assets.findByOrgKeyAndAppIdAndChannelOrderByPath(ORG, APP, Channel.DRAFT)).singleElement()
                .satisfies(asset -> assertThat(asset.getSha256()).isEqualTo(second.sha256()));
        verify(sweeper).sweep(ORG, List.of(first.sha256()));
    }

    @Test
    void uploadingTheSameContentAgainSweepsNothing() {
        put("a.svg", "same");
        put("a.svg", "same");

        verify(sweeper, never()).sweep(any(), any());
    }

    @Test
    void refusesWhatCannotBeStored() {
        assertThatThrownBy(() -> upload.execute(ORG, APP, "../a.svg", null, null, new byte[] {1}))
                .isInstanceOf(ModuleAssetRejectedException.class);
        assertThatThrownBy(() -> upload.execute(ORG, APP, "a.svg", null, null, new byte[0]))
                .isInstanceOf(ModuleAssetRejectedException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> upload.execute(ORG, APP, "a.svg", null, null, new byte[UploadModuleAsset.MAX_BYTES + 1]))
                .isInstanceOf(ModuleAssetRejectedException.class).hasMessageContaining("larger");
        assertThat(memory.objects).isEmpty();
    }

    @Test
    void refusesANewPathOnceTheAppIsFullButStillReplacesAnExistingOne() {
        for (int i = 0; i < UploadAppAsset.MAX_ASSETS; i++) {
            assets.save(new AppAsset(ORG, APP, Channel.DRAFT, "f" + i + ".svg", "h" + i, "image/svg+xml", 1));
        }
        flush();

        assertThatThrownBy(() -> upload.execute(ORG, APP, "one-too-many.svg", null, null, new byte[] {1}))
                .isInstanceOf(ModuleAssetRejectedException.class).hasMessageContaining("at most");
        assertThat(upload.execute(ORG, APP, "f0.svg", null, "image/svg+xml", new byte[] {2}).path()).isEqualTo("f0.svg");
    }

    @Test
    void anAppThatDoesNotExistIsNotFound() {
        assertThatThrownBy(() -> upload.execute(ORG, "nope", "a.svg", null, null, new byte[] {1}))
                .isInstanceOf(AppDefinitionNotFoundException.class);
        assertThatThrownBy(() -> list.execute(ORG, "nope", Channel.DRAFT)).isInstanceOf(AppDefinitionNotFoundException.class);
    }

    @Test
    void withNoObjectStoreWiredAnUploadIsRefusedAndNothingIsRecorded() {
        wire(null, AppTestFixtures.permissiveGuard());

        assertThatThrownBy(() -> upload.execute(ORG, APP, "a.svg", null, null, new byte[] {1}))
                .isInstanceOf(ModuleAssetStoreUnavailableException.class);
        assertThat(assets.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).isEmpty();
    }

    // --- list and read -------------------------------------------------------------------

    @Test
    void listsTheAssetsOfAChannelByPathWithoutTheirContent() {
        put("b.svg", "b");
        put("a/z.svg", "z");
        assets.save(new AppAsset(ORG, APP, Channel.RELEASED, "released.svg", "h", "image/svg+xml", 1));
        flush();

        assertThat(list.execute(ORG, APP, Channel.DRAFT)).extracting(ModuleAssetSummary::path).containsExactly("a/z.svg", "b.svg");
        assertThat(list.execute(ORG, APP, Channel.RELEASED)).extracting(ModuleAssetSummary::path).containsExactly("released.svg");
    }

    @Test
    void anotherAppsAssetsAreNotListed() {
        apps.save(new AppDefinition(ORG, "other", "Other", null, null, AppTestFixtures.validGraph()));
        assets.save(new AppAsset(ORG, "other", Channel.DRAFT, "theirs.svg", "h", "image/svg+xml", 1));
        flush();

        assertThat(list.execute(ORG, APP, Channel.DRAFT)).isEmpty();
    }

    @Test
    void readsTheBytesBackWithTheirHash() {
        var summary = put("a.svg", "<svg/>");

        var read = content.execute(ORG, APP, "a.svg", Channel.DRAFT);

        assertThat(new String(read.content(), StandardCharsets.UTF_8)).isEqualTo("<svg/>");
        assertThat(read.sha256()).isEqualTo(summary.sha256());
        assertThat(content.summaryOf(ORG, APP, "a.svg", Channel.DRAFT)).isEqualTo(summary);
    }

    @Test
    void anAssetNobodyUploadedIsNotFound_andSoIsOneWhoseObjectIsGone() {
        assertThatThrownBy(() -> content.execute(ORG, APP, "nope.svg", Channel.DRAFT)).isInstanceOf(AppAssetNotFoundException.class);

        var summary = put("a.svg", "x");
        memory.objects.remove(ORG + "/" + summary.sha256());
        assertThatThrownBy(() -> content.execute(ORG, APP, "a.svg", Channel.DRAFT)).isInstanceOf(AppAssetNotFoundException.class);
    }

    @Test
    void withNoObjectStoreWiredTheRecordsStillListButTheContentIsUnavailable() {
        put("a.svg", "x");
        wire(null, AppTestFixtures.permissiveGuard());

        assertThat(list.execute(ORG, APP, Channel.DRAFT)).hasSize(1);
        assertThatThrownBy(() -> content.execute(ORG, APP, "a.svg", Channel.DRAFT)).isInstanceOf(ModuleAssetStoreUnavailableException.class);
    }

    // --- delete --------------------------------------------------------------------------

    @Test
    void deletesTheDraftRecordAndHandsItsContentToTheSweeper() {
        var summary = put("a.svg", "x");

        delete.execute(ORG, APP, "a.svg");
        flush();

        assertThat(assets.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).isEmpty();
        verify(sweeper).sweep(ORG, List.of(summary.sha256()));
        assertThatThrownBy(() -> delete.execute(ORG, APP, "a.svg")).isInstanceOf(AppAssetNotFoundException.class);
    }

    @Test
    void deletingTheDraftLeavesTheReleasedRecord() {
        put("a.svg", "x");
        assets.save(new AppAsset(ORG, APP, Channel.RELEASED, "a.svg", "h", "image/svg+xml", 1));
        flush();

        delete.execute(ORG, APP, "a.svg");
        flush();

        assertThat(assets.findByOrgKeyAndAppIdAndChannelAndPath(ORG, APP, Channel.RELEASED, "a.svg")).isPresent();
    }

    // --- access --------------------------------------------------------------------------

    @Test
    void theDraftNeedsDesignRightsAndTheReleasedRowOnlyMembership() {
        wire(memory, AppTestFixtures.denyingGuard());

        assertThatThrownBy(() -> upload.execute(ORG, APP, "a.svg", null, null, new byte[] {1})).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> list.execute(ORG, APP, Channel.DRAFT)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> content.execute(ORG, APP, "a.svg", Channel.RELEASED)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> delete.execute(ORG, APP, "a.svg")).isInstanceOf(OrganizationAccessDeniedException.class);
    }
}
