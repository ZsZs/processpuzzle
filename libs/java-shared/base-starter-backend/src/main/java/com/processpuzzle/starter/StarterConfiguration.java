package com.processpuzzle.starter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** The module's configuration. */
@Configuration
@EnableConfigurationProperties(StarterProperties.class)
public class StarterConfiguration {
}
