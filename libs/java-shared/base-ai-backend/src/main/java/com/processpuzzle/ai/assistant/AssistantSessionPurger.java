package com.processpuzzle.ai.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Expires the idle design conversations once an hour; see {@link AssistantSessions#purgeIdle()}. */
public class AssistantSessionPurger {

    private static final Logger LOG = LoggerFactory.getLogger(AssistantSessionPurger.class);

    private final AssistantSessions sessions;

    public AssistantSessionPurger(AssistantSessions sessions) {
        this.sessions = sessions;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    public void purge() {
        int expired = sessions.purgeIdle();
        if (expired > 0) {
            LOG.info("Expired {} idle design assistant session(s).", expired);
        }
    }
}
