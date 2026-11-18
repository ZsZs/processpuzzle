package com.processpuzzle.chat.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.chat.architecture.fixture.bad.adapters.inbound.EndpointUsesOutbound;
import com.processpuzzle.chat.architecture.fixture.bad.domain.DomainUsesHttp;
import com.processpuzzle.chat.architecture.fixture.bad.domain.DomainUsesUseCase;
import com.processpuzzle.chat.architecture.fixture.bad.usecases.inbound.UseCaseUsesAdapter;
import com.processpuzzle.chat.architecture.fixture.bad.usecases.inbound.UseCaseUsesHttp;
import com.processpuzzle.chat.architecture.fixture.bad.usecases.outbound.NotAnInterface;
import com.processpuzzle.chat.conversation.fixture.ConversationUsesDefinitionAdapter;
import com.processpuzzle.chat.definition.fixture.DefinitionUsesConversation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Each layer rule must catch the violation it names and let a conforming layout through. The
 * fixtures live in test sources only; the plugin checks main classes, so they never fail the build.
 */
class HexagonalLayerRulesTest {

    private static final ClassFileImporter IMPORTER = new ClassFileImporter();

    @Test
    void aConformingLayoutPassesEveryRule() {
        JavaClasses good = IMPORTER.importPackages("com.processpuzzle.chat.architecture.fixture.good");

        assertThatCode(() -> HexagonalLayerRules.all().forEach(rule -> rule.check(good))).doesNotThrowAnyException();
    }

    @Test
    void theDomainMayNotUseAUseCase() {
        assertViolation(HexagonalLayerRules.DOMAIN_IS_INNERMOST, DomainUsesUseCase.class);
    }

    @Test
    void theDomainMayNotUseHttpTypes() {
        assertViolation(HexagonalLayerRules.DOMAIN_IS_INNERMOST, DomainUsesHttp.class);
    }

    @Test
    void aUseCaseMayNotUseAnAdapter() {
        assertViolation(HexagonalLayerRules.USE_CASES_DO_NOT_KNOW_ADAPTERS, UseCaseUsesAdapter.class);
    }

    @Test
    void aUseCaseMayNotUseHttpTypes() {
        assertViolation(HexagonalLayerRules.USE_CASES_DO_NOT_KNOW_ADAPTERS, UseCaseUsesHttp.class);
    }

    @Test
    void anInboundAdapterMayNotUseAnOutboundAdapter() {
        assertViolation(HexagonalLayerRules.INBOUND_DOES_NOT_REACH_OUTBOUND, EndpointUsesOutbound.class);
    }

    @Test
    void anOutboundPortMayBesideItHoldRecordsAndEnumsButNotOtherClasses() {
        JavaClasses dataTypes = IMPORTER.importClasses(
                com.processpuzzle.chat.architecture.fixture.good.usecases.outbound.GoodPortResult.class,
                com.processpuzzle.chat.architecture.fixture.good.usecases.outbound.GoodPortMode.class);

        assertThatCode(() -> HexagonalLayerRules.OUTBOUND_PORTS_ARE_INTERFACES.check(dataTypes)).doesNotThrowAnyException();
    }

    @Test
    void anOutboundPortMustBeAnInterface() {
        assertViolation(HexagonalLayerRules.OUTBOUND_PORTS_ARE_INTERFACES, NotAnInterface.class);
    }

    @Test
    void aDefinitionMayNotUseConversations() {
        assertViolation(HexagonalLayerRules.DEFINITIONS_DO_NOT_KNOW_CONVERSATIONS, DefinitionUsesConversation.class);
    }

    @Test
    void aConversationMayNotUseDefinitionAdapters() {
        assertViolation(
                HexagonalLayerRules.CONVERSATIONS_DO_NOT_KNOW_DEFINITION_ADAPTERS,
                ConversationUsesDefinitionAdapter.class);
    }

    private static void assertViolation(ArchRule rule, Class<?> violator) {
        JavaClasses classes = IMPORTER.importClasses(violator);

        assertThatThrownBy(() -> rule.check(classes)).isInstanceOf(AssertionError.class);
    }
}
