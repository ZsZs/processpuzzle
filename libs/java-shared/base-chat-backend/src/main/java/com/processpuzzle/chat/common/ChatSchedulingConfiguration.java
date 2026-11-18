package com.processpuzzle.chat.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} for the retention job; idempotent, so a host that enables it too is not affected. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ChatSchedulingConfiguration {
}
