package com.processpuzzle.app.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.ModuleAsset;
import com.processpuzzle.app.domain.ModuleAssetRepository;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.usecase.exception.ModuleAssetNotFoundException;
import com.processpuzzle.app.usecase.exception.ModuleAssetRejectedException;
import com.processpuzzle.app.usecase.exception.ModuleAssetStoreUnavailableException;
import com.processpuzzle.app.usecase.exception.ModuleDefinitionNotFoundException;
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

/** The asset use cases against a real repository and an in-memory object store. */
@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.app.domain")
@EnableJpaRepositories("com.processpuzzle.app.domain")
class ModuleAssetUseCasesTest {

    private static final String ORG = "my-org";
    private static final String MODULE = "claims";

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
    private ModuleDefinitionRepository modules;
    @Autowired
    private ModuleAssetRepository assets;
    @Autowired
    private EntityManager entityManager;

    private final MemoryStore memory = new MemoryStore();
    private final ModuleAssetSweeper sweeper = mock(ModuleAssetSweeper.class);
    private UploadModuleAsset upload;
    private ListModuleAssets list;
    private GetModuleAssetContent content;
    private DeleteModuleAsset delete;

    @BeforeEach
    void setUp() {
        wire(memory, AppTestFixtures.permissiveGuard());
        modules.save(new ModuleDefinition(ORG, MODULE, "Claims", Channel.DRAFT));
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
        upload = new UploadModuleAsset(modules, assets, provider, sweeper, guard);
        list = new ListModuleAssets(modules, assets, guard);
        content = new GetModuleAssetContent(modules, assets, provider, guard);
        delete = new DeleteModuleAsset(assets, sweeper, guard);
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private ModuleAssetSummary put(String path, String text) {
        var summary = upload.execute(ORG, MODULE, path, "ignored.bin", "text/markdown; charset=UTF-8", text.getBytes(StandardCharsets.UTF_8));
        flush();
        return summary;
    }

    // --- upload --------------------------------------------------------------------------

    @Test
    void storesTheContentUnderItsHashAndRecordsThePath() {
        var summary = put("docs/welcome.md", "# Hello");

        assertThat(summary.path()).isEqualTo("docs/welcome.md");
        assertThat(summary.size()).isEqualTo(7);
        assertThat(summary.sha256()).hasSize(64);
        assertThat(memory.objects).containsOnlyKeys(ORG + "/" + summary.sha256());
        assertThat(assets.findByOrgKeyAndModuleKeyAndChannelAndPath(ORG, MODULE, Channel.DRAFT, "docs/welcome.md")).isPresent();
        assertThat(assets.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).isEmpty();
    }

    @Test
    void takesTheDeclaredTypeWithoutItsParametersAndGuessesAMissingOne() {
        assertThat(put("a.md", "x").mimeType()).isEqualTo("text/markdown");
        assertThat(upload.execute(ORG, MODULE, "pic.png", null, null, new byte[] {1}).mimeType()).isEqualTo("image/png");
        assertThat(upload.execute(ORG, MODULE, "pic2.png", null, "application/octet-stream", new byte[] {1}).mimeType()).isEqualTo("image/png");
        assertThat(upload.execute(ORG, MODULE, "data.unknownext", null, "nonsense", new byte[] {1}).mimeType()).isEqualTo("application/octet-stream");
    }

    @Test
    void anUploadWithoutAPathTakesTheFilesOwnName() {
        var summary = upload.execute(ORG, MODULE, null, "C:\\docs\\welcome.md", "text/markdown", new byte[] {1});

        assertThat(summary.path()).isEqualTo("welcome.md");
    }

    @Test
    void replacingAPathWithOtherContentHandsTheOldContentToTheSweeper() {
        var first = put("a.md", "one");
        var second = put("a.md", "two");

        assertThat(assets.findByOrgKeyAndModuleKeyAndChannelOrderByPath(ORG, MODULE, Channel.DRAFT)).singleElement()
                .satisfies(asset -> assertThat(asset.getSha256()).isEqualTo(second.sha256()));
        verify(sweeper).sweep(ORG, List.of(first.sha256()));
    }

    @Test
    void uploadingTheSameContentAgainSweepsNothing() {
        put("a.md", "same");
        put("a.md", "same");

        verify(sweeper, never()).sweep(any(), any());
    }

    @Test
    void twoPathsWithTheSameContentShareOneObject() {
        put("a.md", "same");
        put("b.md", "same");

        assertThat(memory.objects).hasSize(1);
        assertThat(assets.findByOrgKeyAndModuleKeyAndChannelOrderByPath(ORG, MODULE, Channel.DRAFT)).hasSize(2);
    }

    @Test
    void refusesWhatCannotBeStored() {
        assertThatThrownBy(() -> upload.execute(ORG, MODULE, "../a.md", null, null, new byte[] {1}))
                .isInstanceOf(ModuleAssetRejectedException.class);
        assertThatThrownBy(() -> upload.execute(ORG, MODULE, "a.md", null, null, new byte[0]))
                .isInstanceOf(ModuleAssetRejectedException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> upload.execute(ORG, MODULE, "a.md", null, null, new byte[UploadModuleAsset.MAX_BYTES + 1]))
                .isInstanceOf(ModuleAssetRejectedException.class).hasMessageContaining("larger");
        assertThat(memory.objects).isEmpty();
    }

    @Test
    void refusesANewPathOnceTheModuleIsFullButStillReplacesAnExistingOne() {
        for (int i = 0; i < UploadModuleAsset.MAX_ASSETS; i++) {
            assets.save(new ModuleAsset(ORG, MODULE, Channel.DRAFT, "f" + i + ".md", "h" + i, "text/markdown", 1));
        }
        flush();

        assertThatThrownBy(() -> upload.execute(ORG, MODULE, "one-too-many.md", null, null, new byte[] {1}))
                .isInstanceOf(ModuleAssetRejectedException.class).hasMessageContaining("at most");
        assertThat(upload.execute(ORG, MODULE, "f0.md", null, "text/markdown", new byte[] {2}).path()).isEqualTo("f0.md");
    }

    @Test
    void aModuleThatDoesNotExistIsNotFound() {
        assertThatThrownBy(() -> upload.execute(ORG, "nope", "a.md", null, null, new byte[] {1}))
                .isInstanceOf(ModuleDefinitionNotFoundException.class);
        assertThatThrownBy(() -> list.execute(ORG, "nope", Channel.DRAFT)).isInstanceOf(ModuleDefinitionNotFoundException.class);
    }

    @Test
    void withNoObjectStoreWiredAnUploadIsRefusedAndNothingIsRecorded() {
        wire(null, AppTestFixtures.permissiveGuard());

        assertThatThrownBy(() -> upload.execute(ORG, MODULE, "a.md", null, null, new byte[] {1}))
                .isInstanceOf(ModuleAssetStoreUnavailableException.class);
        assertThat(assets.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).isEmpty();
    }

    // --- list and read -------------------------------------------------------------------

    @Test
    void listsTheAssetsOfAChannelByPathWithoutTheirContent() {
        put("b.md", "b");
        put("a/z.md", "z");
        modules.save(new ModuleDefinition(ORG, MODULE, "Claims", Channel.RELEASED));
        assets.save(new ModuleAsset(ORG, MODULE, Channel.RELEASED, "released.md", "h", "text/markdown", 1));
        flush();

        assertThat(list.execute(ORG, MODULE, Channel.DRAFT)).extracting(ModuleAssetSummary::path).containsExactly("a/z.md", "b.md");
        assertThat(list.execute(ORG, MODULE, Channel.RELEASED)).extracting(ModuleAssetSummary::path).containsExactly("released.md");
    }

    @Test
    void readsTheBytesBackWithTheirHash() {
        var summary = put("a.md", "# Hello");

        var read = content.execute(ORG, MODULE, "a.md", Channel.DRAFT);

        assertThat(new String(read.content(), StandardCharsets.UTF_8)).isEqualTo("# Hello");
        assertThat(read.sha256()).isEqualTo(summary.sha256());
        assertThat(content.summaryOf(ORG, MODULE, "a.md", Channel.DRAFT)).isEqualTo(summary);
    }

    @Test
    void anAssetNobodyUploadedIsNotFound_andSoIsOneWhoseObjectIsGone() {
        assertThatThrownBy(() -> content.execute(ORG, MODULE, "nope.md", Channel.DRAFT)).isInstanceOf(ModuleAssetNotFoundException.class);

        var summary = put("a.md", "x");
        memory.objects.remove(ORG + "/" + summary.sha256());
        assertThatThrownBy(() -> content.execute(ORG, MODULE, "a.md", Channel.DRAFT)).isInstanceOf(ModuleAssetNotFoundException.class);
    }

    @Test
    void withNoObjectStoreWiredTheRecordsStillListButTheContentIsUnavailable() {
        put("a.md", "x");
        wire(null, AppTestFixtures.permissiveGuard());

        assertThat(list.execute(ORG, MODULE, Channel.DRAFT)).hasSize(1);
        assertThatThrownBy(() -> content.execute(ORG, MODULE, "a.md", Channel.DRAFT)).isInstanceOf(ModuleAssetStoreUnavailableException.class);
    }

    // --- delete --------------------------------------------------------------------------

    @Test
    void deletesTheDraftRecordAndHandsItsContentToTheSweeper() {
        var summary = put("a.md", "x");

        delete.execute(ORG, MODULE, "a.md");
        flush();

        assertThat(assets.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).isEmpty();
        verify(sweeper).sweep(ORG, List.of(summary.sha256()));
        assertThatThrownBy(() -> delete.execute(ORG, MODULE, "a.md")).isInstanceOf(ModuleAssetNotFoundException.class);
    }

    // --- access --------------------------------------------------------------------------

    @Test
    void theDraftNeedsDesignRightsAndTheReleasedRowOnlyMembership() {
        var denied = AppTestFixtures.denyingGuard();
        wire(memory, denied);

        assertThatThrownBy(() -> upload.execute(ORG, MODULE, "a.md", null, null, new byte[] {1})).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> list.execute(ORG, MODULE, Channel.DRAFT)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> content.execute(ORG, MODULE, "a.md", Channel.RELEASED)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> delete.execute(ORG, MODULE, "a.md")).isInstanceOf(OrganizationAccessDeniedException.class);
    }
}
