package com.processpuzzle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The one place that decides which origins may call this backend.
 *
 * <p>Exposed as a {@link CorsConfigurationSource} rather than a {@code CorsFilter} bean, because the
 * policy has to be applied <em>inside</em> the Spring Security filter chain — see
 * {@link com.processpuzzle.security.SecurityConfig}, which picks this bean up by its conventional
 * name. A standalone {@code CorsFilter} bean is auto-registered at
 * {@link org.springframework.core.Ordered#LOWEST_PRECEDENCE}, so it runs <em>after</em> security: a
 * request that security rejects with 401/403 never reaches it and carries no CORS headers, which the
 * browser reports as "No 'Access-Control-Allow-Origin' header is present" even though the origin is
 * allow-listed. Preflight hides the problem, as {@code OPTIONS} is permitted and does reach the
 * filter.
 */
@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;
    private final List<String> allowedMethods;
    private final List<String> allowedHeaders;
    private final List<String> exposedHeaders;
    private final boolean allowCredentials;

    public CorsConfig(
        @Value("${app.cors.allowed-origins:http://localhost:4200}") final String[] allowedOrigins,
        @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}") final String[] allowedMethods,
        @Value("${app.cors.allowed-headers:*}") final String[] allowedHeaders,
        @Value("${app.cors.exposed-headers:Location,X-Object-Name,X-Object-Bucket}") final String[] exposedHeaders,
        @Value("${app.cors.allow-credentials:true}") final boolean allowCredentials
    ) {
        this.allowedOrigins = List.of(allowedOrigins);
        this.allowedMethods = List.of(allowedMethods);
        this.allowedHeaders = List.of(allowedHeaders);
        this.exposedHeaders = List.of(exposedHeaders);
        this.allowCredentials = allowCredentials;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(this.allowedOrigins);
        configuration.setAllowedMethods(this.allowedMethods);
        configuration.setAllowedHeaders(this.allowedHeaders);
        configuration.setExposedHeaders(this.exposedHeaders);
        configuration.setAllowCredentials(this.allowCredentials);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
