package com.processpuzzle.core.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@EnableAspectJAutoProxy
public class LoggingAspectAutoConfiguration {

    @Bean
    public LoggingAspect processpuzzleLoggingAspect(ApplicationContext context) {
        return new LoggingAspect(new ObjectMapper(), new HandledExceptionClassifier(context));
    }
}
