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
import com.processpuzzle.starter.domain.OfferedVersionRepository;
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
    private OfferedVersionRepository offeredRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> calls = new ArrayList<>();
    private final Map<String, byte[]> bundles = new LinkedHashMap<>();
    private StarterCatalog catalog;
    private FakeParticipant entities;
    private FakeParticipant rules;
    private UpgradeStarter upgrade;
    private final List<com.processpuzzle.core.definition.MigrationStep> queued = new ArrayList<>();
    private boolean hostMigrates;
    private OfferStarterVersion offer;
    private ListInstalledStarters listing;

    @BeforeEach
    void setUp() {
        provenanceRepository.deleteAll();
        installedRepository.deleteAll();
        dataSpaceRepository.deleteAll();
        offeredRepository.deleteAll();
        queued.clear();
        hostMigrates = true;
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
                provenanceRepository, installedRepository, commonRules, new KeyPrefixRules(participants), new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(com.processpuzzle.core.definition.ModuleMountPort.class), transactionManager, provider());
        InstallStarter installer = new InstallStarter(guard, new BrowseCatalog(registry), registry, importBundle);
        upgrade = new UpgradeStarter(guard, installer, registry, new BundleReader(new StarterProperties()),
                new UpgradePlanner(participants, provenanceRepository), commonRules, new KeyPrefixRules(participants), provenanceRepository,
                installedRepository, offeredRepository, queueProvider(), transactionManager, provider());
        offer = new OfferStarterVersion(new BrowseCatalog(registry), installer, new BundleReader(new StarterProperties()), commonRules,
                offeredRepository, provider());
        listing = new ListInstalledStarters(guard, installedRepository, provenanceRepository, participants, offeredRepository);
        installer.provision(ORG, "inventory", "1.0.0", "seed");
        calls.clear();
    }

    /** A queue that takes the steps down and refuses an operation named 'explode'. */
    @SuppressWarnings("unchecked")
    private ObjectProvider<com.processpuzzle.core.definition.MigrationStepQueue> queueProvider() {
        ObjectProvider<com.processpuzzle.core.definition.MigrationStepQueue> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenAnswer(call -> hostMigrates ? (com.processpuzzle.core.definition.MigrationStepQueue) (org, steps) -> {
            steps.forEach(step -> {
                if ("explode".equals(step.operation())) {
                    throw new IllegalArgumentException("Nothing runs the migration operation 'explode' on a " + step.kind() + ".");
                }
            });
            queued.addAll(steps);
        } : null);
        return provider;
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

    /** The installed 1.0.0 is an application of its own; a domain-kind 2.0.0 adds modules to the customer's: not an upgrade. */
    @Test
    void anUpgradeThatWouldChangeTheKindOfTheStarterIsRefused() {
        byte[] domain = TestBundles.zip(Map.of("manifest.yaml",
                "manifestVersion: 3\nid: inventory\nname: Inventory\nversion: 2.0.0\ndefinitionSchemaVersion: 1\nkeyPrefix: inv\n"
                        + "contents:\n  entities:\n    - entities/item.yaml\n  rules:\n    - rules/item-rules.yaml\n",
                "entities/item.yaml", "inv-item\n", "rules/item-rules.yaml", "inv-positive\n"));
        bundles.put("inventory/2.0.0/bundle.zip", domain);
        List<StarterCatalog.Version> versions = new ArrayList<>(catalog.starters().getFirst().versions());
        versions.replaceAll(version -> version.version().equals("2.0.0")
                ? new StarterCatalog.Version("2.0.0", null, null, 1, BundleReader.sha256(domain), "inventory/2.0.0/bundle.zip") : version);
        catalog = new StarterCatalog(1, List.of(new StarterCatalog.Starter("inventory", "Inventory", null, null, null, null, null, null, versions)));

        UpgradeReport report = upgrade.provision(ORG, "inventory", "2.0.0", "staff");

        assertThat(report.errors()).extracting(ImportReport.Error::message)
                .anySatisfy(message -> assertThat(message).contains("an application of its own").contains("cannot be exchanged by an upgrade"));
        assertThat(installedRepository.findByOrgKeyAndStarterId(ORG, "inventory")).hasValueSatisfying(
                installed -> assertThat(installed.getVersion()).isEqualTo("1.0.0"));
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

    @Test
    void withoutAVersionAnOrganizationGoesToTheNextOfferedOne_andNeverPastIt() {
        publish("3.0.0", "item\n", "positive\n");
        offer.offer("inventory", "3.0.0", "staff");

        UpgradeReport first = upgrade.provision(ORG, "inventory", null, "alice");
        UpgradeReport second = upgrade.provision(ORG, "inventory", null, "alice");
        UpgradeReport third = upgrade.provision(ORG, "inventory", null, "alice");

        assertThat(first.toVersion()).isEqualTo("2.0.0");
        assertThat(second.toVersion()).isEqualTo("3.0.0");
        assertThat(third.status()).isEqualTo(UpgradeReport.Status.REJECTED);
        assertThat(third.errors()).singleElement().satisfies(error -> assertThat(error.message()).contains("No newer version"));
    }

    @Test
    void withoutAVersionAndNothingOffered_thereIsNothingToUpgradeTo() {
        assertThat(upgrade.provision(ORG, "inventory", null, "alice").errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("No newer version"));
    }

    @Test
    void theListingTellsAnUpgradeIsAvailable_untilTheOrganizationHasApplied() {
        offer.offer("inventory", "2.0.0", "staff");

        assertThat(listing.execute(ORG)).singleElement().satisfies(view -> {
            assertThat(view.upgradeAvailable()).isTrue();
            assertThat(view.offeredVersion()).isEqualTo("2.0.0");
        });

        upgrade.provision(ORG, "inventory", null, "alice");

        assertThat(listing.execute(ORG)).singleElement().satisfies(view -> {
            assertThat(view.upgradeAvailable()).isFalse();
            assertThat(view.offeredVersion()).isNull();
        });
    }

    @Test
    void aRolloutOffersANewerVersionOnly_andWithdrawingStopsTheOffer() {
        offer.offer("inventory", "2.0.0", "staff");

        assertThat(offer.offeredVersion("inventory")).contains("2.0.0");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> offer.offer("inventory", "1.0.0", "staff"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("offered already");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> offer.offer("inventory", "9.9.9", "staff"))
                .isInstanceOf(StarterNotFoundException.class);

        offer.withdraw("inventory");

        assertThat(offer.offeredVersion("inventory")).isEmpty();
        assertThat(listing.execute(ORG)).singleElement().satisfies(view -> assertThat(view.upgradeAvailable()).isFalse());
    }

    @Test
    void aVersionNeedingANewerCommonCannotBeOfferedYet() {
        String tenant = """
                manifestVersion: 2
                id: inventory
                name: Inventory
                version: 3.0.0
                definitionSchemaVersion: 1
                common:
                  id: inventory-common
                  minVersion: 2.0.0
                contents:
                  entities:
                    - entities/item.yaml
                """;
        byte[] bytes = TestBundles.zip(Map.of("manifest.yaml", tenant, "entities/item.yaml", "item\n"));
        bundles.put("inventory/3.0.0/bundle.zip", bytes);
        List<StarterCatalog.Version> versions = new ArrayList<>(catalog.starters().getFirst().versions());
        versions.add(new StarterCatalog.Version("3.0.0", null, null, 1, BundleReader.sha256(bytes), "inventory/3.0.0/bundle.zip"));
        catalog = new StarterCatalog(1, List.of(new StarterCatalog.Starter("inventory", "Inventory", null, null, null, null, null, null, versions)));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> offer.offer("inventory", "3.0.0", "staff"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not exist yet");
        assertThat(offer.offeredVersion("inventory")).isEmpty();
    }

    // --- migrations the bundle brings ---------------------------------------------------

    private static final String RENAME = """
            migration:
              from: 1.0.0
              to: 2.0.0
              steps:
                - kind: entity
                  key: item
                  operation: renameAttribute
                  subject: name
                  parameters: { to: title }
                - id: fill-it
                  kind: entity
                  key: item
                  operation: fillAttribute
                  subject: status
                  parameters: { value: NEW }
            """;

    /** Replaces version 2.0.0 in the catalog by one that carries migration files. */
    private void publish2WithMigrations(Map<String, String> migrationFiles) {
        StringBuilder listed = new StringBuilder();
        migrationFiles.keySet().forEach(path -> listed.append("  - ").append(path).append('\n'));
        String manifest = "manifestVersion: 2\nid: inventory\nname: Inventory\nversion: 2.0.0\ndefinitionSchemaVersion: 1\n"
                + "contents:\n  entities:\n    - entities/item.yaml\n  rules:\n    - rules/item-rules.yaml\nmigrations:\n" + listed;
        Map<String, String> files = new LinkedHashMap<>(migrationFiles);
        files.put("manifest.yaml", manifest);
        files.put("entities/item.yaml", "item\nbarn\n");
        files.put("rules/item-rules.yaml", "positive\n");
        byte[] bytes = TestBundles.zip(files);
        bundles.put("inventory/2.0.0/bundle.zip", bytes);
        List<StarterCatalog.Version> versions = new ArrayList<>(catalog.starters().getFirst().versions());
        versions.replaceAll(version -> version.version().equals("2.0.0")
                ? new StarterCatalog.Version("2.0.0", null, null, 1, BundleReader.sha256(bytes), "inventory/2.0.0/bundle.zip") : version);
        catalog = new StarterCatalog(1, List.of(new StarterCatalog.Starter("inventory", "Inventory", null, null, null, null, null, null, versions)));
    }

    @Test
    void queuesTheMigrationOfExactlyThisStep_inOrder_andReportsIt() {
        publish2WithMigrations(Map.of("migrations/1.0.0-to-2.0.0.yaml", RENAME));

        UpgradeReport report = upgrade.provision(ORG, "inventory", "2.0.0", "staff");

        assertThat(report.status()).isEqualTo(UpgradeReport.Status.APPLIED);
        assertThat(queued).extracting(step -> step.operation() + ":" + step.subject() + ":" + step.id())
                .containsExactly("renameAttribute:name:step-1", "fillAttribute:status:fill-it");
        assertThat(queued.getFirst().parameters()).containsEntry("to", "title");
        assertThat(report.migrationSteps()).extracting(UpgradeReport.MigrationStepInfo::operation, UpgradeReport.MigrationStepInfo::subject)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("renameAttribute", "name"), org.assertj.core.groups.Tuple.tuple("fillAttribute", "status"));
    }

    @Test
    void aMigrationForAnotherStepIsNotQueued() {
        String earlier = RENAME.replace("from: 1.0.0", "from: 0.9.0").replace("to: 2.0.0", "to: 1.0.0");
        publish2WithMigrations(Map.of("migrations/0.9.0-to-1.0.0.yaml", earlier));

        UpgradeReport report = upgrade.provision(ORG, "inventory", "2.0.0", "staff");

        assertThat(report.status()).isEqualTo(UpgradeReport.Status.APPLIED);
        assertThat(queued).isEmpty();
        assertThat(report.migrationSteps()).isEmpty();
    }

    @Test
    void aDryRunReportsTheStepsToo() {
        publish2WithMigrations(Map.of("migrations/1.0.0-to-2.0.0.yaml", RENAME));

        UpgradeReport report = upgrade.execute(ORG, "inventory", "2.0.0", true, "alice");

        assertThat(report.status()).isEqualTo(UpgradeReport.Status.WOULD_APPLY);
        assertThat(report.migrationSteps()).hasSize(2);
    }

    @Test
    void aStepNothingRunsRefusesTheUpgrade_andRollsItBack() {
        publish2WithMigrations(Map.of("migrations/1.0.0-to-2.0.0.yaml", RENAME.replace("renameAttribute", "explode")));

        UpgradeReport report = upgrade.provision(ORG, "inventory", "2.0.0", "staff");

        assertThat(report.status()).isEqualTo(UpgradeReport.Status.REJECTED);
        assertThat(report.errors()).singleElement().satisfies(error -> {
            assertThat(error.file()).isEqualTo("migrations/1.0.0-to-2.0.0.yaml");
            assertThat(error.message()).contains("explode");
        });
        assertThat(installedRepository.findByOrgKeyAndStarterId(ORG, "inventory")).hasValueSatisfying(
                installed -> assertThat(installed.getVersion()).isEqualTo("1.0.0"));
        assertThat(queued).isEmpty();
    }

    @Test
    void anApplicationThatCannotMigrateRefusesABundleThatNeedsIt() {
        publish2WithMigrations(Map.of("migrations/1.0.0-to-2.0.0.yaml", RENAME));
        hostMigrates = false;

        UpgradeReport report = upgrade.provision(ORG, "inventory", "2.0.0", "staff");

        assertThat(report.status()).isEqualTo(UpgradeReport.Status.REJECTED);
        assertThat(report.errors()).singleElement().satisfies(error -> assertThat(error.message()).contains("cannot do"));
    }
}
