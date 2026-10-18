package com.processpuzzle.calculation.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.calculation.domain.definition.CalculationModel;
import com.processpuzzle.calculation.domain.definition.CalculationParser;
import com.processpuzzle.calculation.port.AttributeType;
import com.processpuzzle.calculation.port.EntityRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypedValuesTest {

    @Test
    void typesStoredTextByTheAttributesType() {
        InMemoryEntities types = new InMemoryEntities();
        types.type("e", "due", AttributeType.DATE);
        types.type("e", "at", AttributeType.DATE_TIME);
        types.type("e", "local", AttributeType.DATE_TIME);
        types.type("e", "amount", AttributeType.NUMBER);
        types.type("e", "flag", AttributeType.BOOLEAN);
        types.type("e", "broken", AttributeType.DATE);
        TypedValues.Typer typer = new TypedValues(types).typer("org");

        Map<String, Object> typed = typer.payload("e", Map.of("due", "2026-10-18T00:00:00Z", "at", "2026-10-18T10:00:00+02:00",
                "local", "2026-10-18T10:00:00", "amount", "12.50", "flag", "true", "broken", "soon", "count", 3, "name", "x"));

        assertThat(typed).containsEntry("due", LocalDate.of(2026, 10, 18))
                .containsEntry("at", ZonedDateTime.parse("2026-10-18T10:00:00+02:00"))
                .containsEntry("local", LocalDateTime.of(2026, 10, 18, 10, 0))
                .containsEntry("amount", new BigDecimal("12.50")).containsEntry("flag", true)
                .containsEntry("broken", "soon").containsEntry("count", BigDecimal.valueOf(3)).containsEntry("name", "x");
        assertThat(typer.value(null, "due", "2026-10-18")).isEqualTo("2026-10-18");
    }

    @Test
    void typesPlainParameters() {
        assertThat(TypedValues.ofParameterType("number", "2.5")).isEqualTo(new BigDecimal("2.5"));
        assertThat(TypedValues.ofParameterType("date", "2026-01-02")).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(TypedValues.ofParameterType("boolean", "FALSE")).isEqualTo(false);
        assertThat(TypedValues.ofParameterType("date-time", "2026-01-02T03:04:05")).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        assertThat(TypedValues.ofParameterType("string", "x")).isEqualTo("x");
        assertThat(TypedValues.ofParameterType("number", 4)).isEqualTo(BigDecimal.valueOf(4));
    }

    @Test
    void substitutesPlaceholdersQuotingWhereRsqlNeedsIt() {
        CalculationModel model = CalculationParser.parse("""
                calculation: c
                parameters: { series: { entity: series }, label: { type: string } }
                decisions: { x: { expr: "1" } }
                """).model();
        Map<String, EntityRecord> objects = Map.of("series", new EntityRecord("s-1", 0, Map.of("venue", "Lake \"Garda\"")));

        assertThat(InputResolver.substitute("series==${series};venue==${series.venue};label==${label}", model,
                Map.of("label", "a b"), objects)).isEqualTo("series==s-1;venue==\"Lake \\\"Garda\\\"\";label==\"a b\"");
        assertThat(InputResolver.substitute(" ", model, Map.of(), objects)).isNull();
    }
}
