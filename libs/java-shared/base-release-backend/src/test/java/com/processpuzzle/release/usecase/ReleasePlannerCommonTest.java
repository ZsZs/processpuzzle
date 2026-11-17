package com.processpuzzle.release.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.core.definition.DefinitionKinds;
import com.processpuzzle.core.definition.DefinitionReference;
import com.processpuzzle.core.definition.ReleaseProblem;
import com.processpuzzle.core.tenancy.DataSpaceDirectory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** A common may not release away what its members still refer to; it says which members do. */
class ReleasePlannerCommonTest {

    private static final String COMMON = "sailing-common";

    private final List<String> calls = new ArrayList<>();
    private FakeParticipant entities;
    private FakeParticipant rules;
    private DataSpaceDirectory directory;
    private ReleasePlanner planner;

    @BeforeEach
    void setUp() {
        entities = new FakeParticipant(DefinitionKinds.ENTITY, DefinitionKinds.ENTITY_ORDER, calls);
        rules = new FakeParticipant(DefinitionKinds.RULE, DefinitionKinds.RULE_ORDER, calls);
        directory = mock(DataSpaceDirectory.class);
        when(directory.isCommon(COMMON)).thenReturn(true);
        when(directory.membersOf(COMMON)).thenReturn(Set.of("club-a", "club-b"));
        @SuppressWarnings("unchecked")
        ObjectProvider<DataSpaceDirectory> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(directory);
        planner = new ReleasePlanner(ReleaseParticipants.of(List.of(entities, rules)), provider);
    }

    @Test
    void aReleaseThatTakesAwayWhatMembersReferToIsRefused_namingThemAndWhatRefers() {
        entities.removedKeys.add("boat-class");
        entities.referencesByOrg.put("club-a", List.of(DefinitionReference.required(DefinitionKinds.ENTITY, "club-profile", DefinitionKinds.ENTITY, "boat-class")));
        rules.referencesByOrg.put("club-b", List.of(DefinitionReference.required(DefinitionKinds.RULE, "class-rule", DefinitionKinds.ENTITY, "boat-class")));

        PendingRelease pending = planner.plan(COMMON);

        assertThat(pending.hasErrors()).isTrue();
        assertThat(pending.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.errorId()).isEqualTo(ReleasePlanner.MEMBER_REFERENCES);
            assertThat(problem.kind()).isEqualTo(DefinitionKinds.ENTITY);
            assertThat(problem.key()).isEqualTo("boat-class");
            assertThat(problem.message()).contains("club-a (entity 'club-profile')").contains("club-b (rule 'class-rule')");
        });
    }

    @Test
    void aRenamedTypeCountsAsTakenAway_forTheMembersThatReferToItByItsOldName() {
        entities.removedKeys.add("Boat Class");
        rules.referencesByOrg.put("club-a", List.of(DefinitionReference.required(DefinitionKinds.RULE, "class-rule", DefinitionKinds.ENTITY, "Boat Class")));

        assertThat(planner.plan(COMMON).problems()).extracting(ReleaseProblem::key).containsExactly("Boat Class");
    }

    @Test
    void whatNoMemberReferToGoesThrough() {
        entities.removedKeys.add("boat-class");
        entities.referencesByOrg.put("club-a", List.of(DefinitionReference.required(DefinitionKinds.ENTITY, "club-profile", DefinitionKinds.ENTITY, "sailor")));

        assertThat(planner.plan(COMMON).hasErrors()).isFalse();
    }

    @Test
    void nothingTakenAwayMeansNoMemberIsLookedAt() {
        planner.plan(COMMON);

        verify(directory, org.mockito.Mockito.never()).membersOf(COMMON);
    }

    @Test
    void anOrdinaryOrganizationMayReleaseWhatItLikes() {
        entities.removedKeys.add("boat-class");
        when(directory.isCommon("acme")).thenReturn(false);
        entities.referencesByOrg.put("club-a", List.of(DefinitionReference.required(DefinitionKinds.ENTITY, "club-profile", DefinitionKinds.ENTITY, "boat-class")));

        assertThat(planner.plan("acme").hasErrors()).isFalse();
    }

    @Test
    void aHostWithoutCommonsPlansAsBefore() {
        entities.removedKeys.add("boat-class");

        assertThat(new ReleasePlanner(ReleaseParticipants.of(List.of(entities, rules))).plan(COMMON).hasErrors()).isFalse();
    }
}
