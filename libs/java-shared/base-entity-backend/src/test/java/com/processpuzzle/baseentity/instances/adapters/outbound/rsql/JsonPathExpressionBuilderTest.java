package com.processpuzzle.baseentity.instances.adapters.outbound.rsql;

import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import cz.jirutka.rsql.parser.ast.RSQLOperators;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPathExpressionBuilderTest {

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
}
