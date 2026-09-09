package com.processpuzzle.baseentity.instances.adapters.outbound.rsql;

import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.RSQLOperators;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JsonPathExpressionBuilderTest {

    @Mock
    private CriteriaBuilder cb;

    @Test
    void buildPath_singleSegment_equality() {
        ResolvedAttributePath path = new ResolvedAttributePath(
                List.of(new PathSegment("status", false)),
                ValueKindView.TEXT
        );

        String expr = JsonPathExpressionBuilder.buildPath(path, RSQLOperators.EQUAL, List.of("ACTIVE"));

        assertThat(expr).isEqualTo("$.status ? (@ == $v0)");
    }

    @Test
    void buildPath_nestedArraySegment_comparison() {
        ResolvedAttributePath path = new ResolvedAttributePath(
                List.of(
                        new PathSegment("lines", true),
                        new PathSegment("quantity", false)
                ),
                ValueKindView.NUMBER
        );

        String expr = JsonPathExpressionBuilder.buildPath(path, RSQLOperators.GREATER_THAN, List.of("10"));

        assertThat(expr).isEqualTo("$.lines[*].quantity ? (@ > $v0)");
    }

    @Test
    void buildPath_allOperators() {
        ResolvedAttributePath path = new ResolvedAttributePath(
                List.of(new PathSegment("amount", false)),
                ValueKindView.NUMBER
        );

        assertThat(JsonPathExpressionBuilder.buildPath(path, RSQLOperators.NOT_EQUAL, List.of("10")))
                .isEqualTo("$.amount ? (@ != $v0)");

        assertThat(JsonPathExpressionBuilder.buildPath(path, RSQLOperators.GREATER_THAN_OR_EQUAL, List.of("10")))
                .isEqualTo("$.amount ? (@ >= $v0)");

        assertThat(JsonPathExpressionBuilder.buildPath(path, RSQLOperators.LESS_THAN, List.of("10")))
                .isEqualTo("$.amount ? (@ < $v0)");

        assertThat(JsonPathExpressionBuilder.buildPath(path, RSQLOperators.LESS_THAN_OR_EQUAL, List.of("10")))
                .isEqualTo("$.amount ? (@ <= $v0)");

        assertThat(JsonPathExpressionBuilder.buildPath(path, RSQLOperators.IN, List.of("10", "20")))
                .isEqualTo("$.amount ? (@ == $v0 || @ == $v1)");

        assertThat(JsonPathExpressionBuilder.buildPath(path, RSQLOperators.NOT_IN, List.of("10", "20")))
                .isEqualTo("$.amount ? (!(@ == $v0 || @ == $v1))");
    }

    @Test
    void buildPath_temporalFields_usesDatetimeMethod() {
        ResolvedAttributePath datePath = new ResolvedAttributePath(
                List.of(new PathSegment("birthDate", false)),
                ValueKindView.DATE
        );

        String expr = JsonPathExpressionBuilder.buildPath(datePath, RSQLOperators.GREATER_THAN, List.of("2020-01-01"));
        assertThat(expr).isEqualTo("$.birthDate ? (@.datetime() > $v0.datetime())");

        String inExpr = JsonPathExpressionBuilder.buildPath(datePath, RSQLOperators.IN, List.of("2020-01-01", "2021-01-01"));
        assertThat(inExpr).isEqualTo("$.birthDate ? (@.datetime() == $v0.datetime() || @.datetime() == $v1.datetime())");

        ResolvedAttributePath dateTimePath = new ResolvedAttributePath(
                List.of(new PathSegment("createdAt", false)),
                ValueKindView.DATE_TIME
        );
        String dtExpr = JsonPathExpressionBuilder.buildPath(dateTimePath, RSQLOperators.EQUAL, List.of("2020-01-01T12:00:00Z"));
        assertThat(dtExpr).isEqualTo("$.createdAt ? (@.datetime() == $v0.datetime())");
    }

    @Test
    void buildPath_unsafeAttributeCode_throwsIllegalStateException() {
        ResolvedAttributePath path = new ResolvedAttributePath(
                List.of(new PathSegment("bad;segment", false)),
                ValueKindView.TEXT
        );

        assertThatThrownBy(() -> JsonPathExpressionBuilder.buildPath(path, RSQLOperators.EQUAL, List.of("val")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed the jsonpath-safety check");
    }

    @Test
    void buildPath_unsupportedOperator_throwsIllegalArgumentException() {
        ResolvedAttributePath path = new ResolvedAttributePath(
                List.of(new PathSegment("status", false)),
                ValueKindView.TEXT
        );

        ComparisonOperator customOp = new ComparisonOperator("=custom=");

        assertThatThrownBy(() -> JsonPathExpressionBuilder.buildPath(path, customOp, List.of("val")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported RSQL operator");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildVars_createsJsonbBuildObjectExpression_forAllTypes() {
        Expression<Object> mockResult = mock(Expression.class);
        when(cb.function(eq("jsonb_build_object"), eq(Object.class), any(Expression[].class)))
                .thenReturn(mockResult);

        Expression<Object> resultNum = JsonPathExpressionBuilder.buildVars(cb, ValueKindView.NUMBER, List.of("123.45"));
        assertThat(resultNum).isSameAs(mockResult);
        verify(cb).literal(eq(new BigDecimal("123.45")));

        Expression<Object> resultBool = JsonPathExpressionBuilder.buildVars(cb, ValueKindView.BOOLEAN, List.of("true"));
        assertThat(resultBool).isSameAs(mockResult);
        verify(cb).literal(eq(true));

        List<ValueKindView> stringTypes = List.of(ValueKindView.TEXT, ValueKindView.ENUM, ValueKindView.DATE, ValueKindView.DATE_TIME, ValueKindView.REFERENCE);
        for (ValueKindView vk : stringTypes) {
            Expression<Object> res = JsonPathExpressionBuilder.buildVars(cb, vk, List.of("val"));
            assertThat(res).isSameAs(mockResult);
        }
        org.mockito.Mockito.verify(cb, org.mockito.Mockito.times(stringTypes.size())).literal("val");
    }
}
