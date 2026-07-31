package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RuleExtendsValidatorTest {

    private RuleDefinitionRepository repository;
    private RuleExtendsValidator validator;

    @BeforeEach
    void setUp() {
        repository = mock(RuleDefinitionRepository.class);
        validator = new RuleExtendsValidator(repository);
    }

    @Test
    void noExtendsLinkNeedsNoLookupAtAll() {
        validator.validate("demo", "child", null);

        verifyNoInteractions(repository);
    }

    @Test
    void acceptsAChainThatTerminates() {
        stub("a", "b");
        stub("b", null);

        assertThatCode(() -> validator.validate("demo", "child", "a")).doesNotThrowAnyException();
    }

    @Test
    void rejectsARuleExtendingItself() {
        assertThatThrownBy(() -> validator.validate("demo", "child", "child"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rule cannot extend itself: child");

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAParentThisOrganizationDoesNotHave() {
        // The parent may well exist in another organization; the chain resolves within one tenant.
        when(repository.findByOrgKeyAndId("demo", "foreign")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate("demo", "child", "foreign"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rule 'child' extends unknown rule 'foreign'");
    }

    @Test
    void rejectsALinkThatWouldCloseACycle() {
        stub("a", "b");
        stub("b", "child");

        assertThatThrownBy(() -> validator.validate("demo", "child", "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Setting 'child' to extend 'a' would create a cycle");
    }

    @Test
    void abortsAChainDeeperThanTheGuard() {
        // Every lookup answers with a fresh parent id, so the walk would never terminate on its
        // own — the depth guard is what stops it.
        when(repository.findByOrgKeyAndId(eq("demo"), anyString()))
                .thenAnswer(call -> Optional.of(rule(call.getArgument(1), call.getArgument(1) + "-up")));

        assertThatThrownBy(() -> validator.validate("demo", "child", "a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Extends chain too deep (or already cyclic) starting at 'a");
    }

    private void stub(String id, String extendsRuleId) {
        when(repository.findByOrgKeyAndId("demo", id)).thenReturn(Optional.of(rule(id, extendsRuleId)));
    }

    private static RuleDefinition rule(String id, String extendsRuleId) {
        return new RuleDefinition("demo", id, id, null, "Order", "true", Severity.ERROR,
                null, null, extendsRuleId, false, true, List.of());
    }
}
