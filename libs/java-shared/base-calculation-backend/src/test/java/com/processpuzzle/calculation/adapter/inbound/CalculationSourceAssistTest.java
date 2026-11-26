package com.processpuzzle.calculation.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.processpuzzle.calculation.adapter.outbound.CamundaFeelExpressionEngine;
import com.processpuzzle.calculation.domain.definition.CalculationParser;
import com.processpuzzle.calculation.port.EntityCatalogPort;
import com.processpuzzle.calculation.usecase.CalculationValidator;
import com.processpuzzle.calculation.usecase.assist.Shop;
import com.processpuzzle.calculation.usecase.assist.SourceChecker;
import com.processpuzzle.core.assist.AssistRequest;
import com.processpuzzle.core.definition.AuthoringIssue;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class CalculationSourceAssistTest {

    private final OrganizationGuard guard = mock(OrganizationGuard.class);
    private final CalculationValidator validator = new CalculationValidator(new CamundaFeelExpressionEngine());
    private final CalculationSourceAssist assist = new CalculationSourceAssist(provider(OrganizationGuard.class, guard),
        provider(EntityCatalogPort.class, Shop.CATALOG), new SourceChecker(validator));

    private static final AssistRequest REQUEST = new AssistRequest("Sum the order lines.", Map.of(), "");

    @Test
    void theTaskIsNamedCalculationSource() {
        assertThat(assist.task()).isEqualTo("calculation.source");
    }

    @Test
    void accessIsWhoeverMayDesignInTheOrganization() {
        assist.checkAccess("acme");
        verify(guard).requireDesign("acme");

        doThrow(new SecurityException("no")).when(guard).requireDesign("other");
        assertThatThrownBy(() -> assist.checkAccess("other")).isInstanceOf(SecurityException.class);
    }

    @Test
    void theContextListsTheOrganizationsEntities() {
        assertThat(assist.context("acme", REQUEST)).contains("- order (Order)", "lineItems: list of embedded order-line");
    }

    @Test
    void withoutACatalogTheContextSaysTheEntitiesAreNotKnown() {
        CalculationSourceAssist bare = new CalculationSourceAssist(provider(OrganizationGuard.class, guard),
            new StaticListableBeanFactory().getBeanProvider(EntityCatalogPort.class), new SourceChecker(validator));

        assertThat(bare.context("acme", REQUEST)).contains("not known");
    }

    @Test
    void aParameterIsRefused() {
        assertThatThrownBy(() -> assist.context("acme", new AssistRequest("x", Map.of("context", "Order"), "")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("context");
    }

    @Test
    void aCandidateIsCheckedAgainstTheEntitiesAndTheCurrentSource() {
        assertThat(assist.validate("acme", REQUEST, Shop.ORDER_TOTAL)).isEmpty();

        List<AuthoringIssue> issues = assist.validate("acme", REQUEST, Shop.ORDER_TOTAL.replace("total: total", "totl: total"));
        assertThat(issues).singleElement().satisfies(issue -> assertThat(issue.path()).isEqualTo("output.mapping.totl"));
    }

    @Test
    void theGuideIsLoadedFromTheClasspath() {
        assertThat(assist.guide()).contains("a small graph of decisions", "baseAmount", "ONE of `expr`");
        assertThat(assist.guide()).isSameAs(assist.guide());
    }

    /** The document the model learns the shape from must itself be a valid calculation, or the guide teaches a fault. */
    @Test
    void theGuidesExampleDocumentIsAValidCalculation() {
        Matcher block = Pattern.compile("(?s)```yaml\\n(calculation: order-total.*?)```").matcher(assist.guide());

        assertThat(block.find()).isTrue();
        CalculationValidator.Validation validation = validator.validate(block.group(1));
        assertThat(validation.errors()).isEmpty();
    }

    @Test
    void everyYamlBlockOfTheGuideIsWellFormedYaml() {
        Matcher blocks = Pattern.compile("(?s)```yaml\\n(.*?)```").matcher(assist.guide());
        int count = 0;
        while (blocks.find()) {
            count++;
            String yaml = blocks.group(1);
            // A fragment has no id, so it does not parse as a calculation; it must still be YAML, which the parser reports
            // with a YAML error rather than a missing-key one.
            String error = CalculationParser.parse(yaml).error();
            assertThat(error == null || !error.toLowerCase().contains("yaml")).as(yaml).isTrue();
        }
        assertThat(count).isGreaterThanOrEqualTo(3);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean(type.getSimpleName(), bean);
        return factory.getBeanProvider(type);
    }
}
