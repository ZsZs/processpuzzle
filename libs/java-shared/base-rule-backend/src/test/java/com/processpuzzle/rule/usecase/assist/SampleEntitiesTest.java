package com.processpuzzle.rule.usecase.assist;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.rule.port.RuleContext;
import com.processpuzzle.rule.port.RuleContext.ContextAttribute;
import com.processpuzzle.rule.usecase.assist.SampleEntities.Sample;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SampleEntitiesTest {

    private final List<Sample> samples = SampleEntities.of(Orders.context());

    @Test
    void thereAreThreeSamplesTheFilledOneFirst() {
        assertThat(samples).extracting(Sample::name).containsExactly(Sample.FILLED,
            "an object with only its required attributes", "an object with zeros and empty values");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theFilledSampleHasEveryAttributeWithATypeDefault() {
        Map<String, Object> data = samples.get(0).data();

        assertThat(data).containsEntry("total", 1).containsEntry("status", "sample")
            .containsEntry("startDate", "2025-01-15").containsEntry("weight", Map.of("amount", "1", "unit", "kg"));
        assertThat((List<Map<String, Object>>) data.get("lineItems")).singleElement()
            .satisfies(line -> assertThat(line).containsEntry("quantity", Map.of("amount", "1", "unit", "pc")).containsEntry("amount", 1));
        assertThat((Map<String, Object>) data.get("address")).containsEntry("city", "sample");
        assertThat(samples.get(0).unsampled()).isEmpty();
    }

    @Test
    void theRequiredOnlySampleLeavesTheOptionalAttributesOut() {
        assertThat(samples.get(1).data()).containsOnlyKeys("total");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theZeroSampleHasZerosEmptyTextAndEmptyLists() {
        Map<String, Object> data = samples.get(2).data();

        assertThat(data).containsEntry("total", 0).containsEntry("status", "").containsEntry("lineItems", List.of())
            .containsEntry("weight", Map.of("amount", "0", "unit", "kg"));
    }

    @Test
    void moneyAndDimensionsAreSampledInTheirOwnShape() {
        RuleContext context = new RuleContext("Price", List.of(
            new ContextAttribute("amount", "TEXT", false, false, false, "MONEY", List.of("EUR"), List.of()),
            new ContextAttribute("size", "TEXT", false, false, false, "DIMENSIONS", List.of("cm"), List.of())));

        Map<String, Object> data = SampleEntities.of(context).get(0).data();

        assertThat(data).containsEntry("amount", Map.of("amount", "1", "currency", "EUR"))
            .containsEntry("size", Map.of("width", "1", "height", "1", "unit", "cm"));
    }

    @Test
    void aMeasureWithoutAUnitAndATypeWithoutADefaultAreLeftOutAndReportedAsUnsampled() {
        RuleContext context = new RuleContext("Thing", List.of(
            new ContextAttribute("mass", "TEXT", false, false, false, "QUANTITY", List.of(), List.of()),
            new ContextAttribute("span", "PERIOD", false, false, false, null, List.of(), List.of()),
            new ContextAttribute("flag", "BOOLEAN", false, false, false, null, List.of(), List.of())));

        Sample filled = SampleEntities.of(context).get(0);

        assertThat(filled.data()).containsOnlyKeys("flag");
        assertThat(filled.unsampled()).containsExactlyInAnyOrder("mass", "span");
    }
}
