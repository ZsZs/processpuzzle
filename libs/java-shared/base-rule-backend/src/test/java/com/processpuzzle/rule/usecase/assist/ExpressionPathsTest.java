package com.processpuzzle.rule.usecase.assist;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.core.definition.AuthoringIssue;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpressionPathsTest {

    @Test
    void chainsAreTheMemberNamesReadFromEntity() {
        assertThat(ExpressionPaths.chains("entity.total > 0 && entity.address.city === 'x' && entity['status'] == 'a'"))
            .containsExactly(List.of("total"), List.of("address", "city"), List.of("status"));
    }

    @Test
    void optionalChainingAndSpacesAreFollowed() {
        assertThat(ExpressionPaths.chains("entity ?. address ?. city")).containsExactly(List.of("address", "city"));
    }

    @Test
    void aNameThatMerelyEndsInEntityOrIsAMemberOfSomethingElseIsNotAChain() {
        assertThat(ExpressionPaths.chains("myentity.total > 0 || li.entity.total > 0")).isEmpty();
    }

    @Test
    void theRootsAreTheDistinctTopLevelAttributes() {
        assertThat(ExpressionPaths.roots("entity.a.b && entity.c && entity.a")).containsExactly("a", "c");
    }

    @Test
    void anAttributeTheEntityHasIsAccepted() {
        assertThat(ExpressionPaths.unknownAttributes("entity.total > 0 && entity.lineItems.length > 0", Orders.context())).isEmpty();
    }

    @Test
    void aMisspeltAttributeIsAnErrorThatListsWhatTheEntityHas() {
        List<AuthoringIssue> issues = ExpressionPaths.unknownAttributes("entity.totl > 0 && entity.totl < 9", Orders.context());

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(AuthoringIssue.Severity.ERROR);
            assertThat(issue.text()).contains("entity.totl", "Order", "total", "lineItems");
        });
    }

    @Test
    void anEmbeddedObjectsAttributesAreCheckedToo() {
        assertThat(ExpressionPaths.unknownAttributes("entity.address.city != null", Orders.context())).isEmpty();
        assertThat(ExpressionPaths.unknownAttributes("entity.address.town != null", Orders.context())).singleElement()
            .satisfies(issue -> assertThat(issue.text()).contains("entity.address.town", "embedded address", "city"));
    }

    @Test
    void aListEndsTheChainBecauseItsNextMemberIsAnArrayMethod() {
        assertThat(ExpressionPaths.unknownAttributes("entity.lineItems.every(li => li.quantity)", Orders.context())).isEmpty();
    }
}
