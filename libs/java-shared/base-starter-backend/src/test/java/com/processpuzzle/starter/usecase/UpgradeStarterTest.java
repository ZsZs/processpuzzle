package com.processpuzzle.starter.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.tenancy.OrganizationAccessPolicy;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.starter.StarterProperties;
import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.bundle.TestBundles;
import com.processpuzzle.starter.domain.DataSpaceRepository;
import com.processpuzzle.starter.domain.DefinitionProvenanceRepository;
import com.processpuzzle.starter.domain.InstalledStarterRepository;
import com.processpuzzle.starter.registry.StarterCatalog;
import com.processpuzzle.starter.registry.StarterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Upgrading an organization from one version of its starter to the next, over real repositories and a real
 * transaction manager with in-memory participants: the version 1 bundle brings `item` and `location`, version 2
 * `item` and `barn`.
 */
@DataJpaTest(showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EntityScan("com.processpuzzle.starter.domain")
@EnableJpaRepositories("com.processpuzzle.starter.domain")
class UpgradeStarterTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    private static final String ORG = "acme";

    @Autowired
    private DefinitionProvenanceRepository provenanceRepository;
    @Autowired
    private InstalledStarterRepository installedRepository;
    @Autowired
    private DataSpaceRepository dataSpaceRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> calls = new ArrayList<>();
    private final Map<String, byte[]> bundles = new LinkedHashMap<>();
    private StarterCatalog catalog;
    private FakeParticipant entities;
    private FakeParticipant rules;
    private UpgradeStarter upgrade;

    @BeforeEach
    void setUp() {
        provenanceRepository.deleteAll();
        installedRepository.deleteAll();
        dataSpaceRepository.deleteAll();
        calls.clear();
        entities = new FakeParticipant(DefinitionKinds.ENTITY, DefinitionKinds.ENTITY_ORDER, calls);
        entities.upgradeable = true;
        rules = new FakeParticipant(DefinitionKinds.RULE, DefinitionKinds.RULE_ORDER, calls);
        ImportParticipants participants = ImportParticipants.of(List.of(entities, rules));

        OrganizationAccessPolicy policy = mock(OrganizationAccessPolicy.class);
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("policy", policy);
        OrganizationGuard guard = new OrganizationGuard(factory.getBeanProvider(OrganizationAccessPolicy.class));
        StarterRegistry registry = mock(StarterRegistry.class);
        when(registry.catalog()).thenAnswer(call -> catalog);
        when(registry.bundle(org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> bundles.get(call.<String>getArgument(0)));

        publish("1.0.0", "item\nlocation\n", "positive\n");
        publish("2.0.0", "item\nbarn\n", "positive\n");
        CommonRules commonRules = new CommonRules(dataSpaceRepository, installedRepository, new RegisterDataSpace(dataSpaceRepository));
        ImportBundle importBundle = new ImportBundle(guard, new BundleReader(new StarterProperties()), participants,
                provenanceRepository, installedRepository, commonRules, transactionManager, provider());
        InstallStarter installer = new InstallStarter(guard, new BrowseCatalog(registry), registry, importBundle);
        upgrade = new UpgradeStarter(guard, installer, registry, new BundleReader(new StarterProperties()),
                new UpgradePlanner(participants, provenanceRepository), commonRules, participants, provenanceRepository,
                installedRepository, transactionManager, provider());
        installer.provision(ORG, "inventory", "1.0.0", "seed");
        calls.clear();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> ((java.util.function.Supplier<T>) call.getArgument(0)).get());
        return provider;
    }

    /** Adds a version to the catalog, with a bundle of one entity file and one rule file. */
    private void publish(String version, String entityFile, String ruleFile) {
        String manifest = TestBundles.MANIFEST.replace("version: 1.0.0", "version: " + version);
        byte[] bytes = TestBundles.zip(Map.of("manifest.yaml", manifest, "entities/item.yaml", entityFile, "rules/item-rules.yaml", ruleFile));
        String path = "inventory/" + version + "/bundle.zip";
        bundles.put(path, bytes);
        List<StarterCatalog.Version> versions = new ArrayList<>(catalog == null ? List.of() : catalog.starters().getFirst().versions());
        versions.add(new StarterCatalog.Version(version, null, null, 1, BundleReader.sha256(bytes), path));
        catalog = new StarterCatalog(1, List.of(new StarterCatalog.Starter("inventory", "Inventory", null, null, null,
                null, null, null, versions)));
    }

    private Map<String, String> items(UpgradeReport report) {
        Map<String, String> byKey = new LinkedHashMap<>();
        report.items().forEach(item -> byKey.put(item.kind() + ":" + item.key(), item.action().name()));
        return byKey;
    }

    @Test
    void replacesTheUntouched_addsTheNew_removesTheDropped_andMovesTheVersionOn() {
        UpgradeReport report = upgrade.provision(ORG, "inventory", "2.0.0", "staff");

        assertThat(report.status()).isEqualTo(UpgradeReport.Status.APPLIED);
        assertThat(report.fromVersion()).isEqualTo("1.0.0");
        assertThat(items(report)).containsEntry("entity:item", "REPLACE").containsEntry("entity:barn", "ADD")
                .containsEntry("entity:location", "REMOVE");
        assertThat(entities.store.get(ORG)).containsOnlyKeys("item", "barn");
        assertThat(installedRepository.findByOrgKeyAndStarterId(ORG, "inventory")).hasValueSatisfying(
                installed -> assertThat(installed.getVersion()).isEqualTo("2.0.0"));
        assertThat(provenanceRepository.findByOrgKeyAndKindAndKey(ORG, "entity", "barn")).hasValueSatisfying(
                row -> assertThat(row.getStarterVersion()).isEqualTo("2.0.0"));
        assertThat(provenanceRepository.findByOrgKeyAndKindAndKey(ORG, "entity", "location")).isEmpty();
    }

    @Test
    void keepsWhatTheOrganizationCustomized_whetherTheBundleStillBringsItOrNot() {
        entities.store.get(ORG).put("item", "{\"edited\":true}");
        entities.store.get(ORG).put("location", "{\"edited\":true}");

        UpgradeReport report = upgrade.provision(ORG, "inventory", "2.0.0", "staff");

        assertThat(items(report)).containsEntry("entity:item", "KEEP_CUSTOMIZED").containsEntry("entity:location", "KEEP_CUSTOMIZED")
                .containsEntry("entity:barn", "ADD");
        assertThat(entities.store.get(ORG)).containsEntry("item", "{\"edited\":true}").containsKey("location");
        assertThat(calls).contains("entity:only:barn").doesNotContain("entity:removeDrafts:location");
    }

    @Test
    void doesNotBringBackWhatTheOrganizationDeleted() {
        entities.store.get(ORG).remove("item");

        UpgradeReport report = upgrade.provision(ORG, "inventory", "2.0.0", "staff");

        assertThat(items(report)).containsEntry("entity:item", "KEEP_DELETED");
        assertThat(entities.store.get(ORG)).doesNotContainKey("item");
    }

    @Test
    void keepsADroppedDefinitionSomethingStillDependsOn() {
        entities.refusals.put("location", "still has objects");

        UpgradeReport report = upgrade.provision(ORG, "inventory", "2.0.0", "staff");

        assertThat(items(report)).containsEntry("entity:location", "KEEP_IN_USE");
        assertThat(report.items()).filteredOn(item -> item.key().equals("location")).singleElement()
                .satisfies(item -> assertThat(item.note()).contains("still has objects"));
    }

    @Test
    void aDryRunChangesNothing() {
        UpgradeReport report = upgrade.execute(ORG, "inventory", "2.0.0", true, "alice");

        assertThat(report.status()).isEqualTo(UpgradeReport.Status.WOULD_APPLY);
        assertThat(items(report)).containsEntry("entity:barn", "ADD");
        assertThat(installedRepository.findByOrgKeyAndStarterId(ORG, "inventory")).hasValueSatisfying(
                installed -> assertThat(installed.getVersion()).isEqualTo("1.0.0"));
        assertThat(provenanceRepository.findByOrgKeyAndKindAndKey(ORG, "entity", "barn")).isEmpty();
    }

    @Test
    void aKindThatCannotBeUpgradedYetIsLeftAloneAndNamed() {
        UpgradeReport report = upgrade.provision(ORG, "inventory", "2.0.0", "staff");

        assertThat(report.skippedKinds()).containsExactly("rule");
        assertThat(calls).noneMatch(call -> call.startsWith("rule:"));
    }

    @Test
    void aRejectedFileRollsEverythingBack() {
        publish("3.0.0", "item\n!broken\n", "positive\n");
        upgrade.provision(ORG, "inventory", "2.0.0", "staff");

        UpgradeReport report = upgrade.provision(ORG, "inventory", "3.0.0", "staff");

        assertThat(report.status()).isEqualTo(UpgradeReport.Status.REJECTED);
        assertThat(report.errors()).extracting(ImportReport.Error::message).contains("broken");
        assertThat(installedRepository.findByOrgKeyAndStarterId(ORG, "inventory")).hasValueSatisfying(
                installed -> assertThat(installed.getVersion()).isEqualTo("2.0.0"));
    }

    @Test
    void refusesWhatIsNotAnUpgradeToTheNextVersion() {
        publish("3.0.0", "item\n", "positive\n");

        assertThat(upgrade.provision(ORG, "inventory", "1.0.0", "staff").errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("newer"));
        assertThat(upgrade.provision(ORG, "inventory", "3.0.0", "staff").errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("Upgrade to 2.0.0 first"));
        assertThat(upgrade.provision("nobody", "inventory", "2.0.0", "staff").errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("not installed"));
    }

    @Test
    void anUpgradeBundleThatIsNotTheRequestedStarterIsRefused() {
        byte[] other = TestBundles.zip(Map.of("manifest.yaml", TestBundles.MANIFEST.replace("id: inventory", "id: other").replace("version: 1.0.0", "version: 2.0.0"),
                "entities/item.yaml", "item\n", "rules/item-rules.yaml", "x\n"));
        bundles.put("inventory/2.0.0/bundle.zip", other);
        List<StarterCatalog.Version> versions = new ArrayList<>(catalog.starters().getFirst().versions());
        versions.replaceAll(version -> version.version().equals("2.0.0")
                ? new StarterCatalog.Version("2.0.0", null, null, 1, BundleReader.sha256(other), "inventory/2.0.0/bundle.zip") : version);
        catalog = new StarterCatalog(1, List.of(new StarterCatalog.Starter("inventory", "Inventory", null, null, null, null, null, null, versions)));

        assertThat(upgrade.provision(ORG, "inventory", "2.0.0", "staff").errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("is starter 'other'"));
    }
}
