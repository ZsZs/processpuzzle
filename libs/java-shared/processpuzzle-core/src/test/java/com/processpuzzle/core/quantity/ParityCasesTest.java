package com.processpuzzle.core.quantity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs {@code parity-cases.json} — the cases {@code @processpuzzle/util}'s fundamental-types spec runs too — so a
 * conversion the browser shows and the one the backend stores cannot differ by a digit.
 */
class ParityCasesTest {

    private static final UnitCatalog CATALOG = UnitCatalog.standard();
    private static final JsonNode CASES = read();

    @TestFactory
    Stream<DynamicTest> conversions() {
        return cases("conversions", c -> "%s %s to %s".formatted(c.get("amount").asText(), c.get("from").asText(), c.get("to").asText()), c -> {
            Quantity converted = Quantity.of(text(c, "amount"), text(c, "from")).convertTo(text(c, "to"), CATALOG);
            assertThat(converted.amountText()).isEqualTo(text(c, "expected"));
            assertThat(converted.unit()).isEqualTo(text(c, "to"));
        });
    }

    @TestFactory
    Stream<DynamicTest> baseAmounts() {
        return cases("baseAmounts", c -> text(c, "amount") + " " + text(c, "unit"),
            c -> assertThat(Decimals.text(Quantity.of(text(c, "amount"), text(c, "unit")).baseAmount(CATALOG))).isEqualTo(text(c, "expected")));
    }

    @TestFactory
    Stream<DynamicTest> dimensions() {
        return cases("dimensions", c -> text(c, "width") + " × " + text(c, "height") + " " + text(c, "unit"), c -> {
            BigDecimal depth = c.has("depth") ? new BigDecimal(text(c, "depth")) : null;
            Dimensions dimensions = new Dimensions(new BigDecimal(text(c, "width")), new BigDecimal(text(c, "height")), depth, text(c, "unit"));
            assertThat(dimensions.area(CATALOG)).isEqualTo(Quantity.of(text(c, "area"), "m2"));
            if (c.has("volume")) {
                assertThat(dimensions.volume(CATALOG)).isEqualTo(Quantity.of(text(c, "volume"), "m3"));
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> moneyRounding() {
        return cases("moneyRounding", c -> text(c, "amount") + " " + text(c, "currency"),
            c -> assertThat(Money.of(text(c, "amount"), text(c, "currency")).rounded(CATALOG).amountText()).isEqualTo(text(c, "expected")));
    }

    @TestFactory
    Stream<DynamicTest> toIsoDuration() {
        return cases("toIsoDuration", c -> text(c, "amount") + " " + text(c, "unit"),
            c -> assertThat(IsoDurations.toIsoDuration(Quantity.of(text(c, "amount"), text(c, "unit")), CATALOG)).isEqualTo(text(c, "expected")));
    }

    @TestFactory
    Stream<DynamicTest> fromIsoDuration() {
        return cases("fromIsoDuration", c -> text(c, "duration"),
            c -> assertThat(IsoDurations.toQuantity(text(c, "duration"), text(c, "unit"), CATALOG)).isEqualTo(Quantity.of(text(c, "expected"), text(c, "unit"))));
    }

    @TestFactory
    Stream<DynamicTest> invalidIsoDurations() {
        return cases("invalidIsoDurations", c -> text(c, "duration"), c -> assertThatThrownBy(() -> IsoDurations.toQuantity(text(c, "duration"), "s", CATALOG))
            .isInstanceOfSatisfying(FundamentalTypeException.class, e -> assertThat(e.reason().name()).isEqualTo(text(c, "reason"))));
    }

    private static Stream<DynamicTest> cases(String group, Function<JsonNode, String> name, Consumer<JsonNode> check) {
        return StreamSupport.stream(CASES.get(group).spliterator(), false).map(c -> DynamicTest.dynamicTest(name.apply(c), () -> check.accept(c)));
    }

    private static String text(JsonNode node, String field) {
        return node.get(field).asText();
    }

    private static JsonNode read() {
        try (InputStream source = ParityCasesTest.class.getResourceAsStream("/fundamental-types/parity-cases.json")) {
            return new ObjectMapper().readTree(source);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
