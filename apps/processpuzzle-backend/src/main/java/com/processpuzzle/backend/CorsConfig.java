package com.processpuzzle.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final String[] allowedMethods;
    private final String[] allowedHeaders;
    private final boolean allowCredentials;

    public CorsConfig(
        @Value("${app.cors.allowed-origins:http://localhost:4200}") final String[] allowedOrigins,
        @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}") final String[] allowedMethods,
        @Value("${app.cors.allowed-headers:*}") final String[] allowedHeaders,
        @Value("${app.cors.allow-credentials:true}") final boolean allowCredentials
    ) {
        this.allowedOrigins = allowedOrigins;
        this.allowedMethods = allowedMethods;
        this.allowedHeaders = allowedHeaders;
        this.allowCredentials = allowCredentials;
    }

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry
            .addMapping("/**")
            .allowedOrigins(this.allowedOrigins)
            .allowedMethods(this.allowedMethods)
            .allowedHeaders(this.allowedHeaders)
            .allowCredentials(this.allowCredentials);
    }
}
