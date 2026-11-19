package com.processpuzzle.starter.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.processpuzzle.core.definition.DefinitionImportParticipant;
import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.InstanceDataProbe;
import com.processpuzzle.core.definition.ModuleMountPort;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationAccessPolicy;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.starter.StarterProperties;
import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.bundle.TestBundles;
import com.processpuzzle.starter.domain.DefinitionProvenance;
import com.processpuzzle.starter.domain.DefinitionProvenanceRepository;
import com.processpuzzle.starter.domain.DataSpaceRepository;
import com.processpuzzle.starter.domain.InstalledStarterRepository;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
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
 * The orchestration over real repositories and a real transaction manager, with participants faked.
 * Not transactional itself, so the importer's own transaction is the outermost one, as in production.
 */
@DataJpaTest(showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EntityScan("com.processpuzzle.starter.domain")
@EnableJpaRepositories("com.processpuzzle.starter.domain")
class ImportBundleTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private DefinitionProvenanceRepository provenanceRepository;
    @Autowired
    private InstalledStarterRepository installedStarterRepository;
    @Autowired
    private DataSpaceRepository dataSpaceRepository;
    @Autowired
    private com.processpuzzle.starter.domain.OfferedVersionRepository offeredRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> calls = new ArrayList<>();
    private OrganizationAccessPolicy policy;
    private FakeParticipant entities;
    private FakeParticipant rules;
    private FakeParticipant modules;
    private final RecordingMountPort mountPort = new RecordingMountPort();
    private long entityObjects;
    private final Map<String, Long> objectsByType = new java.util.HashMap<>();
    private ImportBundle importBundle;
    private UninstallStarter uninstallStarter;
    private ListInstalledStarters listInstalledStarters;

    @BeforeEach
    void setUp() {
        provenanceRepository.deleteAll();
        installedStarterRepository.deleteAll();
        dataSpaceRepository.deleteAll();
        policy = mock(OrganizationAccessPolicy.class);
        OrganizationGuard guard = new OrganizationGuard(provider(policy));
        entities = new FakeParticipant(DefinitionKinds.ENTITY, DefinitionKinds.ENTITY_ORDER, calls);
        rules = new FakeParticipant(DefinitionKinds.RULE, DefinitionKinds.RULE_ORDER, calls);
        modules = new FakeParticipant(DefinitionKinds.MODULE, DefinitionKinds.MODULE_ORDER, calls);
        mountPort.reset();
        entityObjects = 0;
        objectsByType.clear();
        InstanceDataProbe objects = new InstanceDataProbe() {
            @Override
            public String label() {
                return "entity objects";
            }

            @Override
            public long count(String orgKey) {
                return entityObjects;
            }

            @Override
            public String definitionKind() {
                return DefinitionKinds.ENTITY;
            }

            @Override
            public long count(String orgKey, java.util.Collection<String> definitionKeys) {
                return definitionKeys.stream().mapToLong(key -> objectsByType.getOrDefault(key, 0L)).sum();
            }
        };
        // Deliberately registered out of order: the importer must sort.
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("rules", rules);
        factory.addBean("entities", entities);
        factory.addBean("modules", modules);
        factory.addBean("objects", objects);
        ImportParticipants participants = new ImportParticipants(factory.getBeanProvider(DefinitionImportParticipant.class),
                factory.getBeanProvider(InstanceDataProbe.class));
        importBundle = new ImportBundle(guard, new BundleReader(new StarterProperties()), participants,
                provenanceRepository, installedStarterRepository, commonRules(), new KeyPrefixRules(participants), provider(mountPort), transactionManager, provider(null));
        listInstalledStarters = new ListInstalledStarters(guard, installedStarterRepository, provenanceRepository, participants, offeredRepository);
        uninstallStarter = new UninstallStarter(guard, participants, provenanceRepository, installedStarterRepository, provider(mountPort), transactionManager);
        entities.upgradeable = true;
        rules.upgradeable = true;
        modules.upgradeable = true;
    }

    @Test
    void appliesEveryKindInOrderAndRecordsProvenance() {
        ImportReport report = importBundle.execute("acme", bundle("item\nlocation\n", "positive-qty\n"), false, "alice");

        assertThat(report.status()).isEqualTo(ImportReport.Status.APPLIED);
        assertThat(report.created()).isEqualTo(3);
        assertThat(calls).containsExactly(
                "module:removeAll", "rule:removeAll", "entity:removeAll", "entity:entities/item.yaml", "rule:rules/item-rules.yaml");
        assertThat(report.items()).extracting(ImportReport.Item::kind, ImportReport.Item::key)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("entity", "item"),
                        org.assertj.core.groups.Tuple.tuple("entity", "location"),
                        org.assertj.core.groups.Tuple.tuple("rule", "positive-qty"));

        DefinitionProvenance item = provenanceRepository.findByOrgKeyAndKindAndKey("acme", "entity", "item").orElseThrow();
        assertThat(item.getStarterId()).isEqualTo("inventory");
        assertThat(item.getStarterVersion()).isEqualTo("1.0.0");
        assertThat(item.getContentHash()).isEqualTo(ImportBundle.hash("{\"key\":\"item\"}"));
        assertThat(item.getImportedBy()).isEqualTo("alice");
        assertThat(installedStarterRepository.findByOrgKeyAndStarterId("acme", "inventory")).isPresent();
    }

    @Test
    void aDryRunReportsButRecordsNothing() {
        ImportReport report = importBundle.execute("acme", bundle("item\n", "positive-qty\n"), true, null);

        assertThat(report.status()).isEqualTo(ImportReport.Status.WOULD_APPLY);
        assertThat(report.dryRun()).isTrue();
        assertThat(report.created()).isEqualTo(2);
        assertThat(provenanceRepository.count()).isZero();
        assertThat(installedStarterRepository.count()).isZero();
    }

    @Test
    void aReimportReportsUpdates() {
        importBundle.execute("acme", bundle("item\n", "positive-qty\n"), false, null);

        ImportReport report = importBundle.execute("acme", bundle("item\n", "positive-qty\n"), false, null);

        assertThat(report.items()).extracting(ImportReport.Item::action).containsOnly(ImportReport.Action.UPDATE);
        assertThat(provenanceRepository.count()).isEqualTo(2);
        assertThat(installedStarterRepository.count()).isEqualTo(1);
    }

    @Test
    void aRefusingParticipantStopsTheImportAndRecordsNothing() {
        assertThatThrownBy(() -> importBundle.execute("acme", bundle("item\n", "!bad expression\n"), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(ImportRejectedException.Reason.INVALID);
                    assertThat(e.getReport().status()).isEqualTo(ImportReport.Status.REJECTED);
                    assertThat(e.getReport().errors()).containsExactly(
                            new ImportReport.Error("rules/item-rules.yaml", "bad expression"));
                });
        assertThat(provenanceRepository.count()).isZero();
    }

    @Test
    void rejectsAKindNoParticipantImports() {
        String manifest = TestBundles.MANIFEST + "  workflows:\n    - workflows/flow.yaml\n";
        Map<String, String> files = TestBundles.inventory();
        files.put("manifest.yaml", manifest);
        files.put("workflows/flow.yaml", "x");

        assertThatThrownBy(() -> importBundle.execute("acme", zip(files), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e ->
                        assertThat(e.getReport().errors()).extracting(ImportReport.Error::message)
                                .containsExactly("This platform cannot import 'workflows' definitions yet."));
        assertThat(calls).isEmpty();
    }

    /** Assets and translations are kinds like the others: a deployment that hosts no participant for them refuses them. */
    @Test
    void rejectsAssetsAndTranslationsNoParticipantImports() {
        String manifest = TestBundles.MANIFEST + "  assets:\n    - assets/apps/app/a.svg\n  translations:\n    - translations/modules/m/de.json\n";
        Map<String, String> files = TestBundles.inventory();
        files.put("manifest.yaml", manifest);
        files.put("assets/apps/app/a.svg", "<svg/>");
        files.put("translations/modules/m/de.json", "{}");

        assertThatThrownBy(() -> importBundle.execute("acme", zip(files), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e ->
                        assertThat(e.getReport().errors()).extracting(ImportReport.Error::message)
                                .containsExactly("This platform cannot import 'assets' definitions yet.",
                                        "This platform cannot import 'translations' definitions yet."));
        assertThat(calls).isEmpty();
    }

    @Test
    void rejectsAnUnknownGroup() {
        Map<String, String> files = TestBundles.inventory();
        files.put("manifest.yaml", TestBundles.MANIFEST + "  gadgets: []\n");

        assertThatThrownBy(() -> importBundle.execute("acme", zip(files), false, null))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessageContaining("Unknown contents group 'gadgets'");
    }

    @Test
    void replacesWhatTheOrganizationHad() {
        importBundle.execute("acme", bundle("item\nlocation\n", "positive-qty\n"), false, null);
        Map<String, String> other = TestBundles.inventory();
        other.put("manifest.yaml", TestBundles.MANIFEST.replace("id: inventory", "id: warehouse"));
        other.put("entities/item.yaml", "item\nshelf\n");
        other.put("rules/item-rules.yaml", "");

        ImportReport report = importBundle.execute("acme", zip(other), false, "bob");

        assertThat(report.items()).extracting(ImportReport.Item::kind, ImportReport.Item::key, ImportReport.Item::action)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("entity", "item", ImportReport.Action.UPDATE),
                        org.assertj.core.groups.Tuple.tuple("entity", "shelf", ImportReport.Action.CREATE),
                        org.assertj.core.groups.Tuple.tuple("rule", "positive-qty", ImportReport.Action.DELETE),
                        org.assertj.core.groups.Tuple.tuple("entity", "location", ImportReport.Action.DELETE));
        assertThat(report.deleted()).isEqualTo(2);
        assertThat(entities.store.get("acme")).containsOnlyKeys("item", "shelf");
        assertThat(rules.store.get("acme")).isEmpty();
        assertThat(installedStarterRepository.findAll()).singleElement()
                .satisfies(installed -> assertThat(installed.getStarterId()).isEqualTo("warehouse"));
        assertThat(provenanceRepository.findAll()).extracting(DefinitionProvenance::getKey).containsExactlyInAnyOrder("item", "shelf");
        assertThat(provenanceRepository.findAll()).extracting(DefinitionProvenance::getStarterId).containsOnly("warehouse");
    }

    @Test
    void refusesAnOrganizationThatHoldsInstanceData() {
        entityObjects = 3;

        assertThatThrownBy(() -> importBundle.execute("acme", bundle("item\n", ""), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(ImportRejectedException.Reason.HOLDS_INSTANCE_DATA);
                    assertThat(e.getMessage()).contains("it holds 3 entity objects");
                });
        assertThat(calls).isEmpty();
    }

    @Test
    void refusesABundleThatIsNotTheExpectedStarter() {
        assertThatThrownBy(() -> importBundle.execute("acme", bundle("item\n", ""), false, null,
                new ImportBundle.Expected("inventory", "2.0.0")))
                .isInstanceOfSatisfying(ImportRejectedException.class, e ->
                        assertThat(e.getMessage()).isEqualTo("The bundle is starter 'inventory' 1.0.0, not 'inventory' 2.0.0."));
        assertThat(calls).isEmpty();
    }

    @Test
    void acceptsABundleMatchingTheRequestedStarterAndVersion() {
        ImportReport report = importBundle.execute("acme", bundle("item\n", ""), false, "alice",
                new ImportBundle.Expected("inventory", "1.0.0"));

        assertThat(report.status()).isEqualTo(ImportReport.Status.APPLIED);
        assertThat(report.starterId()).isEqualTo("inventory");
        assertThat(report.version()).isEqualTo("1.0.0");
        assertThat(report.created()).isEqualTo(1);
    }

    @Test
    void refusesABundleWithTheWrongStarterIdEvenWhenTheVersionMatches() {
        ByteArrayInputStream input = bundle("item\n", "");
        ImportBundle.Expected expected = new ImportBundle.Expected("warehouse", "1.0.0");

        assertThatThrownBy(() -> importBundle.execute("acme", input, false, null, expected))
                .isInstanceOf(ImportRejectedException.class)
                .hasMessage("The bundle is starter 'inventory' 1.0.0, not 'warehouse' 1.0.0.");
        assertThat(calls).isEmpty();
        assertThat(installedStarterRepository.count()).isZero();
    }

    @Test
    void preservesUpdatesReportedByAParticipantDuringTheSameImport() {
        ImportReport report = importBundle.execute("acme", bundle("item\nitem\n", ""), false, null);

        assertThat(report.items()).extracting(ImportReport.Item::action)
                .containsExactly(ImportReport.Action.CREATE, ImportReport.Action.UPDATE);
        assertThat(provenanceRepository.count()).isEqualTo(1);
    }

    @Test
    void anUnreadableBundleIsInvalidRatherThanTooLarge() {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);

        assertThatThrownBy(() -> importBundle.execute("acme", input, true, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(ImportRejectedException.Reason.INVALID);
                    assertThat(e.getReport().dryRun()).isTrue();
                    assertThat(e.getReport().starterId()).isNull();
                    assertThat(e.getReport().version()).isNull();
                    assertThat(e.getMessage()).contains("empty or not a zip");
                });
        assertThat(calls).isEmpty();
    }

    @Test
    void anOversizedBundleIsTooLarge() {
        StarterProperties small = new StarterProperties();
        small.getBundle().setMaxFileBytes(4);
        ImportBundle strict = new ImportBundle(new OrganizationGuard(provider(policy)), new BundleReader(small),
                ImportParticipants.of(List.of(entities)), provenanceRepository, installedStarterRepository,
                commonRules(), new KeyPrefixRules(ImportParticipants.of(List.of(entities))), provider(null), transactionManager, provider(null));

        assertThatThrownBy(() -> strict.execute("acme", bundle("item\n", ""), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(ImportRejectedException.Reason.TOO_LARGE));
    }

    @Test
    void requiresDesignRights() {
        doThrow(new OrganizationAccessDeniedException("acme")).when(policy).requireDesign("acme");

        assertThatThrownBy(() -> importBundle.execute("acme", bundle("item\n", ""), false, null))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThat(calls).isEmpty();
    }

    @Test
    void listsInstalledStartersAndCountsCustomizedDefinitions() {
        importBundle.execute("acme", bundle("item\nlocation\n", "positive-qty\n"), false, "alice");
        assertThat(listInstalledStarters.execute("acme")).singleElement().satisfies(view -> {
            assertThat(view.starterId()).isEqualTo("inventory");
            assertThat(view.installedBy()).isEqualTo("alice");
            assertThat(view.customizedDefinitions()).isZero();
        });

        entities.store.get("acme").put("item", "{\"key\":\"item\",\"edited\":true}");
        rules.store.get("acme").remove("positive-qty");

        assertThat(listInstalledStarters.execute("acme")).singleElement()
                .extracting(ListInstalledStarters.View::customizedDefinitions).isEqualTo(2);
        assertThat(listInstalledStarters.execute("other")).isEmpty();
    }

    /** Only a starter that was added can be removed again; the list says which, so the designer offers Remove only there. */
    @Test
    void theListSaysWhichInstalledStartersAreDomainStarters() {
        importBundle.execute("acme", domainStarter("inventory", "inv", "inv-item\n", "inv-positive\n"), false, "alice");
        importBundle.execute("acme", domainStarter("billing", "bil", "bil-invoice\n", "bil-total\n"), false, "alice");
        importBundle.execute("other", bundle("item\n", "positive\n"), false, "alice");

        assertThat(listInstalledStarters.execute("acme")).extracting(ListInstalledStarters.View::starterId, ListInstalledStarters.View::domainStarter)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("billing", true), org.assertj.core.groups.Tuple.tuple("inventory", true));
        assertThat(listInstalledStarters.execute("other")).singleElement()
                .satisfies(view -> assertThat(view.domainStarter()).as("it replaced the definitions").isFalse());
    }

    private CommonRules commonRules() {
        return new CommonRules(dataSpaceRepository, installedStarterRepository, new RegisterDataSpace(dataSpaceRepository));
    }

    private static final String COMMON_MANIFEST = """
            manifestVersion: 2
            kind: common
            id: inventory-common
            name: Inventory Common
            version: %s
            definitionSchemaVersion: 1
            contents:
              entities:
                - entities/item.yaml
            """;

    private static final String TENANT_MANIFEST = """
            manifestVersion: 2
            id: inventory
            name: Inventory
            version: 1.0.0
            definitionSchemaVersion: 1
            common:
              id: inventory-common
              minVersion: %s
            contents:
              entities:
                - entities/item.yaml
            """;

    private ByteArrayInputStream commonBundle(String version) {
        return zip(Map.of("manifest.yaml", COMMON_MANIFEST.formatted(version), "entities/item.yaml", "entityDefinitions: []\n"));
    }

    private ByteArrayInputStream tenantBundle(String minVersion) {
        return zip(Map.of("manifest.yaml", TENANT_MANIFEST.formatted(minVersion), "entities/item.yaml", "entityDefinitions: []\n"));
    }

    @Test
    void aCommonBundleCreatesTheCommonOfItsStarter() {
        ImportReport report = importBundle.execute("inventory-common", commonBundle("1.0.0"), false, "staff");

        assertThat(report.status()).isEqualTo(ImportReport.Status.APPLIED);
        assertThat(dataSpaceRepository.findByStarterId("inventory")).hasValueSatisfying(
                space -> assertThat(space.getCommonKey()).isEqualTo("inventory-common"));
        assertThat(installedStarterRepository.findByOrgKeyAndStarterId("inventory-common", "inventory-common")).isPresent();
    }

    @Test
    void aDryRunOfACommonBundleCreatesNothing() {
        importBundle.execute("inventory-common", commonBundle("1.0.0"), true, null);

        assertThat(dataSpaceRepository.findAll()).isEmpty();
    }

    @Test
    void aCommonBundleInstallsOnlyIntoItsOwnOrganization() {
        assertThatThrownBy(() -> importBundle.execute("acme", commonBundle("1.0.0"), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e -> assertThat(e.getReport().errors())
                        .extracting(ImportReport.Error::message).singleElement().asString().contains("installs only into"));
        assertThat(calls).isEmpty();
    }

    @Test
    void aTenantBundleIsRefusedWhileItsCommonDoesNotExist() {
        assertThatThrownBy(() -> importBundle.execute("acme", tenantBundle("1.0.0"), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e -> assertThat(e.getReport().errors())
                        .extracting(ImportReport.Error::message).singleElement().asString().contains("does not exist yet"));
        assertThat(calls).isEmpty();
    }

    @Test
    void aTenantBundleIsRefusedWhileItsCommonIsOlderThanMinVersion() {
        importBundle.execute("inventory-common", commonBundle("1.0.0"), false, null);
        calls.clear();

        assertThatThrownBy(() -> importBundle.execute("acme", tenantBundle("1.2.0"), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e -> assertThat(e.getReport().errors())
                        .extracting(ImportReport.Error::message).singleElement().asString().contains("needs 'inventory-common' 1.2.0"));
        assertThat(calls).isEmpty();
    }

    @Test
    void aTenantBundleInstallsOnceItsCommonIsAtMinVersion() {
        importBundle.execute("inventory-common", commonBundle("1.2.0"), false, null);

        ImportReport report = importBundle.execute("acme", tenantBundle("1.2.0"), false, null);

        assertThat(report.status()).isEqualTo(ImportReport.Status.APPLIED);
    }

    @Test
    void aTenantBundleIsNeverInstalledIntoACommon() {
        importBundle.execute("inventory-common", commonBundle("1.0.0"), false, null);

        assertThatThrownBy(() -> importBundle.execute("inventory-common", tenantBundle("1.0.0"), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e -> assertThat(e.getReport().errors())
                        .extracting(ImportReport.Error::message).singleElement().asString().contains("is a common"));
    }

    private static final String DOMAIN_MANIFEST = "manifestVersion: 3\nid: inventory\nname: Inventory\nversion: 1.0.0\n"
            + "definitionSchemaVersion: 1\nkeyPrefix: inv\n"
            + "contents:\n  entities:\n    - entities/item.yaml\n  rules:\n    - rules/item-rules.yaml\n";

    private ByteArrayInputStream domainBundle(String manifestTail, String entityFile, String ruleFile) {
        return zip(Map.of("manifest.yaml", DOMAIN_MANIFEST + manifestTail, "entities/item.yaml", entityFile,
                "rules/item-rules.yaml", ruleFile));
    }

    @Test
    void aDomainStarterWhoseKeysCarryItsPrefixIsApplied() {
        ImportReport report = importBundle.execute("acme", domainBundle("", "inv-item\ninv-location\n", "inv-positive\n"), false, null);

        assertThat(report.status()).isEqualTo(ImportReport.Status.APPLIED);
        assertThat(report.created()).isEqualTo(3);
    }

    @Test
    void aDomainStarterWithAKeyOutsideItsPrefixIsRefusedBeforeAnythingIsWritten() {
        assertThatThrownBy(() -> importBundle.execute("acme", domainBundle("", "inv-item\nlocation\n", "positive\n"), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e -> assertThat(e.getReport().errors())
                        .extracting(ImportReport.Error::file, ImportReport.Error::message)
                        .containsExactly(
                                org.assertj.core.groups.Tuple.tuple("entities/item.yaml",
                                        "The entity 'location' does not start with the starter's key prefix 'inv-'."),
                                org.assertj.core.groups.Tuple.tuple("rules/item-rules.yaml",
                                        "The rule 'positive' does not start with the starter's key prefix 'inv-'.")));
        assertThat(calls).isEmpty();
    }

    @Test
    void aDomainStarterWhoseMountNamesNoModuleOfTheBundleIsRefused() {
        String mount = "mounts:\n  - moduleKey: inv-stock\n    basePath: stock\n";

        assertThatThrownBy(() -> importBundle.execute("acme", domainBundle(mount, "inv-item\n", "inv-positive\n"), false, null))
                .isInstanceOfSatisfying(ImportRejectedException.class, e -> assertThat(e.getReport().errors())
                        .extracting(ImportReport.Error::message).singleElement().asString()
                        .contains("names the module 'inv-stock', which the bundle does not define"));
    }

    @Test
    void aBundleBelowVersion3IsNotHeldToAPrefix() {
        ImportReport report = importBundle.execute("acme", bundle("item\n", "positive\n"), false, null);

        assertThat(report.status()).isEqualTo(ImportReport.Status.APPLIED);
    }

    // --- Domain starters are added, not replacing (business-starters design, decision 11) -------------------

    private static ByteArrayInputStream domainStarter(String id, String prefix, String entityFile, String ruleFile) {
        String manifest = DOMAIN_MANIFEST.replace("id: inventory", "id: " + id).replace("keyPrefix: inv", "keyPrefix: " + prefix);
        return zip(Map.of("manifest.yaml", manifest, "entities/item.yaml", entityFile, "rules/item-rules.yaml", ruleFile));
    }

    private void installBoth() {
        importBundle.execute("acme", domainStarter("inventory", "inv", "inv-item\ninv-location\n", "inv-positive\n"), false, "alice");
        importBundle.execute("acme", domainStarter("billing", "bil", "bil-invoice\n", "bil-total\n"), false, "alice");
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ImportRejectedException.Reason reason,
                                       String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ImportRejectedException.class, e -> {
            assertThat(e.getReason()).isEqualTo(reason);
            assertThat(e.getReport().errors()).extracting(ImportReport.Error::message).anyMatch(text -> text.contains(message));
        });
    }

    @Test
    void severalDomainStartersShareAnOrganizationAndNothingIsRemoved() {
        installBoth();

        assertThat(calls).noneMatch(call -> call.endsWith(":removeAll"));
        assertThat(installedStarterRepository.findByOrgKeyOrderByStarterId("acme"))
                .extracting(starter -> starter.getStarterId() + ":" + starter.isDomainStarter())
                .containsExactly("billing:true", "inventory:true");
        assertThat(provenanceRepository.findByOrgKeyAndStarterId("acme", "inventory")).hasSize(3);
        assertThat(provenanceRepository.findByOrgKeyAndStarterId("acme", "billing")).hasSize(2);
        assertThat(entities.store.get("acme")).containsKeys("inv-item", "inv-location", "bil-invoice");
    }

    @Test
    void aDomainStarterIsAddedEvenWhereTheOrganizationHoldsData() {
        entityObjects = 12;

        ImportReport report = importBundle.execute("acme", domainStarter("inventory", "inv", "inv-item\n", "inv-positive\n"), false, null);

        assertThat(report.status()).isEqualTo(ImportReport.Status.APPLIED);
        assertThat(report.deleted()).isZero();
    }

    @Test
    void aDomainStarterThatWouldOverwriteTheCustomersDefinitionIsRefusedAndNothingIsWritten() {
        entities.store.put("acme", new java.util.LinkedHashMap<>(Map.of("inv-item", "{\"key\":\"inv-item\"}")));

        assertRejected(() -> importBundle.execute("acme", domainStarter("inventory", "inv", "inv-item\ninv-location\n", "inv-positive\n"),
                false, null), ImportRejectedException.Reason.CONFLICT, "The entity 'inv-item' exists already; it belongs to the organization.");
        assertThat(calls).isEmpty();
        assertThat(installedStarterRepository.findByOrgKeyAndStarterId("acme", "inventory")).isEmpty();
    }

    @Test
    void aCollisionWithAnotherStartersDefinitionNamesThatStarter() {
        importBundle.execute("acme", domainStarter("inventory", "inv", "inv-item\n", "inv-positive\n"), false, null);
        calls.clear();

        // Same prefix on purpose: the packager forbids it, the importer must not rely on that.
        assertRejected(() -> importBundle.execute("acme", domainStarter("billing", "inv", "inv-item\n", "inv-total\n"), false, null),
                ImportRejectedException.Reason.CONFLICT, "belongs to the starter 'inventory'");
        assertThat(calls).isEmpty();
    }

    @Test
    void everyCollisionIsListedSoThatADryRunShowsThemAll() {
        entities.store.put("acme", new java.util.LinkedHashMap<>(Map.of("inv-item", "{}", "inv-location", "{}")));
        rules.store.put("acme", new java.util.LinkedHashMap<>(Map.of("inv-positive", "{}")));

        assertThatThrownBy(() -> importBundle.execute("acme", domainStarter("inventory", "inv", "inv-item\ninv-location\n", "inv-positive\n"),
                true, null)).isInstanceOfSatisfying(ImportRejectedException.class, e ->
                assertThat(e.getReport().errors()).extracting(ImportReport.Error::message).hasSize(3));
    }

    @Test
    void aDomainStarterThatIsInstalledAlreadyIsToBeUpgradedNotInstalledAgain() {
        importBundle.execute("acme", domainStarter("inventory", "inv", "inv-item\n", "inv-positive\n"), false, null);

        assertRejected(() -> importBundle.execute("acme", domainStarter("inventory", "inv", "inv-item\n", "inv-positive\n"), false, null),
                ImportRejectedException.Reason.CONFLICT, "installed already; upgrade it instead");
    }

    @Test
    void aDryRunOfADomainStarterWritesNoProvenanceAndNoInstallRow() {
        ImportReport report = importBundle.execute("acme", domainStarter("inventory", "inv", "inv-item\n", "inv-positive\n"), true, null);

        assertThat(report.status()).isEqualTo(ImportReport.Status.WOULD_APPLY);
        assertThat(installedStarterRepository.findByOrgKeyOrderByStarterId("acme")).isEmpty();
        assertThat(provenanceRepository.findByOrgKeyAndStarterId("acme", "inventory")).isEmpty();
    }

    @Test
    void aReplacingStarterIsRefusedWhileTheOrganizationHoldsADomainStarter() {
        importBundle.execute("acme", domainStarter("inventory", "inv", "inv-item\n", "inv-positive\n"), false, null);
        calls.clear();

        assertRejected(() -> importBundle.execute("acme", bundle("item\n", "positive\n"), false, null),
                ImportRejectedException.Reason.CONFLICT, "would remove the domain starters inventory");
        assertThat(calls).isEmpty();
    }

    @Test
    void aReplacingStarterStillReplacesWhereThereIsNoDomainStarter() {
        importBundle.execute("acme", bundle("item\n", "positive\n"), false, null);

        ImportReport report = importBundle.execute("acme", bundle("other\n", "positive\n"), false, null);

        assertThat(report.status()).isEqualTo(ImportReport.Status.APPLIED);
        assertThat(installedStarterRepository.findByOrgKeyOrderByStarterId("acme")).singleElement()
                .satisfies(starter -> assertThat(starter.isDomainStarter()).isFalse());
    }

    // --- Uninstall ---------------------------------------------------------------------------------------------

    @Test
    void uninstallingRemovesTheStartersOwnDefinitionsAndLeavesTheRest() {
        installBoth();
        entities.store.get("acme").put("customer-thing", "{}");
        calls.clear();

        ImportReport report = uninstallStarter.execute("acme", "inventory", false);

        assertThat(report.status()).isEqualTo(ImportReport.Status.APPLIED);
        assertThat(report.items()).extracting(ImportReport.Item::kind, ImportReport.Item::key, ImportReport.Item::action)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("rule", "inv-positive", ImportReport.Action.DELETE),
                        org.assertj.core.groups.Tuple.tuple("entity", "inv-item", ImportReport.Action.DELETE),
                        org.assertj.core.groups.Tuple.tuple("entity", "inv-location", ImportReport.Action.DELETE));
        // dependents first, and only this starter's keys asked for
        assertThat(calls).containsExactly("rule:removeDrafts:inv-positive", "entity:removeDrafts:inv-item,inv-location");
        assertThat(entities.store.get("acme")).containsOnlyKeys("bil-invoice", "customer-thing");
        assertThat(installedStarterRepository.findByOrgKeyAndStarterId("acme", "inventory")).isEmpty();
        assertThat(provenanceRepository.findByOrgKeyAndStarterId("acme", "inventory")).isEmpty();
        assertThat(installedStarterRepository.findByOrgKeyAndStarterId("acme", "billing")).isPresent();
        assertThat(provenanceRepository.findByOrgKeyAndStarterId("acme", "billing")).hasSize(2);
    }

    @Test
    void aDryRunOfAnUninstallChangesNothing() {
        installBoth();

        ImportReport report = uninstallStarter.execute("acme", "inventory", true);

        assertThat(report.status()).isEqualTo(ImportReport.Status.WOULD_APPLY);
        assertThat(report.deleted()).isEqualTo(3);
        assertThat(installedStarterRepository.findByOrgKeyAndStarterId("acme", "inventory")).isPresent();
        assertThat(provenanceRepository.findByOrgKeyAndStarterId("acme", "inventory")).hasSize(3);
    }

    @Test
    void anUninstallIsRefusedWhileTheOrganizationHoldsDataOfTheStartersEntityTypes() {
        installBoth();
        objectsByType.put("inv-item", 4L);
        calls.clear();

        assertRejected(() -> uninstallStarter.execute("acme", "inventory", false),
                ImportRejectedException.Reason.HOLDS_INSTANCE_DATA, "4 entity objects");
        assertThat(calls).isEmpty();
        assertThat(installedStarterRepository.findByOrgKeyAndStarterId("acme", "inventory")).isPresent();
    }

    @Test
    void dataOfAnotherStartersTypeDoesNotStopAnUninstall() {
        installBoth();
        objectsByType.put("bil-invoice", 9L);

        assertThat(uninstallStarter.execute("acme", "inventory", false).status()).isEqualTo(ImportReport.Status.APPLIED);
        assertRejected(() -> uninstallStarter.execute("acme", "billing", false),
                ImportRejectedException.Reason.HOLDS_INSTANCE_DATA, "9 entity objects");
    }

    @Test
    void aDefinitionSomethingElseStillNeedsStopsTheWholeUninstall() {
        installBoth();
        entities.refusals.put("inv-item", "a state machine of the customer refers to it");

        assertRejected(() -> uninstallStarter.execute("acme", "inventory", false),
                ImportRejectedException.Reason.CONFLICT, "The entity 'inv-item' stays: a state machine of the customer refers to it");
        assertThat(installedStarterRepository.findByOrgKeyAndStarterId("acme", "inventory")).isPresent();
        assertThat(provenanceRepository.findByOrgKeyAndStarterId("acme", "inventory")).hasSize(3);
    }

    @Test
    void aStarterThatReplacedTheOrganizationsDefinitionsCannotBeUninstalled() {
        importBundle.execute("acme", bundle("item\n", "positive\n"), false, null);

        assertRejected(() -> uninstallStarter.execute("acme", "inventory", false),
                ImportRejectedException.Reason.INVALID, "replaced the organization's definitions");
    }

    @Test
    void anUninstallOfAStarterTheOrganizationDoesNotHoldIsNotFound() {
        assertThatThrownBy(() -> uninstallStarter.execute("acme", "inventory", false))
                .isInstanceOf(StarterNotFoundException.class);
    }

    @Test
    void anUninstallNeedsDesignRights() {
        installBoth();
        doThrow(new OrganizationAccessDeniedException("acme")).when(policy).requireDesign("acme");

        assertThatThrownBy(() -> uninstallStarter.execute("acme", "inventory", false)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThat(installedStarterRepository.findByOrgKeyAndStarterId("acme", "inventory")).isPresent();
    }

    // --- Mounting the modules into the application ---------------------------------------------------------------

    private static final String MOUNT = "mounts:\n  - moduleKey: inv-stock\n    basePath: stock\n"
            + "    nav: { icon: inventory, labelKey: nav.title }\n    launcher: { titleKey: launcher.title, link: overview }\n";

    private static ByteArrayInputStream mountingStarter(String mounts) {
        String manifest = DOMAIN_MANIFEST.replace("  rules:", "  modules:\n    - modules/m.yaml\n  rules:") + mounts;
        return zip(Map.of("manifest.yaml", manifest, "entities/item.yaml", "inv-item\n", "rules/item-rules.yaml", "inv-positive\n",
                "modules/m.yaml", "inv-stock\n"));
    }

    @Test
    void theManifestsMountsReachThePortAsHintsAndTheReportCountsThemAsMounts() {
        mountPort.next = new ModuleMountPort.MountOutcome(List.of("inv-stock"), Map.of(), true);

        ImportReport report = importBundle.execute("acme", mountingStarter(MOUNT), false, null);

        assertThat(mountPort.mountCalls).singleElement().satisfies(hints -> assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.moduleKey()).isEqualTo("inv-stock");
            assertThat(hint.basePath()).isEqualTo("stock");
            assertThat(hint.nav()).isEqualTo(new ModuleMountPort.Nav("inventory", "nav.title"));
            assertThat(hint.launcher().titleKey()).isEqualTo("launcher.title");
            assertThat(hint.launcher().link()).isEqualTo("overview");
        }));
        assertThat(report.items()).extracting(ImportReport.Item::kind, ImportReport.Item::key, ImportReport.Item::action)
                .contains(org.assertj.core.groups.Tuple.tuple("mount", "inv-stock", ImportReport.Action.CREATE));
        assertThat(report.warnings()).isEmpty();
        // a mount is not a definition: no provenance row, so an upgrade or uninstall never mistakes it for one
        assertThat(provenanceRepository.findByOrgKeyAndStarterId("acme", "inventory")).extracting(DefinitionProvenance::getKind)
                .doesNotContain("mount");
    }

    @Test
    void whatCouldNotBeMountedAndWhatIsLeftToReleaseAreWarningsNotRefusals() {
        mountPort.next = new ModuleMountPort.MountOutcome(List.of(), Map.of("inv-stock", "the path 'stock' is taken"), false);

        ImportReport skipped = importBundle.execute("acme", mountingStarter(MOUNT), false, null);

        assertThat(skipped.status()).isEqualTo(ImportReport.Status.APPLIED);
        assertThat(skipped.warnings()).containsExactly("The module 'inv-stock' is installed but not mounted: the path 'stock' is taken.");
        assertThat(installedStarterRepository.findByOrgKeyAndStarterId("acme", "inventory")).isPresent();
    }

    @Test
    void mountsLeftAsDraftsAreSaidSo() {
        mountPort.next = new ModuleMountPort.MountOutcome(List.of("inv-stock"), Map.of(), false);

        ImportReport report = importBundle.execute("acme", mountingStarter(MOUNT), false, null);

        assertThat(report.warnings()).containsExactly("The mounts are drafts of the application; release it to show them.");
    }

    @Test
    void aDryRunAsksThePortToo_itsWritesRollBackWithTheInstall() {
        mountPort.next = new ModuleMountPort.MountOutcome(List.of("inv-stock"), Map.of(), true);

        ImportReport report = importBundle.execute("acme", mountingStarter(MOUNT), true, null);

        assertThat(report.status()).isEqualTo(ImportReport.Status.WOULD_APPLY);
        assertThat(mountPort.mountCalls).hasSize(1);
        assertThat(report.items()).extracting(ImportReport.Item::kind).contains("mount");
    }

    @Test
    void theManifestsWithoutMountsAndReplacingStartersNeverCallThePort() {
        importBundle.execute("acme", domainStarter("inventory", "inv", "inv-item\n", "inv-positive\n"), false, null);
        importBundle.execute("other", bundle("item\n", "positive\n"), false, null);

        assertThat(mountPort.mountCalls).isEmpty();
    }

    @Test
    void anUninstallTakesTheStartersModulesOutOfTheApplication() {
        mountPort.next = new ModuleMountPort.MountOutcome(List.of("inv-stock"), Map.of(), true);
        importBundle.execute("acme", mountingStarter(MOUNT), false, null);
        mountPort.next = new ModuleMountPort.MountOutcome(List.of("inv-stock"), Map.of(), false);

        ImportReport report = uninstallStarter.execute("acme", "inventory", false);

        assertThat(mountPort.unmountCalls).singleElement().satisfies(keys -> assertThat(keys).containsExactly("inv-stock"));
        assertThat(report.items()).extracting(ImportReport.Item::kind, ImportReport.Item::key, ImportReport.Item::action)
                .contains(org.assertj.core.groups.Tuple.tuple("mount", "inv-stock", ImportReport.Action.DELETE),
                        org.assertj.core.groups.Tuple.tuple("module", "inv-stock", ImportReport.Action.DELETE));
        assertThat(report.warnings()).containsExactly(
                "The mounts are removed from the draft of the application; release it to remove them from the live one.");
    }

    /** Records what the starter asks of base-app, and answers {@link #next}. */
    private static final class RecordingMountPort implements ModuleMountPort {

        final List<List<MountHint>> mountCalls = new ArrayList<>();
        final List<java.util.Collection<String>> unmountCalls = new ArrayList<>();
        MountOutcome next = MountOutcome.nothing();

        void reset() {
            mountCalls.clear();
            unmountCalls.clear();
            next = MountOutcome.nothing();
        }

        @Override
        public MountOutcome mount(String orgKey, List<MountHint> hints) {
            mountCalls.add(hints);
            return next;
        }

        @Override
        public MountOutcome unmount(String orgKey, java.util.Collection<String> moduleKeys) {
            unmountCalls.add(List.copyOf(moduleKeys));
            return next;
        }
    }

    private ByteArrayInputStream bundle(String entityFile, String ruleFile) {
        Map<String, String> files = TestBundles.inventory();
        files.put("entities/item.yaml", entityFile);
        files.put("rules/item-rules.yaml", ruleFile);
        return zip(files);
    }

    private static ByteArrayInputStream zip(Map<String, String> files) {
        return new ByteArrayInputStream(TestBundles.zip(files));
    }

    /** A provider of {@code bean}, or an empty one for null. */
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean == null) {
            return (ObjectProvider<T>) factory.getBeanProvider(Void.class);
        }
        factory.addBean("bean", bean);
        return factory.getBeanProvider((Class<T>) bean.getClass());
    }
}
