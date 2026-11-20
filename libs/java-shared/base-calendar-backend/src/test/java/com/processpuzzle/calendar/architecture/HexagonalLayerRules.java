package com.processpuzzle.calendar.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * The direction of the layers inside the calendar module: {@code domain} knows nothing of {@code usecases}
 * and {@code adapters}; {@code usecases} knows nothing of {@code adapters} or of HTTP; inbound adapters
 * never reach outbound ones; the outbound side of {@code usecases} is interfaces — plus the records and
 * enums that are the data of their contract, and the {@code PermitAll} defaults.
 *
 * <p>Spring Modulith verifies the edges <em>between</em> modules; these rules verify the layers
 * <em>within</em> one. {@code arch-unit-maven-plugin} finds the static {@link ArchRule} fields below and
 * checks them against the main classes at {@code process-test-classes}, so a violation fails the
 * build; {@code HexagonalLayerRulesTest} proves each rule catches what it claims to.
 *
 * <p>Every rule allows an empty match: a part or layer with no classes yet is not a violation.
 *
 * <p>The packages are spelled out as {@code com.processpuzzle.calendar..} rather than a bare
 * {@code ..domain..}, so a dependency on another module's {@code domain} package is not mistaken for
 * ours.
 */
public final class HexagonalLayerRules {

    private static final String DOMAIN = "com.processpuzzle.calendar..domain..";
    private static final String USECASES = "com.processpuzzle.calendar..usecases..";
    private static final String ADAPTERS = "com.processpuzzle.calendar..adapters..";
    private static final String INBOUND = "com.processpuzzle.calendar..adapters.inbound..";
    private static final String OUTBOUND = "com.processpuzzle.calendar..adapters.outbound..";
    private static final String OUTBOUND_PORTS = "com.processpuzzle.calendar..usecases.outbound..";
    private static final String DEFINITION = "com.processpuzzle.calendar.definition..";
    private static final String CONVERSATION = "com.processpuzzle.calendar.entry..";
    private static final String DEFINITION_ADAPTERS = "com.processpuzzle.calendar.definition.adapters..";

    /** HTTP transport and the DTOs generated from the OpenAPI contract: adapter business only. */
    private static final String[] TRANSPORT = {
        "org.springframework.web..",
        "org.springframework.http..",
        "jakarta.servlet..",
        "com.processpuzzle.calendar.api..",
        "com.processpuzzle.calendar.model.."
    };

    public static final ArchRule DOMAIN_IS_INNERMOST = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(USECASES, ADAPTERS)
            .orShould().dependOnClassesThat().resideInAnyPackage(TRANSPORT)
            .allowEmptyShould(true)
            .because("the domain is the innermost layer: it names no use case, adapter or transport type");

    public static final ArchRule USE_CASES_DO_NOT_KNOW_ADAPTERS = noClasses()
            .that().resideInAPackage(USECASES)
            .should().dependOnClassesThat().resideInAnyPackage(ADAPTERS)
            .orShould().dependOnClassesThat().resideInAnyPackage(TRANSPORT)
            .allowEmptyShould(true)
            .because("use cases reach the outside world through outbound ports, never through an adapter or HTTP type");

    public static final ArchRule INBOUND_DOES_NOT_REACH_OUTBOUND = noClasses()
            .that().resideInAPackage(INBOUND)
            .should().dependOnClassesThat().resideInAnyPackage(OUTBOUND)
            .allowEmptyShould(true)
            .because("an inbound adapter calls a use case; it does not pick the outbound adapter itself");

    public static final ArchRule OUTBOUND_PORTS_ARE_INTERFACES = classes()
            .that().resideInAPackage(OUTBOUND_PORTS)
            .and().haveSimpleNameNotStartingWith("PermitAll")
            .and().haveSimpleNameNotEndingWith("package-info")
            .and().areNotRecords()
            .and().areNotEnums()
            .should().beInterfaces()
            .allowEmptyShould(true)
            .because("an outbound port is a contract the host application implements; besides the PermitAll defaults "
                    + "only the plain data types of that contract — records and enums — may sit beside it");

    public static final ArchRule DEFINITIONS_DO_NOT_KNOW_CONVERSATIONS = noClasses()
            .that().resideInAPackage(DEFINITION)
            .should().dependOnClassesThat().resideInAPackage(CONVERSATION)
            .allowEmptyShould(true)
            .because("a definition describes a calendar; it must not know what runs on it");

    public static final ArchRule CONVERSATIONS_DO_NOT_KNOW_DEFINITION_ADAPTERS = noClasses()
            .that().resideInAPackage(CONVERSATION)
            .should().dependOnClassesThat().resideInAPackage(DEFINITION_ADAPTERS)
            .allowEmptyShould(true)
            .because("entries read definitions through the definition use cases, not through its endpoints");

    /** Every rule, in the order they are checked. */
    static List<ArchRule> all() {
        return List.of(
                DOMAIN_IS_INNERMOST,
                USE_CASES_DO_NOT_KNOW_ADAPTERS,
                INBOUND_DOES_NOT_REACH_OUTBOUND,
                OUTBOUND_PORTS_ARE_INTERFACES,
                DEFINITIONS_DO_NOT_KNOW_CONVERSATIONS,
                CONVERSATIONS_DO_NOT_KNOW_DEFINITION_ADAPTERS);
    }
}
