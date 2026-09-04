package com.processpuzzle;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void corsConfigurationSourceUsesConfiguredValues() {
        CorsConfig config = new CorsConfig(
                new String[]{"http://localhost:4200", "http://localhost:9091"},
                new String[]{"GET", "POST"},
                new String[]{"Authorization", "Content-Type"},
                new String[]{"Location", "X-Object-Name"},
                true);

        CorsConfigurationSource source = config.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/platform/organizations");
        CorsConfiguration cors = source.getCorsConfiguration(request);

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:4200", "http://localhost:9091");
        assertThat(cors.getAllowedMethods()).containsExactly("GET", "POST");
        assertThat(cors.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
        assertThat(cors.getExposedHeaders()).containsExactly("Location", "X-Object-Name");
        assertThat(cors.getAllowCredentials()).isTrue();
    }
}
