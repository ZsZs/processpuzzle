package com.processpuzzle.starter.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.InstanceDataProbe;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationAccessPolicy;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.starter.StarterProperties;
import com.processpuzzle.starter.bundle.BundleReader;
import com.processpuzzle.starter.bundle.TestBundles;
import com.processpuzzle.starter.domain.DefinitionProvenance;
import com.processpuzzle.starter.domain.DefinitionProvenanceRepository;
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
    private PlatformTransactionManager transactionManager;

    private final List<String> calls = new ArrayList<>();
    private OrganizationAccessPolicy policy;
    private FakeParticipant entities;
    private FakeParticipant rules;
    private long entityObjects;
    private ImportBundle importBundle;
    private ListInstalledStarters listInstalledStarters;

    @BeforeEach
    void setUp() {
        provenanceRepository.deleteAll();
        installedStarterRepository.deleteAll();
        policy = mock(OrganizationAccessPolicy.class);
        OrganizationGuard guard = new OrganizationGuard(provider(policy));
        entities = new FakeParticipant(DefinitionKinds.ENTITY, DefinitionKinds.ENTITY_ORDER, calls);
        rules = new FakeParticipant(DefinitionKinds.RULE, DefinitionKinds.RULE_ORDER, calls);
        entityObjects = 0;
        InstanceDataProbe objects = new InstanceDataProbe() {
            @Override
            public String label() {
                return "entity objects";
            }

            @Override
            public long count(String orgKey) {
                return entityObjects;
            }
        };
        // Deliberately registered out of order: the importer must sort.
        ImportParticipants participants = ImportParticipants.of(List.of(rules, entities), List.of(objects));
        importBundle = new ImportBundle(guard, new BundleReader(new StarterProperties()), participants,
                provenanceRepository, installedStarterRepository, transactionManager, provider(null));
        listInstalledStarters = new ListInstalledStarters(guard, installedStarterRepository, provenanceRepository, participants);
    }

    @Test
    void appliesEveryKindInOrderAndRecordsProvenance() {
        ImportReport report = importBundle.execute("acme", bundle("item\nlocation\n", "positive-qty\n"), false, "alice");

        assertThat(report.status()).isEqualTo(ImportReport.Status.APPLIED);
        assertThat(report.created()).isEqualTo(3);
        assertThat(calls).containsExactly(
                "rule:removeAll", "entity:removeAll", "entity:entities/item.yaml", "rule:rules/item-rules.yaml");
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
    void anOversizedBundleIsTooLarge() {
        StarterProperties small = new StarterProperties();
        small.getBundle().setMaxFileBytes(4);
        ImportBundle strict = new ImportBundle(new OrganizationGuard(provider(policy)), new BundleReader(small),
                ImportParticipants.of(List.of(entities)), provenanceRepository, installedStarterRepository,
                transactionManager, provider(null));

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
