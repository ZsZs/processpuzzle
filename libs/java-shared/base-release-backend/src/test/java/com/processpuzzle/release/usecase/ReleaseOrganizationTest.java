package com.processpuzzle.release.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.PendingChange;
import com.processpuzzle.core.definition.ReleaseParticipant;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.core.tenancy.OrganizationAccessPolicy;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.release.domain.OrgReleaseItem;
import com.processpuzzle.release.domain.OrgReleaseContentRepository;
import com.processpuzzle.release.domain.OrgReleaseItemRepository;
import com.processpuzzle.release.domain.OrgReleaseRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
 * Not transactional itself, so the release's own transaction is the outermost one, as in production.
 */
@DataJpaTest(showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EntityScan("com.processpuzzle.release.domain")
@EnableJpaRepositories("com.processpuzzle.release.domain")
class ReleaseOrganizationTest {

    private static final String ORG = "my-org";
    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private OrgReleaseRepository releaseRepository;
    @Autowired
    private OrgReleaseItemRepository itemRepository;
    @Autowired
    private OrgReleaseContentRepository contentRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> calls = new ArrayList<>();
    private OrganizationAccessPolicy policy;
    private FakeParticipant entities;
    private FakeParticipant apps;
    private ReleaseOrganization releaseOrganization;
    private DescribePendingRelease describePendingRelease;
    private ListReleases listReleases;
    private ReleaseHistory releaseHistory;

    @BeforeEach
    void setUp() {
        contentRepository.deleteAll();
        itemRepository.deleteAll();
        releaseRepository.deleteAll();
        policy = mock(OrganizationAccessPolicy.class);
        entities = new FakeParticipant(DefinitionKinds.ENTITY, DefinitionKinds.ENTITY_ORDER, calls);
        apps = new FakeParticipant(DefinitionKinds.APP, DefinitionKinds.APP_ORDER, calls);
        useParticipants(apps, entities);
    }

    private void useParticipants(ReleaseParticipant... participants) {
        OrganizationGuard guard = new OrganizationGuard(provider(policy));
        ReleaseParticipants all = ReleaseParticipants.of(List.of(participants));
        ReleasePlanner planner = new ReleasePlanner(all);
        releaseOrganization = new ReleaseOrganization(guard, planner, all, releaseRepository, itemRepository,
                contentRepository, transactionManager, provider(Clock.fixed(NOW, ZoneOffset.UTC)));
        releaseHistory = new ReleaseHistory(itemRepository, contentRepository);
        describePendingRelease = new DescribePendingRelease(guard, planner);
        listReleases = new ListReleases(guard, releaseRepository, itemRepository);
    }

    @Test
    void releasesEveryKindInOrder_andRecordsWhatItCarried() {
        entities.withDraft("Claim", PendingChange.Change.CHANGED);
        apps.withDraft("app", PendingChange.Change.ADDED);

        ReleaseRecord released = releaseOrganization.execute(ORG, false, "designer");

        assertThat(calls).containsExactly("release entity", "release app");
        assertThat(released.release().getReleaseNumber()).isEqualTo(1);
        assertThat(released.release().getReleasedAt()).isEqualTo(NOW);
        assertThat(released.release().getReleasedBy()).isEqualTo("designer");
        assertThat(released.items()).extracting(OrgReleaseItem::getKind, OrgReleaseItem::getDefinitionKey)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("entity", "Claim"), org.assertj.core.groups.Tuple.tuple("app", "app"));
        assertThat(released.items().getFirst().getContentHash())
                .isEqualTo(ReleaseOrganization.sha256("{\"key\":\"Claim\"}")).hasSize(64);
    }

    /**
     * A release records only what it changed; what the organization ran at release N is rebuilt from the newest
     * item of each definition up to N, the removed ones left out.
     */
    @Test
    void keepsWhatEachReleaseCarried_soTheStateAtAnyReleaseCanBeReadBack() {
        entities.pending.add(new PendingChange("entity", "Claim", PendingChange.Change.ADDED, "{\"v\":1}"));
        entities.pending.add(new PendingChange("entity", "Order", PendingChange.Change.ADDED, "{\"order\":1}"));
        releaseOrganization.execute(ORG, false, null);

        entities.pending.clear();
        entities.pending.add(new PendingChange("entity", "Claim", PendingChange.Change.CHANGED, "{\"v\":2}"));
        entities.pending.add(new PendingChange("entity", "Order", PendingChange.Change.REMOVED, null));
        entities.pending.add(new PendingChange("entity", "Invoice", PendingChange.Change.ADDED, "{\"invoice\":1}"));
        releaseOrganization.execute(ORG, false, null);

        assertThat(releaseHistory.contentAt(ORG, 1))
                .extracting(ReleaseHistory.ReleasedDefinition::key, ReleaseHistory.ReleasedDefinition::content)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Claim", "{\"v\":1}"),
                        org.assertj.core.groups.Tuple.tuple("Order", "{\"order\":1}"));
        assertThat(releaseHistory.contentAt(ORG, 2))
                .extracting(ReleaseHistory.ReleasedDefinition::key, ReleaseHistory.ReleasedDefinition::content,
                        ReleaseHistory.ReleasedDefinition::releasedIn)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Claim", "{\"v\":2}", 2L),
                        org.assertj.core.groups.Tuple.tuple("Invoice", "{\"invoice\":1}", 2L));
        assertThat(releaseHistory.contentAt(ORG, 0)).isEmpty();
        assertThat(releaseHistory.contentAt("other-org", 2)).isEmpty();
    }

    @Test
    void numbersReleasesPerOrganization_andListsThemNewestFirst() {
        entities.withDraft("Claim", PendingChange.Change.ADDED);
        releaseOrganization.execute(ORG, false, null);
        releaseOrganization.execute(ORG, false, null);

        assertThat(listReleases.execute(ORG)).extracting(releaseRecord -> releaseRecord.release().getReleaseNumber())
                .containsExactly(2L, 1L);
        assertThat(listReleases.execute(ORG).getFirst().items()).extracting(OrgReleaseItem::getDefinitionKey)
                .containsExactly("Claim");
    }

    @Test
    void anUnresolvedRequiredReferenceRefusesTheRelease() {
        apps.withDraft("app", PendingChange.Change.CHANGED);
        apps.references.add(DefinitionReference.required(DefinitionKinds.APP, "app", DefinitionKinds.ENTITY, "Claim"));

        assertThatThrownBy(() -> releaseOrganization.execute(ORG, true, null))
                .isInstanceOfSatisfying(ReleaseRefusedException.class, refused -> {
                    assertThat(refused.getReason()).isEqualTo(ReleaseRefusedException.Reason.ERRORS);
                    assertThat(refused.getPending().problems()).singleElement().satisfies(problem -> {
                        assertThat(problem.isError()).isTrue();
                        assertThat(problem.errorId()).isEqualTo(ReleasePlanner.UNRESOLVED_REFERENCE);
                        assertThat(problem.key()).isEqualTo("app");
                    });
                });
        assertThat(calls).isEmpty();
        assertThat(releaseRepository.count()).isZero();
    }

    @Test
    void aReferenceToAnUnchangedDraftResolves() {
        entities.drafts.add("Claim");
        apps.withDraft("app", PendingChange.Change.CHANGED);
        apps.references.add(DefinitionReference.required(DefinitionKinds.APP, "app", DefinitionKinds.ENTITY, "Claim"));

        assertThat(describePendingRelease.execute(ORG).problems()).isEmpty();
    }

    @Test
    void aReferenceIntoAKindNobodyReleasesIsNotChecked() {
        apps.withDraft("app", PendingChange.Change.CHANGED);
        apps.references.add(DefinitionReference.required(DefinitionKinds.APP, "app", DefinitionKinds.DOCUMENT, "welcome"));

        assertThat(describePendingRelease.execute(ORG).problems()).isEmpty();
    }

    @Test
    void warningsNeedConfirming() {
        apps.withDraft("app", PendingChange.Change.CHANGED);
        apps.references.add(DefinitionReference.optional(DefinitionKinds.APP, "app", DefinitionKinds.ENTITY, "Claim"));

        assertThatThrownBy(() -> releaseOrganization.execute(ORG, false, null))
                .isInstanceOfSatisfying(ReleaseRefusedException.class,
                        refused -> assertThat(refused.getReason()).isEqualTo(ReleaseRefusedException.Reason.UNCONFIRMED_WARNINGS));

        releaseOrganization.execute(ORG, true, null);
        assertThat(calls).contains("release app");
    }

    @Test
    void aParticipantsOwnErrorRefusesTheRelease() {
        entities.withDraft("Claim", PendingChange.Change.REMOVED);
        entities.problems.add(ReleaseProblem.error(DefinitionKinds.ENTITY, "Claim", "entity.release.objects-exist", "Objects exist."));

        assertThatThrownBy(() -> releaseOrganization.execute(ORG, true, null)).isInstanceOf(ReleaseRefusedException.class);
        assertThat(calls).isEmpty();
    }

    @Test
    void nothingPendingIsRefused() {
        assertThatThrownBy(() -> releaseOrganization.execute(ORG, false, null))
                .isInstanceOfSatisfying(ReleaseRefusedException.class,
                        refused -> assertThat(refused.getReason()).isEqualTo(ReleaseRefusedException.Reason.NOTHING_PENDING));
    }

    @Test
    void aFailingParticipantRollsBackWhatEarlierOnesWrote() {
        entities.withDraft("Claim", PendingChange.Change.ADDED);
        apps.withDraft("app", PendingChange.Change.ADDED);
        entities.onRelease = () -> itemRepository.save(new OrgReleaseItem(ORG, 99, 0, "entity", "written", PendingChange.Change.ADDED, null));
        apps.onRelease = () -> {
            throw new IllegalStateException("app release failed");
        };

        assertThatThrownBy(() -> releaseOrganization.execute(ORG, false, null)).hasMessage("app release failed");

        assertThat(itemRepository.count()).isZero();
        assertThat(releaseRepository.count()).isZero();
    }

    @Test
    void aCallerWithoutDesignRightsIsRefusedBeforeAnythingHappens() {
        entities.withDraft("Claim", PendingChange.Change.ADDED);
        doThrow(new OrganizationAccessDeniedException(ORG)).when(policy).requireDesign(ORG);

        assertThatThrownBy(() -> releaseOrganization.execute(ORG, false, null)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> describePendingRelease.execute(ORG)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> listReleases.execute(ORG)).isInstanceOf(OrganizationAccessDeniedException.class);
        assertThat(calls).isEmpty();
    }

    @Test
    void aProvisioningReleaseNeedsNoDesignRightsAndConfirmsItsWarnings() {
        apps.withDraft("app", PendingChange.Change.ADDED);
        apps.references.add(DefinitionReference.optional(DefinitionKinds.APP, "app", DefinitionKinds.ENTITY, "Claim"));
        doThrow(new OrganizationAccessDeniedException(ORG)).when(policy).requireDesign(ORG);

        ReleaseRecord released = releaseOrganization.provision(ORG, "seed");

        assertThat(released.release().getReleasedBy()).isEqualTo("seed");
        assertThat(calls).containsExactly("release entity", "release app");
    }

    @Test
    void twoParticipantsForOneKindAreAConfigurationError() {
        FakeParticipant twin = new FakeParticipant(DefinitionKinds.APP, DefinitionKinds.APP_ORDER, calls);

        List<ReleaseParticipant> both = List.of(apps, twin);

        assertThatThrownBy(() -> ReleaseParticipants.of(both)).isInstanceOf(IllegalStateException.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("bean", bean);
        return factory.getBeanProvider((Class<T>) bean.getClass());
    }
}
