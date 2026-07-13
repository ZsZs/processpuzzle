package com.processpuzzle.rule.adapter.inbound.rsql;

import com.processpuzzle.core.rsql.RsqlSpecificationBuilder;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@EntityScan("com.processpuzzle.rule.domain")
@EnableJpaRepositories("com.processpuzzle.rule.domain")
class RsqlSpecificationBuilderTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    @Autowired
    private RuleDefinitionRepository repository;

    private final RsqlSpecificationBuilder<RuleDefinition> builder = new RsqlSpecificationBuilder<>();

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAll(List.of(
                rule("r-1", "positive-quantities", "Order", Severity.ERROR, true),
                rule("r-2", "customer-name-required", "Order", Severity.WARNING, true),
                rule("r-3", "vat-computation", "Invoice", Severity.INFO, false),
                rule("r-4", "shipping-country", "Order", Severity.ERROR, false)
        ));
    }

    @Test
    void blankExpression_returnsNullSpec() {
        assertThat(builder.build(null)).isNull();
        assertThat(builder.build("")).isNull();
        assertThat(builder.build("   ")).isNull();
    }

    @Test
    void equals_onString() {
        assertThat(findIds("context==Order")).containsExactlyInAnyOrder("r-1", "r-2", "r-4");
    }

    @Test
    void notEquals_onString() {
        assertThat(findIds("context!=Order")).containsExactly("r-3");
    }

    @Test
    void equals_onEnum() {
        assertThat(findIds("severity==ERROR")).containsExactlyInAnyOrder("r-1", "r-4");
    }

    @Test
    void equals_onBoolean() {
        assertThat(findIds("enabled==true")).containsExactlyInAnyOrder("r-1", "r-2");
    }

    @Test
    void inOperator() {
        assertThat(findIds("severity=in=(ERROR,INFO)")).containsExactlyInAnyOrder("r-1", "r-3", "r-4");
    }

    @Test
    void outOperator() {
        assertThat(findIds("severity=out=(ERROR,INFO)")).containsExactly("r-2");
    }

    @Test
    void likeOperator_addsWildcards() {
        assertThat(findIds("name=like=quantities")).containsExactly("r-1");
    }

    @Test
    void likeOperator_respectsExplicitWildcards() {
        assertThat(findIds("name=like=customer%")).containsExactly("r-2");
    }

    @Test
    void nullLiteral_translatesToIsNull() {
        assertThat(findIds("extendsRuleId==null")).containsExactlyInAnyOrder("r-1", "r-2", "r-3", "r-4");
    }

    @Test
    void andComposition() {
        assertThat(findIds("context==Order;enabled==true")).containsExactlyInAnyOrder("r-1", "r-2");
    }

    @Test
    void orComposition() {
        assertThat(findIds("severity==WARNING,severity==INFO")).containsExactlyInAnyOrder("r-2", "r-3");
    }

    @Test
    void syntaxError_isRejectedWithIllegalArgument() {
        assertThatThrownBy(() -> builder.build("context=="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid RSQL");
    }

    @Test
    void invalidEnumValue_isRejectedAtQueryTime() {
        // Coercion errors surface when the Specification is evaluated. Spring's JPA
        // translation wraps our IllegalArgumentException in InvalidDataAccessApiUsageException;
        // ApiExceptionHandler maps that back to HTTP 400 by looking at the root cause.
        Specification<RuleDefinition> spec = builder.build("severity==NOT_A_LEVEL");
        assertThatThrownBy(() -> repository.findAll(spec))
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NOT_A_LEVEL");
    }

    private List<String> findIds(String rsql) {
        return repository.findAll(builder.build(rsql)).stream()
                .map(RuleDefinition::getId)
                .toList();
    }

    private static RuleDefinition rule(String id, String name, String context,
                                        Severity severity, boolean enabled) {
        return new RuleDefinition(
                id, name, "desc-" + id, context, "true",
                severity, "msg", "transloco." + id, null, false, enabled, List.of());
    }
}
