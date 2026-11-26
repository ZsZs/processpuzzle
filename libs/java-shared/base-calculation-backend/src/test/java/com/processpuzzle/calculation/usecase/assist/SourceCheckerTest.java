package com.processpuzzle.calculation.usecase.assist;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.calculation.adapter.outbound.CamundaFeelExpressionEngine;
import com.processpuzzle.calculation.port.EntityCatalogPort;
import com.processpuzzle.calculation.usecase.CalculationValidator;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.definition.AuthoringIssue.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceCheckerTest {

    private final SourceChecker checker = new SourceChecker(new CalculationValidator(new CamundaFeelExpressionEngine()));

    private List<AuthoringIssue> check(String candidate) {
        return checker.check("acme", Shop.CATALOG, "", candidate);
    }

    private static String texts(List<AuthoringIssue> issues) {
        return String.join("\n", issues.stream().map(AuthoringIssue::text).toList());
    }

    @Test
    void aValidSourceOverKnownEntitiesHasNoIssues() {
        assertThat(check(Shop.ORDER_TOTAL)).isEmpty();
    }

    @Test
    void anEmptySourceIsAnError() {
        assertThat(check("  ")).singleElement().satisfies(issue -> assertThat(issue.severity()).isEqualTo(Severity.ERROR));
        assertThat(check(null)).hasSize(1);
    }

    @Test
    void aSourceThatDoesNotParseIsReportedWithTheParseErrorAndNothingElse() {
        List<AuthoringIssue> issues = check("calculation: [unclosed");

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void theValidatorsOwnFindingsAreErrors() {
        List<AuthoringIssue> issues = check(Shop.ORDER_TOTAL.replace("sum: lineTotal", "sum: lineTotal, avg: lineTotal"));

        assertThat(issues).isNotEmpty().allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(Severity.ERROR));
        assertThat(texts(issues)).contains("aggregate");
    }

    @Test
    void aFeelSyntaxErrorIsReported() {
        assertThat(texts(check(Shop.ORDER_TOTAL.replace("quantity.baseAmount * unitPrice.baseAmount", "quantity.baseAmount *"))))
            .contains("lineTotal");
    }

    @Test
    void aParameterNamingAnEntityTheOrganizationDoesNotHaveIsAnErrorListingTheEntities() {
        List<AuthoringIssue> issues = check(Shop.ORDER_TOTAL.replace("order: { entity: order }", "order: { entity: ordr }"));

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.path()).isEqualTo("parameters.order.entity");
            assertThat(issue.text()).contains("'ordr'", "customer", "order-line");
        });
    }

    @Test
    void aQueryInputNamingAnUnknownEntityIsAnError() {
        String source = Shop.ORDER_TOTAL.replace("lines: { from: order, path: lineItems, entity: order-line }",
            "lines: { entity: ghost, where: \"order==${order}\" }");

        assertThat(check(source)).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("inputs.lines.entity"));
    }

    @Test
    void anEmbeddedInputWhosePathIsNotAnAttributeOfTheEntityIsAnErrorListingTheAttributes() {
        List<AuthoringIssue> issues = check(Shop.ORDER_TOTAL.replace("path: lineItems", "path: lines"));

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.path()).isEqualTo("inputs.lines.path");
            assertThat(issue.text()).contains("'lines'", "reference, total, lineItems");
        });
    }

    @Test
    void anEmbeddedInputWhosePathIsNotAnEmbeddedListIsAnError() {
        assertThat(check(Shop.ORDER_TOTAL.replace("path: lineItems", "path: total")))
            .anySatisfy(issue -> assertThat(issue.text()).contains("not an embedded list"));
    }

    @Test
    void anEmbeddedInputWhoseEntityDiffersFromTheLinkedOneIsOnlyAWarning() {
        List<AuthoringIssue> issues = check(Shop.ORDER_TOTAL.replace("entity: order-line }", "entity: customer }"));

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.text()).contains("order-line", "customer");
        });
    }

    @Test
    void anOutputThatWritesAnAttributeTheEntityLacksIsAnErrorListingTheAttributes() {
        List<AuthoringIssue> issues = check(Shop.ORDER_TOTAL.replace("mapping: { total: total }", "mapping: { totl: total }"));

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.path()).isEqualTo("output.mapping.totl");
            assertThat(issue.text()).contains("'totl'", "reference, total, lineItems");
        });
    }

    @Test
    void anOutputEntityTheOrganizationDoesNotHaveIsAnError() {
        assertThat(check(Shop.ORDER_TOTAL.replace("  entity: order\n  mapping", "  entity: ordr\n  mapping")))
            .anySatisfy(issue -> assertThat(issue.path()).isEqualTo("output.entity"));
    }

    @Test
    void withoutACatalogOnlyTheSourcesOwnStructureIsChecked() {
        assertThat(checker.check("acme", EntityCatalogPort.UNKNOWN, "", Shop.ORDER_TOTAL.replace("totl", "x").replace("total: total", "nonsense: total")))
            .isEmpty();
    }

    @Test
    void theIdOfTheCurrentSourceHasToStay() {
        String current = "calculation: order-total\nname: Order total\ndescription: Sum the order lines.\n";

        List<AuthoringIssue> issues = checker.check("acme", Shop.CATALOG, current, Shop.ORDER_TOTAL.replace("calculation: order-total", "calculation: other"));

        assertThat(issues).anySatisfy(issue -> assertThat(issue.text()).contains("must stay 'order-total'"));
    }

    @Test
    void theUsersDescriptionHasToStayAsTheyWroteIt() {
        String current = "calculation: order-total\nname: Order total\ndescription: Sum the order lines.\n";

        List<AuthoringIssue> issues = checker.check("acme", Shop.CATALOG, current, Shop.ORDER_TOTAL.replace("Sum the order lines.", "Adds up everything."));

        assertThat(issues).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("description"));
        assertThat(checker.check("acme", Shop.CATALOG, current, Shop.ORDER_TOTAL)).isEmpty();
    }

    @Test
    void aCurrentSourceThatDoesNotParseOrHasNoIdAsksNothingOfTheCandidate() {
        assertThat(checker.check("acme", Shop.CATALOG, "calculation: [unclosed", Shop.ORDER_TOTAL)).isEmpty();
        assertThat(checker.check("acme", Shop.CATALOG, "name: Only a name\n", Shop.ORDER_TOTAL)).isEmpty();
    }
}
