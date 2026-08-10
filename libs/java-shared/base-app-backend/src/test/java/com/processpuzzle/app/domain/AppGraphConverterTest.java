package com.processpuzzle.app.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The converter is the only thing standing between a persisted JSON blob and an unreadable app
 * definition, so the property that matters most is the tolerant read: dropping a field from one of
 * the graph records must not make every row written before that release fail to load.
 */
class AppGraphConverterTest {

    private final AppGraphConverter converter = new AppGraphConverter();

    @Test
    void aGraphRoundTripsThroughItsColumn() {
        AppGraph graph = new AppGraph(
                new Theme("rose-red", "dark", Map.of("--pp-surface-sidenav", "#0d1b2a"), "/logo.png", null),
                new Layout("sidenav-left", "side", true, false, "1280px"),
                List.of(new Region("sidenav",
                        List.of(new NavNode("nav-claims", "Claims", "claims.nav", "list_alt",
                                "page-claims", List.of("CLAIMS_ADJUSTER"), List.of())),
                        List.of())),
                List.of(new AppPage("page-claims", "Claims", null,
                        List.of(new Widget("widget-grid", "entity-grid", Map.of("entityName", "Claim"),
                                WidgetPlacement.REFERENCED)))));

        String column = converter.convertToDatabaseColumn(graph);

        assertThat(converter.convertToEntityAttribute(column)).isEqualTo(graph);
    }

    @Test
    void anEmptyGraphRoundTripsToAnEmptyGraph() {
        String column = converter.convertToDatabaseColumn(AppGraph.empty());

        assertThat(converter.convertToEntityAttribute(column)).isEqualTo(AppGraph.empty());
    }

    /** {@code NON_NULL} inclusion: an unset theme or layout must not become a {@code null} literal. */
    @Test
    void unsetPartsAreOmittedFromTheColumnRatherThanWrittenAsNull() {
        String column = converter.convertToDatabaseColumn(AppGraph.empty());

        assertThat(column).doesNotContain("null").contains("regions", "pages");
    }

    @Test
    void noGraphMeansNoColumnValue() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void anAbsentOrEmptyColumnReadsBackAsNoGraph() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("")).isNull();
        assertThat(converter.convertToEntityAttribute("   ")).isNull();
    }

    /** Removing a field from one of the graph records must not orphan every row already written. */
    @Test
    void aPropertyThisReleaseNoLongerKnows_isIgnoredRatherThanFailingTheRead() {
        String fromAnEarlierRelease = """
                {"regions":[{"type":"sidenav","navItems":[],"widgets":[],"legacyFlag":true}],\
                "pages":[],"retiredField":"whatever"}""";

        AppGraph graph = converter.convertToEntityAttribute(fromAnEarlierRelease);

        assertThat(graph).isNotNull();
        assertThat(graph.regions()).singleElement()
                .satisfies(region -> assertThat(region.type()).isEqualTo("sidenav"));
    }

    @Test
    void anUnreadableColumn_failsLoudlyRatherThanSilentlyYieldingNoGraph() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{not json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot deserialize app graph");
    }

    /**
     * Widget {@code props} is opaque to the backend, so nothing stops a caller putting something
     * unserializable in it. Better to fail the write than to persist a truncated graph.
     */
    @Test
    void aGraphThatCannotBeSerialized_failsTheWrite() {
        AppGraph unserializable = new AppGraph(null, null, List.of(),
                List.of(new AppPage("page-1", "One", null,
                        List.of(new Widget("widget-1", "custom", Map.of("opaque", new Object()), WidgetPlacement.STANDALONE)))));

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(unserializable))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot serialize app graph");
    }
}
