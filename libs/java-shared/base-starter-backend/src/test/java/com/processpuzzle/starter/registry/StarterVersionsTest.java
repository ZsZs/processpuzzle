package com.processpuzzle.starter.registry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StarterVersionsTest {

    @ParameterizedTest
    @CsvSource({
            "1.9.0, 1.10.0, -1",
            "2.0.0, 1.99.99, 1",
            "1.0.1, 1.0.0, 1",
            "1.0.0, 1.0.0, 0",
            "1.0, 1.0.0, 0",
            "1.0.0, 1.0, 0",
            "1.0.0-rc.1, 1.0.0, -1",
            "1.0.0, 1.0.0-rc.1, 1",
            "1.0.0-alpha, 1.0.0-beta, -1",
            "1.0.0-beta, 1.0.0-alpha, 1",
            "1.0.0-rc.1, 1.0.0-rc.1, 0",
            "1.invalid.0, 1.0.0, 0",
            "1.0.0, 1.99999999999999999999.0, 0"
    })
    void ordersVersionsNumericallyWithPrereleasesBeforeReleases(String left, String right, int expectedSign) {
        assertThat(Integer.signum(StarterVersions.ASCENDING.compare(left, right))).isEqualTo(expectedSign);
    }
}
