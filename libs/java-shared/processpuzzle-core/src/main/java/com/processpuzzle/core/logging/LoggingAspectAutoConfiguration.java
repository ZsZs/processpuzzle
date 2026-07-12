package com.processpuzzle.core.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@EnableAspectJAutoProxy
public class LoggingAspectAutoConfiguration {

    @Bean
    public LoggingAspect processpuzzleLoggingAspect() {
        return new LoggingAspect();
    }
}
