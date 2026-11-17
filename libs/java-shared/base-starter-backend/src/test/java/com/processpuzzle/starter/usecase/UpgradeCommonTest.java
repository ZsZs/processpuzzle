package com.processpuzzle.starter.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.OrganizationReleaseRefusedException;
import com.processpuzzle.core.definition.OrganizationReleaser;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.core.tenancy.OrganizationAccessPolicy;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.starter.StarterProperties;
import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.bundle.TestBundles;
import com.processpuzzle.starter.domain.DataSpaceRepository;
import com.processpuzzle.starter.domain.DefinitionProvenanceRepository;
import com.processpuzzle.starter.domain.InstalledStarterRepository;
import com.processpuzzle.starter.domain.OfferedVersionRepository;
import com.processpuzzle.starter.registry.StarterCatalog;
import com.processpuzzle.starter.registry.StarterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rolling out a version of a common: the upgrade and the release are one transaction, so a refused release leaves the
 * common as it was. Over real repositories and a real transaction manager, with in-memory participants and a releaser
 * the test sets.
 */
@DataJpaTest(showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EntityScan("com.processpuzzle.starter.domain")
@EnableJpaRepositories("com.processpuzzle.starter.domain")
class UpgradeCommonTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    private static final String COMMON = "inventory-common";

    @Autowired
    private DefinitionProvenanceRepository provenanceRepository;
    @Autowired
    private InstalledStarterRepository installedRepository;
    @Autowired
    private DataSpaceRepository dataSpaceRepository;
    @Autowired
    private OfferedVersionRepository offeredRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final Map<String, byte[]> bundles = new LinkedHashMap<>();
    private final List<String> released = new ArrayList<>();
    private StarterCatalog catalog;
    private OrganizationReleaser releaser;
    private boolean hostCanRelease;
    private UpgradeCommon upgradeCommon;

    @BeforeEach
    void setUp() {
        provenanceRepository.deleteAll();
        installedRepository.deleteAll();
        dataSpaceRepository.deleteAll();
        offeredRepository.deleteAll();
        released.clear();
        catalog = null;
        hostCanRelease = true;
        releaser = (orgKey, by) -> {
            released.add(orgKey);
            return OrganizationReleaser.Outcome.RELEASED;
        };
        FakeParticipant entities = new FakeParticipant(DefinitionKinds.ENTITY, DefinitionKinds.ENTITY_ORDER, new ArrayList<>());
        entities.upgradeable = true;
        ImportParticipants participants = ImportParticipants.of(List.of(entities));
        OrganizationAccessPolicy policy = mock(OrganizationAccessPolicy.class);
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("policy", policy);
        OrganizationGuard guard = new OrganizationGuard(factory.getBeanProvider(OrganizationAccessPolicy.class));
        StarterRegistry registry = mock(StarterRegistry.class);
        when(registry.catalog()).thenAnswer(call -> catalog);
        when(registry.bundle(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> bundles.get(call.<String>getArgument(0)));

        publish("1.0.0", "item\nlocation\n");
        publish("2.0.0", "item\nbarn\n");
        CommonRules commonRules = new CommonRules(dataSpaceRepository, installedRepository, new RegisterDataSpace(dataSpaceRepository));
        ImportBundle importBundle = new ImportBundle(guard, new BundleReader(new StarterProperties()), participants,
                provenanceRepository, installedRepository, commonRules, transactionManager, provider(null));
        InstallStarter installer = new InstallStarter(guard, new BrowseCatalog(registry), registry, importBundle);
        UpgradeStarter upgrade = new UpgradeStarter(guard, installer, registry, new BundleReader(new StarterProperties()),
                new UpgradePlanner(participants, provenanceRepository), commonRules, participants, provenanceRepository,
                installedRepository, offeredRepository, transactionManager, provider(null));
        upgradeCommon = new UpgradeCommon(upgrade, releaserProvider(), dataSpaceRepository, transactionManager);
        installer.provision(COMMON, COMMON, "1.0.0", "seed");
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> bean != null ? bean
                : ((Supplier<T>) call.getArgument(0)).get());
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<OrganizationReleaser> releaserProvider() {
        ObjectProvider<OrganizationReleaser> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenAnswer(call -> hostCanRelease ? releaser : null);
        return provider;
    }

    private void publish(String version, String entityFile) {
        String manifest = """
                manifestVersion: 2
                kind: common
                id: inventory-common
                name: Inventory Common
                version: %s
                definitionSchemaVersion: 1
                contents:
                  entities:
                    - entities/item.yaml
                """.formatted(version);
        byte[] bytes = TestBundles.zip(Map.of("manifest.yaml", manifest, "entities/item.yaml", entityFile));
        String path = "inventory-common/" + version + "/bundle.zip";
        bundles.put(path, bytes);
        List<StarterCatalog.Version> versions = new ArrayList<>(catalog == null ? List.of() : catalog.starters().getFirst().versions());
        versions.add(new StarterCatalog.Version(version, null, null, 1, BundleReader.sha256(bytes), path));
        catalog = new StarterCatalog(1, List.of(new StarterCatalog.Starter(COMMON, "Inventory Common", null, null, null,
                null, null, null, versions)));
    }

    private String installedVersion() {
        return installedRepository.findByOrgKeyAndStarterId(COMMON, COMMON).orElseThrow().getVersion();
    }

    @Test
    void upgradesTheCommonAndReleasesItInOneGo() {
        UpgradeCommon.Result result = upgradeCommon.upgradeAndRelease(COMMON, "2.0.0", "staff");

        assertThat(result.done()).isTrue();
        assertThat(result.release()).isEqualTo(UpgradeCommon.Release.RELEASED);
        assertThat(result.upgrade().status()).isEqualTo(UpgradeReport.Status.APPLIED);
        assertThat(released).containsExactly(COMMON);
        assertThat(installedVersion()).isEqualTo("2.0.0");
    }

    @Test
    void aRefusedReleaseRollsTheUpgradeBack_andComesBackWithTheProblemsAndTheReport() {
        ReleaseProblem problem = ReleaseProblem.error("entity", "location", "release.common-member-references",
                "The entity 'location' is referred to by members of this common: club-a (entity 'profile').");
        releaser = (orgKey, by) -> {
            throw new OrganizationReleaseRefusedException("refused", List.of(problem));
        };

        UpgradeCommon.Result result = upgradeCommon.upgradeAndRelease(COMMON, "2.0.0", "staff");

        assertThat(result.done()).isFalse();
        assertThat(result.release()).isEqualTo(UpgradeCommon.Release.REFUSED);
        assertThat(result.problems()).containsExactly(problem);
        assertThat(result.upgrade().items()).extracting(UpgradeReport.Item::key).contains("barn", "location");
        assertThat(installedVersion()).as("rolled back with the release").isEqualTo("1.0.0");
        assertThat(provenanceRepository.findByOrgKeyAndKindAndKey(COMMON, "entity", "barn")).isEmpty();
    }

    @Test
    void anUpgradeThatChangedNothingNeedsNoRelease_butStillMovesTheVersion() {
        releaser = (orgKey, by) -> OrganizationReleaser.Outcome.NOTHING_PENDING;

        UpgradeCommon.Result result = upgradeCommon.upgradeAndRelease(COMMON, "2.0.0", "staff");

        assertThat(result.release()).isEqualTo(UpgradeCommon.Release.NOTHING_PENDING);
        assertThat(result.done()).isTrue();
        assertThat(installedVersion()).isEqualTo("2.0.0");
    }

    @Test
    void anUpgradeThatItselfIsRefusedReleasesNothing() {
        UpgradeCommon.Result result = upgradeCommon.upgradeAndRelease(COMMON, "9.9.9", "staff");

        assertThat(result.release()).isEqualTo(UpgradeCommon.Release.NOT_ATTEMPTED);
        assertThat(result.upgrade().status()).isEqualTo(UpgradeReport.Status.REJECTED);
        assertThat(released).isEmpty();
        assertThat(installedVersion()).isEqualTo("1.0.0");
    }

    @Test
    void onlyACommonCanBeRolledOut() {
        assertThatThrownBy(() -> upgradeCommon.upgradeAndRelease("acme", "2.0.0", "staff")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> upgradeCommon.upgradeAndRelease("other-common", "2.0.0", "staff")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aHostThatCannotReleaseCannotRollOutACommon() {
        hostCanRelease = false;

        assertThatThrownBy(() -> upgradeCommon.upgradeAndRelease(COMMON, "2.0.0", "staff")).isInstanceOf(IllegalStateException.class);
        assertThat(installedVersion()).isEqualTo("1.0.0");
    }
}
