package com.processpuzzle.baseentity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.baseentity.definition.domain.ValueKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoredValuesTest {

    @Test
    void aBlankValueIsAbsentNullBlankTextOrAnEmptyArray() {
        assertThat(StoredValues.isBlank(null)).isTrue();
        assertThat(StoredValues.isBlank(" ")).isTrue();
        assertThat(StoredValues.isBlank(List.of())).isTrue();
        assertThat(StoredValues.isBlank(0)).isFalse();
        assertThat(StoredValues.isBlank(false)).isFalse();
    }

    @Test
    void scalarsFitTheirStoredJsonType() {
        assertThat(StoredValues.fits("12", ValueKind.TEXT, false)).isTrue();
        assertThat(StoredValues.fits(12, ValueKind.TEXT, false)).isFalse();
        assertThat(StoredValues.fits(12.5, ValueKind.NUMBER, false)).isTrue();
        assertThat(StoredValues.fits("12", ValueKind.NUMBER, false)).isFalse();
        assertThat(StoredValues.fits(true, ValueKind.BOOLEAN, false)).isTrue();
        assertThat(StoredValues.fits("true", ValueKind.BOOLEAN, false)).isFalse();
        assertThat(StoredValues.fits("GOLD", ValueKind.ENUM, false)).isTrue();
    }

    @Test
    void temporalValuesAreIsoStrings() {
        assertThat(StoredValues.fits("2026-10-14", ValueKind.DATE, false)).isTrue();
        assertThat(StoredValues.fits("14.10.2026", ValueKind.DATE, false)).isFalse();
        assertThat(StoredValues.fits("2026-10-14T09:30:00Z", ValueKind.DATE_TIME, false)).isTrue();
        assertThat(StoredValues.fits("2026-10-14T09:30", ValueKind.DATE_TIME, false)).isTrue();
        assertThat(StoredValues.fits("2026-10-14", ValueKind.DATE_TIME, false)).isFalse();
    }

    @Test
    void aReferenceIsAnIdOrAnEmbeddedComponent() {
        assertThat(StoredValues.fits("5b0c0c44-6f3c-4d1e-9a51-2b7f1e0e3c11", ValueKind.REFERENCE, false)).isTrue();
        assertThat(StoredValues.fits(Map.of("street", "Main"), ValueKind.REFERENCE, false)).isTrue();
        assertThat(StoredValues.fits(7, ValueKind.REFERENCE, false)).isFalse();
    }

    @Test
    void multiplicityMustMatch_elementByElement() {
        assertThat(StoredValues.fits(List.of("a", "b"), ValueKind.TEXT, true)).isTrue();
        assertThat(StoredValues.fits(List.of("a", 2), ValueKind.TEXT, true)).isFalse();
        assertThat(StoredValues.fits("a", ValueKind.TEXT, true)).isFalse();
        assertThat(StoredValues.fits(List.of("a"), ValueKind.TEXT, false)).isFalse();
    }
}
