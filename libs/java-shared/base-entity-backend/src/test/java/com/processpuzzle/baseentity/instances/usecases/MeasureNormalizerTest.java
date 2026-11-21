package com.processpuzzle.baseentity.instances.usecases;

import com.processpuzzle.baseentity.instances.usecases.inbound.MeasureNormalizer;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityAttributeView.ValueKindView;
import com.processpuzzle.baseentity.instances.usecases.outbound.EntityDefinitionView;
import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView;
import com.processpuzzle.baseentity.instances.usecases.outbound.MeasureView.Kind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeasureNormalizerTest {

    private static final MeasureView MASS = new MeasureView(Kind.QUANTITY, "MASS", null, null, null, null);

    private static EntityAttributeView measure(String code, MeasureView view) {
        return new EntityAttributeView(code, ValueKindView.REFERENCE, false, false, null, false, false, false, false, view);
    }

    private static EntityAttributeView embedded(String code, String linkedType, boolean multiValued) {
        return new EntityAttributeView(code, ValueKindView.REFERENCE, multiValued, true, linkedType, false);
    }

    private static final EntityDefinitionView LINE = new EntityDefinitionView("line", true, List.of(measure("weight", MASS)));
    private static final EntityDefinitionView PARCEL = new EntityDefinitionView("parcel", true, List.of(embedded("lines", "line", true)));
    private static final Function<String, Optional<EntityDefinitionView>> DEFINITIONS = code -> switch (code) {
        case "line" -> Optional.of(LINE);
        case "parcel" -> Optional.of(PARCEL);
        default -> Optional.empty();
    };

    @Test
    void putsATopLevelMeasureIntoItsStoredForm() {
        EntityDefinitionView definition = new EntityDefinitionView("item", false, List.of(measure("weight", MASS)));
        Map<String, Object> payload = Map.of("name", "bolt", "weight", Map.of("amount", "250.0", "unit", "g"));

        Map<String, Object> result = MeasureNormalizer.withDerivedValues(definition, payload, DEFINITIONS);

        assertThat(result).containsEntry("name", "bolt");
        assertThat(result.get("weight")).isEqualTo(Map.of("amount", "250", "unit", "g", "baseAmount", 0.25));
    }

    @Test
    void doesNotChangeTheInput() {
        EntityDefinitionView definition = new EntityDefinitionView("item", false, List.of(measure("weight", MASS)));
        Map<String, Object> weight = Map.of("amount", "1.0", "unit", "kg");
        Map<String, Object> payload = Map.of("weight", weight);

        MeasureNormalizer.withDerivedValues(definition, payload, DEFINITIONS);

        assertThat(payload).containsEntry("weight", weight);
        assertThat(weight).hasSize(2);
    }

    @Test
    void leavesAnAbsentMeasureAbsentAndAValueItCannotReadAsItIs() {
        EntityDefinitionView definition = new EntityDefinitionView("item", false, List.of(measure("weight", MASS), measure("other", MASS)));

        Map<String, Object> result = MeasureNormalizer.withDerivedValues(definition, Map.of("other", "5 kg"), DEFINITIONS);

        assertThat(result).containsExactly(Map.entry("other", "5 kg"));
    }

    @Test
    void reachesTheRowsOfAMultiValuedEmbeddedCollectionAtEveryLevel() {
        EntityDefinitionView definition = new EntityDefinitionView("shipment", false, List.of(embedded("parcels", "parcel", true)));
        Map<String, Object> payload = Map.of("parcels", List.of(
                Map.of("lines", List.of(Map.of("weight", Map.of("amount", "2", "unit", "kg")), Map.of("note", "no weight"))),
                "not a row"));

        Map<String, Object> result = MeasureNormalizer.withDerivedValues(definition, payload, DEFINITIONS);

        List<?> parcels = (List<?>) result.get("parcels");
        Map<?, ?> first = (Map<?, ?>) parcels.get(0);
        List<?> lines = (List<?>) first.get("lines");
        assertThat(((Map<?, ?>) lines.get(0)).get("weight")).isEqualTo(Map.of("amount", "2", "unit", "kg", "baseAmount", 2.0));
        assertThat(lines.get(1)).isEqualTo(Map.of("note", "no weight"));
        assertThat(parcels.get(1)).isEqualTo("not a row");
    }

    @Test
    void reachesASingleEmbeddedComponent() {
        EntityDefinitionView definition = new EntityDefinitionView("item", false, List.of(embedded("main", "line", false)));

        Map<String, Object> result = MeasureNormalizer.withDerivedValues(definition,
                Map.of("main", Map.of("weight", Map.of("amount", "1", "unit", "t"))), DEFINITIONS);

        assertThat(((Map<?, ?>) ((Map<?, ?>) result.get("main")).get("weight")).get("baseAmount")).isEqualTo(1000.0);
    }

    @Test
    void leavesALevelWhoseDefinitionItCannotFindAndAValueOfAnotherShapeAsTheyAre() {
        EntityDefinitionView definition = new EntityDefinitionView("item", false,
                List.of(embedded("ghosts", "ghost", true), embedded("noType", null, false), embedded("odd", "line", false)));
        Map<String, Object> payload = Map.of("ghosts", List.of(Map.of("x", 1)), "noType", Map.of("y", 2), "odd", 7);

        Map<String, Object> result = MeasureNormalizer.withDerivedValues(definition, payload, DEFINITIONS);

        assertThat(result).isEqualTo(payload);
    }

    @Test
    void passesANullPayloadOrDefinitionThrough() {
        assertThat(MeasureNormalizer.withDerivedValues(LINE, null, DEFINITIONS)).isNull();
        Map<String, Object> payload = Map.of("a", 1);
        assertThat(MeasureNormalizer.withDerivedValues(null, payload, DEFINITIONS)).isSameAs(payload);
    }
}
