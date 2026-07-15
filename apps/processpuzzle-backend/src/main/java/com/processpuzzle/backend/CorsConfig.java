package com.processpuzzle.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

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
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(this.allowedOrigins);
        configuration.setAllowedMethods(this.allowedMethods);
        configuration.setAllowedHeaders(this.allowedHeaders);
        configuration.setExposedHeaders(this.exposedHeaders);
        configuration.setAllowCredentials(this.allowCredentials);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return new CorsFilter(source);
    }
}
